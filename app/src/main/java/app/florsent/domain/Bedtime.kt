package app.florsent.domain

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/** Which kind of guidance a fade belongs to; frozen when the fade starts. */
enum class GuidanceContext { Day, Bedtime }

/**
 * Bedtime wind-down, anchored to a time the user accepted or chose. Learning never moves the anchor
 * (that would let the app's own effect chase the window earlier every week).
 *
 * @param anchorMinuteOfDay intended bedtime as minutes after local midnight (e.g. 23:40 = 1420).
 * @param thresholdMs watched-app use inside the window before the first gentle fade.
 * @param secondThresholdMs use after the first bedtime quiet time before the second (last) nudge.
 */
data class BedtimeConfig(
    val anchorMinuteOfDay: Int,
    val thresholdMs: Long = 15 * MINUTE_MS,
    val secondThresholdMs: Long = 7 * MINUTE_MS,
    val cooldownMs: Long = 30 * MINUTE_MS,
    val leadMinutes: Int = 30,
    val maxNudgesPerNight: Int = 2,
) {
    init {
        require(anchorMinuteOfDay in 0 until MINUTES_PER_DAY) { "anchor must be a minute of the day" }
    }

    companion object {
        const val MINUTE_MS = 60_000L
        const val MINUTES_PER_DAY = 24 * 60
    }
}

/** One night's window in epoch milliseconds: [startMs, endMs). */
data class BedtimeWindow(val startMs: Long, val endMs: Long) {
    operator fun contains(nowMs: Long) = nowMs in startMs until endMs
}

object BedtimeWindows {
    /** Mornings never end the window before this, so a 03:00 bedtime still gets two hours. */
    private val EARLIEST_END: LocalTime = LocalTime.of(5, 0)
    private const val END_AFTER_ANCHOR_MINUTES = 120L

    /**
     * The window for the night whose anchor falls on [anchorDate]. Anchors before noon belong to the
     * night that started the previous evening, so their window may start the day before.
     */
    fun forAnchorDate(anchorDate: LocalDate, config: BedtimeConfig, zone: ZoneId): BedtimeWindow {
        val anchor = anchorDate.atStartOfDay(zone).plusMinutes(config.anchorMinuteOfDay.toLong())
        val start = anchor.minusMinutes(config.leadMinutes.toLong())
        val morningDate = if (anchor.toLocalTime() < LocalTime.NOON) anchor.toLocalDate() else anchor.toLocalDate().plusDays(1)
        val earliestEnd = morningDate.atTime(EARLIEST_END).atZone(zone)
        val end = maxOf(anchor.plusMinutes(END_AFTER_ANCHOR_MINUTES), earliestEnd)
        return BedtimeWindow(start.toInstant().toEpochMilli(), end.toInstant().toEpochMilli())
    }

    /** The window containing [nowMs], if any. */
    fun at(nowMs: Long, config: BedtimeConfig, zone: ZoneId): BedtimeWindow? {
        val today = Instant.ofEpochMilli(nowMs).atZone(zone).toLocalDate()
        return (-1L..1L).asSequence()
            .map { forAnchorDate(today.plusDays(it), config, zone) }
            .firstOrNull { nowMs in it }
    }

    /** The next window that starts after [nowMs] (for scheduling and the home-screen hint). */
    fun next(nowMs: Long, config: BedtimeConfig, zone: ZoneId): BedtimeWindow {
        val today = Instant.ofEpochMilli(nowMs).atZone(zone).toLocalDate()
        return (-1L..2L).asSequence()
            .map { forAnchorDate(today.plusDays(it), config, zone) }
            .first { it.startMs > nowMs }
    }
}
