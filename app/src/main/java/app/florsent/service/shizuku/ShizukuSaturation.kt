package app.florsent.service.shizuku

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.IBinder
import android.util.Log
import app.florsent.BuildConfig
import app.florsent.service.effect.DirtyPackageStore
import app.florsent.service.effect.SaturationBackend
import app.florsent.service.effect.SaturationSession
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import rikka.shizuku.Shizuku

enum class ShizukuState {
    /** The Shizuku app is not installed (and no Sui/root binder is present). */
    NotInstalled,

    /** Installed, but its service has not been started (e.g. after a reboot). */
    NotRunning,

    /** Shizuku older than v11; unsupported. */
    TooOld,

    /** Running, but the user has not allowed Florsent yet. */
    NoPermission,

    /** Running and allowed. */
    Ready,
}

/**
 * App-wide owner of the optional per-app desaturation layer (third visual layer).
 *
 * - Tracks Shizuku's state for the settings screen.
 * - Owns one [SaturationSession] on one worker thread, so every shell call is ordered and never on
 *   the main thread.
 * - Restores leftovers as soon as Shizuku becomes usable again (process death, Shizuku restart), using
 *   the persisted dirty list; level 100 always wins over whatever an earlier run left behind.
 * - Binds the Shizuku UserService only when needed (feature on, or something to restore).
 */
class ShizukuSaturation(context: Context) {

    private val appContext = context.applicationContext
    private val callerPackage = appContext.packageName

    private val _state = MutableStateFlow(ShizukuState.NotInstalled)
    val state: StateFlow<ShizukuState> = _state.asStateFlow()

    /** A shell call failed; the layer is paused and the other layers carry the guidance. */
    private val _degraded = MutableStateFlow(false)
    val degraded: StateFlow<Boolean> = _degraded.asStateFlow()

    /** Some app may still be desaturated and cannot be restored until Shizuku is back. */
    private val _pendingRestore = MutableStateFlow(false)
    val pendingRestore: StateFlow<Boolean> = _pendingRestore.asStateFlow()

    @Volatile private var shell: IShellService? = null
    @Volatile private var binding = false

    private val dirty = SharedPrefsDirtyStore(appContext)
    private val worker = Executors.newSingleThreadExecutor { r ->
        Thread(r, "florsent-saturation").apply { isDaemon = true }
    }
    private val backend = object : SaturationBackend {
        override fun isReady(): Boolean = shell?.asBinder()?.isBinderAlive == true

        override fun setLayerSaturation(callerPackage: String, targetPackage: String, saturation: Int): Boolean =
            try {
                shell?.setLayerSaturation(saturation, callerPackage, targetPackage) == 0
            } catch (e: Exception) {
                Log.w(TAG, "setLayerSaturation failed", e)
                false
            }
    }
    private val session = SaturationSession(backend, dirty, callerPackage)

    private data class Request(val target: String?, val level: Float, val enabled: Boolean)

    @Volatile private var latest: Request? = null
    private val scheduled = AtomicBoolean(false)

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            if (binder == null || !binder.pingBinder()) return
            shell = IShellService.Stub.asInterface(binder)
            worker.execute {
                session.recover() // undo anything a previous run (or crash) left behind
                publish()
                latest?.let { session.render(it.target, it.level, it.enabled) }
                publish()
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            shell = null
            binding = false
            _pendingRestore.value = dirty.all().isNotEmpty()
        }
    }

    init {
        _pendingRestore.value = dirty.all().isNotEmpty()
        Shizuku.addBinderReceivedListenerSticky { refresh() }
        Shizuku.addBinderDeadListener {
            shell = null
            binding = false
            refresh()
        }
        Shizuku.addRequestPermissionResultListener { requestCode, _ ->
            if (requestCode == PERMISSION_REQUEST) refresh()
        }
        refresh()
    }

    /** Re-reads Shizuku's state (call from the UI on resume). Main thread. */
    fun refresh() {
        _state.value = currentState()
        _pendingRestore.value = dirty.all().isNotEmpty()
        if (_state.value == ShizukuState.Ready && (dirty.all().isNotEmpty() || latest?.enabled == true)) {
            bindIfNeeded()
        }
    }

    fun requestPermission() {
        runCatching {
            if (Shizuku.pingBinder() && !Shizuku.isPreV11()) Shizuku.requestPermission(PERMISSION_REQUEST)
        }.onFailure { Log.w(TAG, "requestPermission failed", it) }
    }

    /** After a failure: reconnect, restore and resume. */
    fun retry() {
        refresh()
        bindIfNeeded()
        worker.execute {
            session.recover()
            publish()
        }
    }

    /** Latest guidance; coalesced so the worker only ever applies the newest request. */
    fun render(targetPackage: String?, level: Float, enabled: Boolean) {
        latest = Request(targetPackage, level, enabled)
        if (enabled && _state.value == ShizukuState.Ready) bindIfNeeded()
        if (scheduled.compareAndSet(false, true)) {
            worker.execute {
                scheduled.set(false)
                val request = latest ?: return@execute
                session.render(request.target, request.level, request.enabled)
                publish()
            }
        }
    }

    /** Restore every app to full colour (service shutdown, feature off). */
    fun restoreAll() {
        latest = null
        worker.execute {
            session.restoreAll()
            publish()
        }
    }

    private fun publish() {
        _degraded.value = session.degraded
        _pendingRestore.value = session.pendingRestores.isNotEmpty() && !backend.isReady()
    }

    private fun bindIfNeeded() {
        if (shell != null || binding) return
        runCatching {
            Shizuku.bindUserService(userServiceArgs(), connection)
            binding = true
        }.onFailure { Log.w(TAG, "bindUserService failed", it) }
    }

    private fun userServiceArgs() = Shizuku.UserServiceArgs(ComponentName(appContext, ShellService::class.java))
        .daemon(false) // dies with our process; nothing privileged lingers
        .processNameSuffix("saturation")
        .debuggable(BuildConfig.DEBUG)
        .version(BuildConfig.VERSION_CODE)
        .tag(USER_SERVICE_TAG) // explicit: class names change under R8

    private fun currentState(): ShizukuState = try {
        when {
            !Shizuku.pingBinder() -> if (isManagerInstalled()) ShizukuState.NotRunning else ShizukuState.NotInstalled
            Shizuku.isPreV11() -> ShizukuState.TooOld
            Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED -> ShizukuState.NoPermission
            else -> ShizukuState.Ready
        }
    } catch (e: Exception) {
        // The binder can die between calls.
        if (isManagerInstalled()) ShizukuState.NotRunning else ShizukuState.NotInstalled
    }

    private fun isManagerInstalled(): Boolean = try {
        appContext.packageManager.getPackageInfo(MANAGER_PACKAGE, 0)
        true
    } catch (e: PackageManager.NameNotFoundException) {
        false
    }

    companion object {
        const val MANAGER_PACKAGE = "moe.shizuku.privileged.api"
        const val DOWNLOAD_URL = "https://shizuku.rikka.app/"
        private const val TAG = "ShizukuSaturation"
        private const val PERMISSION_REQUEST = 4201
        private const val USER_SERVICE_TAG = "florsent-saturation"
    }
}

/** Synchronously persisted (commit) so the record exists before any app is desaturated. */
private class SharedPrefsDirtyStore(context: Context) : DirtyPackageStore {
    private val prefs = context.getSharedPreferences("shizuku_saturation", Context.MODE_PRIVATE)

    override fun all(): Set<String> = prefs.getStringSet(KEY, emptySet()).orEmpty().toSet()

    override fun add(packageName: String) {
        val current = all()
        if (packageName !in current) prefs.edit().putStringSet(KEY, current + packageName).commit()
    }

    override fun remove(packageName: String) {
        val current = all()
        if (packageName in current) prefs.edit().putStringSet(KEY, current - packageName).commit()
    }

    private companion object {
        const val KEY = "desaturated_packages"
    }
}
