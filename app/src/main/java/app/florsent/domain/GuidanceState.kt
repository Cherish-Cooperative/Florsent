package app.florsent.domain

enum class GuidanceMode {
    /** Counting usage; guidance appears once a budget is exceeded. */
    Tracking,

    /** The user accepted the nudge and is in the breathing flow. */
    Breathing,

    /** Rest period after a completed breathing exercise; no visual effects. */
    Cooldown,
}

/**
 * Complete engine state. Wall-clock milliseconds are used throughout; [GuidanceConfig.maxTickGapMs]
 * protects usage accounting from sleep and clock changes.
 */
data class GuidanceState(
    val foregroundPackage: String? = null,
    val screenOn: Boolean = true,
    val epochDay: Long = 0,
    val dailyUsedMs: Long = 0,
    val sessionUsedMs: Long = 0,
    /** Usage accumulated after a budget was exceeded; drives the fade. */
    val overBudgetMs: Long = 0,
    val lastWatchedAtMs: Long? = null,
    val lastTickAtMs: Long? = null,
    val mode: GuidanceMode = GuidanceMode.Tracking,
    val modeSinceMs: Long = 0,
    val cooldownUntilMs: Long = 0,
    val nudgeSnoozedUntilMs: Long = 0,
    val previewStartedAtMs: Long? = null,
    val breathingFromPreview: Boolean = false,
) {
    fun isWatched(config: GuidanceConfig): Boolean =
        foregroundPackage != null && foregroundPackage in config.watchedPackages

    /** True while watched-app time is being accumulated. */
    fun isCounting(config: GuidanceConfig): Boolean = screenOn && isWatched(config)
}

sealed interface GuidanceEvent {
    val nowMs: Long

    data class ForegroundChanged(val packageName: String?, override val nowMs: Long) : GuidanceEvent
    data class ScreenChanged(val on: Boolean, override val nowMs: Long) : GuidanceEvent
    data class Tick(override val nowMs: Long) : GuidanceEvent
    data class NudgeAccepted(override val nowMs: Long) : GuidanceEvent
    data class NudgeDismissed(override val nowMs: Long) : GuidanceEvent
    data class BreathingCompleted(override val nowMs: Long) : GuidanceEvent
    data class BreathingCancelled(override val nowMs: Long) : GuidanceEvent
    data class PreviewStarted(override val nowMs: Long) : GuidanceEvent
    data class PreviewStopped(override val nowMs: Long) : GuidanceEvent
}

/** What the effect layer should show right now. */
data class GuidanceEffect(
    /** 0 = untouched screen, 1 = strongest guidance. Already eased. */
    val level: Float,
    val showNudge: Boolean,
    /** The app being guided; during the preview, whatever app is in front (may be null). */
    val targetPackage: String?,
    val isPreview: Boolean,
) {
    companion object {
        val None = GuidanceEffect(level = 0f, showNudge = false, targetPackage = null, isPreview = false)
    }
}

/** User-facing summary for the home screen. */
sealed interface GuidanceStatus {
    data object Idle : GuidanceStatus
    data object Guiding : GuidanceStatus
    data object Breathing : GuidanceStatus
    data object Preview : GuidanceStatus
    data class Cooldown(val untilMs: Long) : GuidanceStatus
}
