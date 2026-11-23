package app.florsent.service.shizuku

import android.content.Context
import androidx.annotation.Keep
import java.util.concurrent.TimeUnit
import kotlin.system.exitProcess

/**
 * Shizuku UserService: runs in a separate process under the shell (adb) or root uid, never in the
 * Florsent app process. It exposes exactly one operation and builds the command line itself from
 * validated arguments, so this privileged channel cannot be used to run anything else.
 */
class ShellService() : IShellService.Stub() {

    /** Shizuku prefers the Context constructor when present. */
    @Keep
    constructor(@Suppress("UNUSED_PARAMETER") context: Context) : this()

    override fun destroy() {
        exitProcess(0)
    }

    override fun setLayerSaturation(level: Int, callerPackage: String, targetPackage: String): Int {
        if (level !in 0..100 || !isPackageName(callerPackage) || !isPackageName(targetPackage)) {
            return INVALID_ARGUMENT
        }
        return try {
            val process = ProcessBuilder(
                "cmd", "color_display", "set-layer-saturation", level.toString(), callerPackage, targetPackage,
            ).redirectErrorStream(true).start()
            if (!process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                process.destroy()
                return TIMED_OUT
            }
            val output = process.inputStream.bufferedReader().use { it.readText() }
            // `cmd` prints the exception (e.g. SecurityException on early Android 14) to stdout.
            if (process.exitValue() != 0 || output.contains("Exception")) FAILED else OK
        } catch (e: Exception) {
            FAILED
        }
    }

    private companion object {
        const val OK = 0
        const val FAILED = -1
        const val INVALID_ARGUMENT = -2
        const val TIMED_OUT = -3
        const val TIMEOUT_SECONDS = 5L
        val PACKAGE_NAME = Regex("^[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*)+$")

        fun isPackageName(value: String) = value.length <= 255 && PACKAGE_NAME.matches(value)
    }
}
