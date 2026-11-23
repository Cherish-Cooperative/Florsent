package app.florsent.domain

/**
 * Turns a TYPE_WINDOW_STATE_CHANGED event into "the app the user is looking at", or null when the
 * event should not change the current foreground app.
 */
object ForegroundPolicy {

    /**
     * Shade, keyguard, volume dialog and runtime-permission prompts: transient system surfaces
     * shown on top of the app the user is still in.
     */
    private val transientPackages = setOf(
        "com.android.systemui",
        "com.android.permissioncontroller",
        "com.google.android.permissioncontroller",
        "android", // share sheet / resolver dialogs on older releases
        "com.android.intentresolver", // share sheet on Android 14+
    )

    fun resolve(
        packageName: CharSequence?,
        className: CharSequence?,
        selfPackage: String,
        ignoredPackages: Set<String>,
    ): String? {
        val pkg = packageName?.toString()?.takeIf { it.isNotEmpty() } ?: return null
        if (pkg in transientPackages || pkg in ignoredPackages) return null
        // Our own overlay windows (dim layer, nudge) must not count as "the user opened Florsent";
        // only our activities do.
        if (pkg == selfPackage && className?.startsWith("$selfPackage.") != true) return null
        return pkg
    }

    /** How long after the nudge hands off to Florsent a window event from the app just left is ignored. */
    const val HANDOFF_ECHO_MS = 2_000L

    /**
     * After the nudge opens the breathing screen, the app the user just left can still emit a late
     * window event (a dialog or transition finishing). Taken at face value it would look like
     * "went back to the app", cancel breathing and bring the fade back over the breathing screen.
     */
    fun isHandoffEcho(pkg: String, leftPackage: String?, leftAtMs: Long, nowMs: Long): Boolean =
        pkg == leftPackage && nowMs - leftAtMs in 0 until HANDOFF_ECHO_MS
}

/**
 * When is the screen "in use" (unlocked, so watched apps can be timed)?
 *
 * ACTION_USER_PRESENT cannot be relied on: on Android 17 it is sent with a defer-until-active policy
 * and an accessibility service's process can sit in a cached state, so the broadcast may never
 * arrive. SCREEN_ON comes while the lock screen is still up. The reliable signal is the first
 * window event after unlocking (launcher or app): at that moment the device is interactive and the
 * keyguard is gone.
 */
object ScreenPolicy {
    /** True when a window event shows the user has unlocked, but the engine still thinks it is locked. */
    fun unlockNotYetSeen(engineScreenOn: Boolean, interactive: Boolean, keyguardLocked: Boolean): Boolean =
        !engineScreenOn && interactive && !keyguardLocked
}

