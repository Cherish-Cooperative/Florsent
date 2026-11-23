package app.florsent.service.shizuku

import android.util.Log
import app.florsent.service.effect.DirtyPackageStore
import app.florsent.service.effect.SaturationBackend
import app.florsent.service.effect.SaturationSession
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

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

/** A connected privileged shell (the Shizuku UserService). */
interface ShellHandle {
    fun isAlive(): Boolean

    /** 0 on success; never throws. */
    fun setLayerSaturation(level: Int, callerPackage: String, targetPackage: String): Int
}

/** Everything ShizukuSaturation needs from Shizuku; the platform version wraps the static API. */
interface ShizukuClient {
    fun pingBinder(): Boolean
    fun isPreV11(): Boolean
    fun hasPermission(): Boolean
    fun requestPermission()
    fun isManagerInstalled(): Boolean

    /** Binds the UserService; callbacks may arrive on any thread. */
    fun bind(onConnected: (ShellHandle) -> Unit, onDisconnected: () -> Unit)

    fun setListeners(onBinderReceived: () -> Unit, onBinderDead: () -> Unit, onPermissionResult: () -> Unit)
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
class ShizukuSaturation(
    private val client: ShizukuClient,
    private val dirty: DirtyPackageStore,
    callerPackage: String,
    private val worker: Executor = Executors.newSingleThreadExecutor { r ->
        Thread(r, "florsent-saturation").apply { isDaemon = true }
    },
) {
    private val _state = MutableStateFlow(ShizukuState.NotInstalled)
    val state: StateFlow<ShizukuState> = _state.asStateFlow()

    /** A shell call failed; the layer is paused and the other layers carry the guidance. */
    private val _degraded = MutableStateFlow(false)
    val degraded: StateFlow<Boolean> = _degraded.asStateFlow()

    /** Some app may still be desaturated and cannot be restored until Shizuku is back. */
    private val _pendingRestore = MutableStateFlow(false)
    val pendingRestore: StateFlow<Boolean> = _pendingRestore.asStateFlow()

    @Volatile private var shell: ShellHandle? = null
    @Volatile private var binding = false

    private val backend = object : SaturationBackend {
        override fun isReady(): Boolean = shell?.isAlive() == true

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

    init {
        _pendingRestore.value = dirty.all().isNotEmpty()
        client.setListeners(
            onBinderReceived = { refresh() },
            onBinderDead = {
                shell = null
                binding = false
                refresh()
            },
            onPermissionResult = { refresh() },
        )
        refresh()
    }

    /** Re-reads Shizuku's state (call from the UI on resume). */
    fun refresh() {
        _state.value = currentState()
        _pendingRestore.value = dirty.all().isNotEmpty() && !backend.isReady()
        if (_state.value == ShizukuState.Ready && (dirty.all().isNotEmpty() || latest?.enabled == true)) {
            bindIfNeeded()
        }
    }

    fun requestPermission() {
        runCatching { if (client.pingBinder() && !client.isPreV11()) client.requestPermission() }
            .onFailure { Log.w(TAG, "requestPermission failed", it) }
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

    private fun onShellConnected(handle: ShellHandle) {
        shell = handle
        worker.execute {
            session.recover() // undo anything a previous run (or crash) left behind
            latest?.let { session.render(it.target, it.level, it.enabled) }
            publish()
        }
    }

    private fun onShellDisconnected() {
        shell = null
        binding = false
        _pendingRestore.value = dirty.all().isNotEmpty()
    }

    private fun publish() {
        _degraded.value = session.degraded
        _pendingRestore.value = session.pendingRestores.isNotEmpty() && !backend.isReady()
    }

    private fun bindIfNeeded() {
        if (shell != null || binding) return
        binding = true
        runCatching { client.bind(::onShellConnected, ::onShellDisconnected) }
            .onFailure {
                binding = false
                Log.w(TAG, "bindUserService failed", it)
            }
    }

    private fun currentState(): ShizukuState = try {
        when {
            !client.pingBinder() ->
                if (client.isManagerInstalled()) ShizukuState.NotRunning else ShizukuState.NotInstalled
            client.isPreV11() -> ShizukuState.TooOld
            !client.hasPermission() -> ShizukuState.NoPermission
            else -> ShizukuState.Ready
        }
    } catch (e: Exception) {
        // The binder can die between calls.
        if (client.isManagerInstalled()) ShizukuState.NotRunning else ShizukuState.NotInstalled
    }

    companion object {
        const val MANAGER_PACKAGE = "moe.shizuku.privileged.api"
        const val DOWNLOAD_URL = "https://shizuku.rikka.app/"
        private const val TAG = "ShizukuSaturation"
    }
}
