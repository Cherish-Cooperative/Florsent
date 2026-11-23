package app.florsent.service.shizuku

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.IBinder
import app.florsent.BuildConfig
import app.florsent.service.effect.DirtyPackageStore
import rikka.shizuku.Shizuku

/** The real Shizuku: static API, UserService binding and the AIDL shell. */
class PlatformShizukuClient(context: Context) : ShizukuClient {

    private val appContext = context.applicationContext

    override fun pingBinder(): Boolean = Shizuku.pingBinder()

    override fun isPreV11(): Boolean = Shizuku.isPreV11()

    override fun hasPermission(): Boolean = Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED

    override fun requestPermission() = Shizuku.requestPermission(PERMISSION_REQUEST)

    override fun isManagerInstalled(): Boolean = try {
        appContext.packageManager.getPackageInfo(ShizukuSaturation.MANAGER_PACKAGE, 0)
        true
    } catch (e: PackageManager.NameNotFoundException) {
        false
    }

    override fun bind(onConnected: (ShellHandle) -> Unit, onDisconnected: () -> Unit) {
        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
                if (binder == null || !binder.pingBinder()) return
                onConnected(AidlShell(IShellService.Stub.asInterface(binder)))
            }

            override fun onServiceDisconnected(name: ComponentName?) = onDisconnected()
        }
        Shizuku.bindUserService(userServiceArgs(), connection)
    }

    override fun setListeners(
        onBinderReceived: () -> Unit,
        onBinderDead: () -> Unit,
        onPermissionResult: () -> Unit,
    ) {
        Shizuku.addBinderReceivedListenerSticky { onBinderReceived() }
        Shizuku.addBinderDeadListener { onBinderDead() }
        Shizuku.addRequestPermissionResultListener { requestCode, _ ->
            if (requestCode == PERMISSION_REQUEST) onPermissionResult()
        }
    }

    private fun userServiceArgs() = Shizuku.UserServiceArgs(ComponentName(appContext, ShellService::class.java))
        .daemon(false) // dies with our process; nothing privileged lingers
        .processNameSuffix("saturation")
        .debuggable(BuildConfig.DEBUG)
        .version(BuildConfig.VERSION_CODE)
        .tag(USER_SERVICE_TAG) // explicit: class names change under R8

    private class AidlShell(private val service: IShellService) : ShellHandle {
        override fun isAlive(): Boolean = service.asBinder().isBinderAlive

        override fun setLayerSaturation(level: Int, callerPackage: String, targetPackage: String): Int =
            try {
                service.setLayerSaturation(level, callerPackage, targetPackage)
            } catch (e: Exception) {
                -1
            }
    }

    companion object {
        private const val PERMISSION_REQUEST = 4201
        private const val USER_SERVICE_TAG = "florsent-saturation"

        fun saturation(context: Context): ShizukuSaturation = ShizukuSaturation(
            client = PlatformShizukuClient(context),
            dirty = SharedPrefsDirtyStore(context.applicationContext),
            callerPackage = context.packageName,
        )
    }
}

/** Synchronously persisted (commit) so the record exists before any app is desaturated. */
class SharedPrefsDirtyStore(context: Context) : DirtyPackageStore {
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
