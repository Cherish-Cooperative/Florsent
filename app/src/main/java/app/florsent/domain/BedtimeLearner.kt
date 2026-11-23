package app.florsent.domain

import java.time.LocalDate
import kotlin.math.abs
import kotlin.math.pow

/**
 * One night's evidence. "Onset" is when the phone was put down for the night (start of the longest
 * screen-off gap); we never claim it is when the user fell asleep.
 *
 * @param minuteOfNight minutes after 12:00 local time on [night] (20:00 = 480, 01:00 = 780).
 */
data class NightObservation(
    val night: LocalDate,
    val minuteOfNight: Int,
    val onsetMs: Long,
    val preSleepAppMinutes: Int,
)

sealed interface BedtimeLearning {
    /** Still gathering nights. */
    data class Learning(val nightsObserved: Int, val daysRecorded: Int) : BedtimeLearning

    /** A habit is visible: the phone is usually put down around [minuteOfDay]. */
    data class Confident(val minuteOfDay: Int, val spreadMinutes: Int, val nights: Int) : BedtimeLearning

    /** A week of data and still no stable pattern (shift work, irregular schedule). */
    data class Irregular(val nightsObserved: Int) : BedtimeLearning
}

/**
 * On-device, explainable estimate of the habitual "put the phone down" time.
 *
 * Night = local 12:00 to the next 12:00, measured in real elapsed time for gap lengths and in the
 * recorded wall-clock time (per-record UTC offset) for positions, so DST nights and travel are right.
 */
object BedtimeLearner {

    const val MIN_GAP_MS = 3 * 60 * 60_000L
    const val BLIP_MAX_MS = 10 * 60_000L
    const val BLIP_SIDE_MIN_MS = 60 * 60_000L
    const val VIDEO_SPAN_MS = 2 * 60 * 60_000L
    private const val PRE_SLEEP_WINDOW_MS = 120 * 60_000L
    private const val ONSET_EARLIEST = 8 * 60 // 20:00 as minutes after noon
    private const val ONSET_LATEST = 17 * 60 // 05:00
    private const val LOOKBACK_NIGHTS = 28
    private const val HALF_LIFE_DAYS = 7.0
    private const val MIN_NIGHTS = 3
    private const val MAX_SPREAD_MINUTES = 60
    private const val DAYS_BEFORE_IRREGULAR = 7

    private data class Interval(val startMs: Long, val endMs: Long, val offsetSec: Int)

    fun observe(records: List<UsageRecord>, nowMs: Long): List<NightObservation> {
        val screens = records.filterIsInstance<UsageRecord.Screen>()
            .map { Interval(it.startSec * 1000, (it.startSec + it.durationSec) * 1000, it.offsetSec) }
            .sortedBy { it.startMs }
        if (screens.isEmpty()) return emptyList()
        val blind = blindIntervals(records, nowMs)
        val apps = records.filterIsInstance<UsageRecord.App>()

        return mergedGaps(screens)
            .asSequence()
            .filter { it.endMs - it.startMs >= MIN_GAP_MS }
            .filter { gap -> blind.none { it.startMs < gap.endMs && it.endMs > gap.startMs } }
            .mapNotNull { gap ->
                val (night, minute) = nightPosition(gap.startMs, gap.offsetSec)
                when {
                    minute !in ONSET_EARLIEST..ONSET_LATEST -> null
                    nightEndMs(night, gap.offsetSec) > nowMs -> null // not final until the next noon
                    else -> Triple(night, gap, minute)
                }
            }
            .groupBy { it.first }
            .mapNotNull { (night, candidates) ->
                val (_, gap, minute) = candidates.maxBy { it.second.endMs - it.second.startMs }
                if (isUncertain(night, gap.offsetSec, screens, blind)) null
                else NightObservation(night, minute, gap.startMs, preSleepMinutes(apps, gap.startMs))
            }
            .sortedBy { it.night }
    }

    fun estimate(observations: List<NightObservation>, records: List<UsageRecord>, nowMs: Long, offsetSec: Int): BedtimeLearning {
        val (tonight, _) = nightPosition(nowMs, offsetSec)
        val recent = observations.filter { it.night > tonight.minusDays(LOOKBACK_NIGHTS.toLong() + 1) }
        val daysRecorded = recordingDays(records, nowMs)
        if (recent.size >= MIN_NIGHTS) {
            val weights = recent.map { 0.5.pow((tonight.toEpochDay() - it.night.toEpochDay()) / HALF_LIFE_DAYS) }
            val values = recent.map { it.minuteOfNight.toDouble() }
            val median = weightedMedian(values, weights)
            val spread = weightedMedian(values.map { abs(it - median) }, weights)
            if (spread <= MAX_SPREAD_MINUTES) {
                val minuteOfDay = (median.toInt() + 12 * 60) % (24 * 60)
                return BedtimeLearning.Confident(minuteOfDay, spread.toInt(), recent.size)
            }
        }
        return if (daysRecorded >= DAYS_BEFORE_IRREGULAR) BedtimeLearning.Irregular(recent.size)
        else BedtimeLearning.Learning(recent.size, daysRecorded)
    }

    /** Rounds a minute of day to 5 minutes (for invitations and display). */
    fun roundTo5(minuteOfDay: Int): Int = ((minuteOfDay + 2) / 5 * 5) % (24 * 60)

    fun weightedMedian(values: List<Double>, weights: List<Double>): Double {
        val sorted = values.zip(weights).sortedBy { it.first }
        val half = weights.sum() / 2
        var acc = 0.0
        for ((v, w) in sorted) {
            acc += w
            if (acc >= half) return v
        }
        return sorted.last().first
    }

    /**
     * Gaps between screen-in-use spans, where a short unlock (≤ 10 min) with ≥ 60 min of darkness on
     * both sides is absorbed (checking the alarm, answering one message). Merging chains.
     */
    private fun mergedGaps(screens: List<Interval>): List<Interval> {
        val gaps = mutableListOf<Interval>()
        var current: Interval? = null
        for (i in 0 until screens.size - 1) {
            val next = Interval(screens[i].endMs, screens[i + 1].startMs, screens[i].offsetSec)
            if (next.endMs <= next.startMs) continue
            val blip = screens[i]
            val cur = current
            if (cur != null && cur.endMs == blip.startMs &&
                blip.endMs - blip.startMs <= BLIP_MAX_MS &&
                cur.endMs - cur.startMs >= BLIP_SIDE_MIN_MS &&
                next.endMs - next.startMs >= BLIP_SIDE_MIN_MS
            ) {
                current = cur.copy(endMs = next.endMs)
            } else {
                cur?.let { gaps += it }
                current = next
            }
        }
        current?.let { gaps += it }
        return gaps
    }

    /** Time we cannot vouch for: Unknown records and every stretch outside Begin…End. */
    private fun blindIntervals(records: List<UsageRecord>, nowMs: Long): List<Interval> {
        val result = mutableListOf<Interval>()
        var recordingSince: Long? = null
        var lastStop = Long.MIN_VALUE / 2
        for (r in records.sortedBy { it.startSec }) {
            val t = r.startSec * 1000
            when (r) {
                is UsageRecord.Begin -> {
                    if (recordingSince == null) result += Interval(lastStop, t, r.offsetSec)
                    recordingSince = t
                }
                is UsageRecord.End -> {
                    recordingSince = null
                    lastStop = t
                }
                is UsageRecord.Unknown -> result += Interval(t, t + r.durationSec * 1000, r.offsetSec)
                else -> Unit
            }
        }
        if (recordingSince == null) result += Interval(lastStop, nowMs, 0)
        return result
    }

    /** Uncertain nights: something blind in the core of the night, or a 2 h+ span after midnight (video). */
    private fun isUncertain(night: LocalDate, offsetSec: Int, screens: List<Interval>, blind: List<Interval>): Boolean {
        val noon = localNoonMs(night, offsetSec)
        val coreStart = noon + 8 * 60 * 60_000L // 20:00
        val coreEnd = noon + 22 * 60 * 60_000L // 10:00 next day
        if (blind.any { it.startMs < coreEnd && it.endMs > coreStart }) return true
        val midnight = noon + 12 * 60 * 60_000L
        val fiveAm = noon + 17 * 60 * 60_000L
        return screens.any {
            it.endMs - it.startMs > VIDEO_SPAN_MS && it.startMs < fiveAm && it.endMs > midnight
        }
    }

    private fun preSleepMinutes(apps: List<UsageRecord.App>, onsetMs: Long): Int {
        val from = onsetMs - PRE_SLEEP_WINDOW_MS
        val ms = apps.sumOf { a ->
            val s = a.startSec * 1000
            val e = s + a.durationSec * 1000
            (minOf(e, onsetMs) - maxOf(s, from)).coerceAtLeast(0)
        }
        return (ms / 60_000).toInt()
    }

    /** (night date, minutes after that night's local noon) for an instant with the given offset. */
    fun nightPosition(epochMs: Long, offsetSec: Int): Pair<LocalDate, Int> {
        val localMinutes = Math.floorDiv(epochMs / 1000 + offsetSec, 60L)
        val sinceNoon = localMinutes - 12 * 60
        val day = Math.floorDiv(sinceNoon, 24 * 60L)
        return LocalDate.ofEpochDay(day) to (sinceNoon - day * 24 * 60).toInt()
    }

    private fun localNoonMs(night: LocalDate, offsetSec: Int): Long =
        (night.toEpochDay() * 86_400L + 12 * 3600 - offsetSec) * 1000

    private fun nightEndMs(night: LocalDate, offsetSec: Int): Long = localNoonMs(night.plusDays(1), offsetSec)

    private fun recordingDays(records: List<UsageRecord>, nowMs: Long): Int {
        val first = records.filterIsInstance<UsageRecord.Begin>().minOfOrNull { it.startSec } ?: return 0
        return ((nowMs / 1000 - first) / 86_400).toInt()
    }
}
