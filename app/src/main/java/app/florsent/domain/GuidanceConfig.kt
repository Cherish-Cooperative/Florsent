package app.florsent.domain

private const val MINUTE_MS = 60_000L

/**
 * Tunables for the gradual guidance engine. Pure data so the engine stays testable.
 */
data class GuidanceConfig(
    /** Packages whose foreground time counts toward the budgets. */
    val watchedPackages: Set<String> = emptySet(),
    /** Continuous time in watched apps before guidance starts. */
    val sessionBudgetMs: Long = 15 * MINUTE_MS,
    /** Total watched-app time per local day; once exceeded, guidance comes much sooner. */
    val dailyBudgetMs: Long = 60 * MINUTE_MS,
    /**
     * After the daily budget is used up, each session still gets this much use before the fade
     * starts, so opening an app never feels like hitting a wall.
     */
    val dailyGraceMs: Long = 2 * MINUTE_MS,
    /** Over-budget usage needed to go from no effect to full effect. */
    val fadeDurationMs: Long = 3 * MINUTE_MS,
    /**
     * Each time a watched app comes back to the front, the guidance builds up from nothing to its
     * accumulated level over this long instead of jumping straight back. The nudge waits for it.
     */
    val entryRampMs: Long = 8_000L,
    /** Fade progress (0..1) at which the "take a breath?" nudge slides in. */
    val nudgeAtProgress: Float = 0.5f,
    /** How long a dismissed nudge stays hidden. */
    val nudgeSnoozeMs: Long = 2 * MINUTE_MS,
    /** Quiet period after a completed breathing exercise. */
    val cooldownMs: Long = 20 * MINUTE_MS,
    /** Time away from watched apps after which a new session starts from zero. */
    val sessionGapMs: Long = 10 * MINUTE_MS,
    /** A breathing session left open longer than this is treated as abandoned. */
    val breathingTimeoutMs: Long = 15 * MINUTE_MS,
    /** Longest the runtime sleeps between ticks when nothing visible changes. */
    val maxTickIntervalMs: Long = 15_000L,
    /**
     * Upper bound for one accounting step, so a frozen process or a clock jump is not counted as
     * usage. Must exceed [maxTickIntervalMs].
     */
    val maxTickGapMs: Long = 20_000L,
    val preview: PreviewConfig = PreviewConfig(),
    /** Bedtime wind-down; null until the user accepts an invitation or picks a time. */
    val bedtime: BedtimeConfig? = null,
)

/** The 30-second "try the gradual guidance" demo. */
data class PreviewConfig(
    val durationMs: Long = 30_000L,
    val fadeMs: Long = 20_000L,
    val nudgeAtMs: Long = 12_000L,
)
