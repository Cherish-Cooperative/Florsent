package app.florsent.domain

import java.time.LocalDate

/** One local day of watched-app use. [minutes] is null when nothing was being recorded that day. */
data class DayUsage(val date: LocalDate, val minutes: Int?, val perAppMinutes: Map<String, Int>)

/** A calm, factual sentence; the UI turns it into localized text. */
sealed interface Insight {
    data class PutsPhoneDownAround(val minuteOfDay: Int) : Insight
    data class LessThanLastWeek(val minutes: Int) : Insight
    data object MoreThanLastWeek : Insight
    data object SimilarToLastWeek : Insight
    data object NotEnoughData : Insight
}

data class UsageSummary(
    val today: DayUsage,
    /** Oldest first, today last. */
    val lastSevenDays: List<DayUsage>,
    /** Top watched apps over the last 7 days (at most 3), most used first. */
    val topApps: List<Pair<String, Int>>,
    val insights: List<Insight>,
)

object UsageInsights {

    private const val TREND_THRESHOLD = 0.15
    private const val MAX_INSIGHTS = 2

    /**
     * Splits watched-app records into local days (using each record's own UTC offset, so travel does
     * not rewrite history) and marks days without any recording as "no data" instead of zero.
     */
    fun days(records: List<UsageRecord>, today: LocalDate, count: Int, nowMs: Long): List<DayUsage> {
        val first = today.minusDays(count.toLong() - 1)
        val perDayApp = mutableMapOf<LocalDate, MutableMap<String, Long>>()
        for (r in records.filterIsInstance<UsageRecord.App>()) {
            var start = r.startSec + r.offsetSec
            val end = start + r.durationSec
            while (start < end) {
                val day = LocalDate.ofEpochDay(Math.floorDiv(start, 86_400L))
                val dayEnd = (day.toEpochDay() + 1) * 86_400
                val chunk = minOf(end, dayEnd) - start
                if (day in first..today) {
                    perDayApp.getOrPut(day) { mutableMapOf() }.merge(r.packageName, chunk, Long::plus)
                }
                start += chunk
            }
        }
        val covered = coveredDays(records, nowMs)
        return (0 until count).map { i ->
            val date = first.plusDays(i.toLong())
            val apps = perDayApp[date].orEmpty().mapValues { (it.value / 60).toInt() }
            val hasData = date in covered || apps.isNotEmpty()
            DayUsage(date, if (hasData) apps.values.sum() else null, apps)
        }
    }

    fun summarize(
        records: List<UsageRecord>,
        today: LocalDate,
        nowMs: Long,
        learning: BedtimeLearning,
    ): UsageSummary {
        // 15 days: today plus two full, completed weeks. Today is never part of a comparison,
        // otherwise every morning would look like "less than last week".
        val days = days(records, today, 15, nowMs)
        val week = days.takeLast(7)
        val completed = days.dropLast(1)
        val top = week.flatMap { it.perAppMinutes.entries }
            .groupBy({ it.key }, { it.value })
            .mapValues { it.value.sum() }
            .filterValues { it > 0 }
            .entries.sortedByDescending { it.value }
            .take(3)
            .map { it.key to it.value }
        return UsageSummary(week.last(), week, top, insights(completed, learning))
    }

    /**
     * At most two sentences: the bedtime habit first, then a two-week trend over 14 *completed* days
     * (last 7 vs the 7 before). Never judgmental.
     */
    fun insights(fortnight: List<DayUsage>, learning: BedtimeLearning): List<Insight> {
        val result = mutableListOf<Insight>()
        if (learning is BedtimeLearning.Confident) {
            result += Insight.PutsPhoneDownAround(BedtimeLearner.roundTo5(learning.minuteOfDay))
        }
        if (fortnight.size >= 14 && fortnight.all { it.minutes != null }) {
            val previous = fortnight.take(7).sumOf { it.minutes!! }
            val current = fortnight.takeLast(7).sumOf { it.minutes!! }
            if (previous > 0) {
                val change = (current - previous).toDouble() / previous
                result += when {
                    change <= -TREND_THRESHOLD -> Insight.LessThanLastWeek(previous - current)
                    change >= TREND_THRESHOLD -> Insight.MoreThanLastWeek
                    else -> Insight.SimilarToLastWeek
                }
            }
        }
        if (result.isEmpty()) result += Insight.NotEnoughData
        return result.take(MAX_INSIGHTS)
    }

    /** Local days that overlap any recorded (Begin…End, minus Unknown) time. */
    private fun coveredDays(records: List<UsageRecord>, nowMs: Long): Set<LocalDate> {
        val days = mutableSetOf<LocalDate>()
        var since: UsageRecord.Begin? = null
        fun cover(from: Long, to: Long, offset: Int) {
            var d = Math.floorDiv(from + offset, 86_400L)
            val last = Math.floorDiv(to + offset, 86_400L)
            while (d <= last) {
                days += LocalDate.ofEpochDay(d)
                d++
            }
        }
        for (r in records.sortedBy { it.startSec }) {
            if (r !is UsageRecord.Begin && r !is UsageRecord.End && r !is UsageRecord.Unknown) continue
            // Any of these closes the current recording stretch; only Begin opens a new one.
            since?.let { cover(it.startSec, r.startSec, it.offsetSec) }
            since = r as? UsageRecord.Begin
        }
        since?.let { cover(it.startSec, nowMs / 1000, it.offsetSec) }
        return days
    }
}
