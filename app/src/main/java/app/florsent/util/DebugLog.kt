package app.florsent.util

import android.util.Log
import app.florsent.BuildConfig

/**
 * Diagnostics for the guidance pipeline (effect levels, window events, overlay attach/detach).
 * Debug builds only: release builds never log which app is in front.
 * Read with `adb logcat --pid=$(adb shell pidof app.florsent) -s FlorsentDbg`.
 */
object DebugLog {
    const val TAG = "FlorsentDbg"

    inline fun d(message: () -> String) {
        if (BuildConfig.DEBUG) Log.d(TAG, message())
    }
}
