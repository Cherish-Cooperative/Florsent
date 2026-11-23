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

/** Common feed-style social apps, used to preselect the watch list on first launch. */
object KnownSocialApps {
    val packages: Set<String> = linkedSetOf(
        "com.instagram.android",
        "com.instagram.barcelona", // Threads
        "com.facebook.katana",
        "com.facebook.lite",
        "com.zhiliaoapp.musically", // TikTok
        "com.ss.android.ugc.trill", // TikTok (some regions)
        "com.google.android.youtube",
        "com.twitter.android", // X
        "com.reddit.frontpage",
        "com.snapchat.android",
        "com.pinterest",
        "tv.twitch.android.app",
        "com.xingin.xhs", // 小紅書
    )
}
