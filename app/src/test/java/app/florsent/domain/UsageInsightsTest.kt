package app.florsent.domain

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UsageInsightsTest {

    private val off = 8 * 3600
    private val today = LocalDate.of(2026, 11, 24)
    private fun at(day: LocalDate, h: Int, m: Int = 0) = day.toEpochDay() * 86_400 + h * 3600 + m * 60 - off
    private val nowMs = (at(today, 21) * 1000)

    private fun app(day: LocalDate, h: Int, minutes: Int, pkg: String = "ig") =
        UsageRecord.App(at(day, h), minutes * 60L, off, pkg)

    private val recordingSince = UsageRecord.Begin(at(today.minusDays(20), 0), off)

    @Test
    fun `usage across midnight is split between the two days`() {
        val records = listOf(recordingSince, UsageRecord.App(at(today.minusDays(1), 23, 40), 40 * 60L, off, "ig"))
        val days = UsageInsights.days(records, today, 2, nowMs)
        assertEquals(20, days[0].minutes)
        assertEquals(20, days[1].minutes)
    }

    @Test
    fun `days without recording have no data rather than zero`() {
        val records = listOf(
            UsageRecord.Begin(at(today.minusDays(6), 9), off),
            UsageRecord.End(at(today.minusDays(5), 9), off),
            UsageRecord.Begin(at(today, 8), off),
        )
        val days = UsageInsights.days(records, today, 7, nowMs)
        assertEquals(0, days[0].minutes) // recorded, nothing used
        assertNull(days[2].minutes) // service was off
        assertEquals(0, days[6].minutes)
    }

    @Test
    fun `week comparison needs two full weeks and never shames`() {
        // Completed days only: 14..8 days ago vs 7..1 days ago; today never counts.
        val lastWeek = (14 downTo 8).map { app(today.minusDays(it.toLong()), 20, 60) }
        val thisWeek = (7 downTo 1).map { app(today.minusDays(it.toLong()), 20, 40) } + app(today, 9, 0)
        val summary = UsageInsights.summarize(listOf(recordingSince) + lastWeek + thisWeek, today, nowMs, BedtimeLearning.Learning(0, 0))
        assertEquals(listOf<Insight>(Insight.LessThanLastWeek(140)), summary.insights)

        val more = (7 downTo 1).map { app(today.minusDays(it.toLong()), 20, 90) }
        assertEquals(
            listOf<Insight>(Insight.MoreThanLastWeek),
            UsageInsights.summarize(listOf(recordingSince) + lastWeek + more, today, nowMs, BedtimeLearning.Learning(0, 0)).insights,
        )

        val similar = (7 downTo 1).map { app(today.minusDays(it.toLong()), 20, 58) }
        assertEquals(
            listOf<Insight>(Insight.SimilarToLastWeek),
            UsageInsights.summarize(listOf(recordingSince) + lastWeek + similar, today, nowMs, BedtimeLearning.Learning(0, 0)).insights,
        )
    }

    @Test
    fun `a quiet morning never reads as using less`() {
        // Two identical full weeks; today has barely started.
        val records = listOf(recordingSince) + (14 downTo 1).map { app(today.minusDays(it.toLong()), 20, 60) } + app(today, 8, 1)
        assertEquals(
            listOf<Insight>(Insight.SimilarToLastWeek),
            UsageInsights.summarize(records, today, nowMs, BedtimeLearning.Learning(0, 0)).insights,
        )
    }

    @Test
    fun `with a gap in the last two weeks there is no comparison`() {
        val records = listOf(UsageRecord.Begin(at(today.minusDays(5), 0), off)) + app(today, 20, 30)
        val summary = UsageInsights.summarize(records, today, nowMs, BedtimeLearning.Learning(1, 5))
        assertEquals(listOf<Insight>(Insight.NotEnoughData), summary.insights)
    }

    @Test
    fun `the bedtime habit comes first, rounded to five minutes`() {
        val summary = UsageInsights.summarize(listOf(recordingSince), today, nowMs, BedtimeLearning.Confident(23 * 60 + 38, 10, 6))
        assertEquals(Insight.PutsPhoneDownAround(23 * 60 + 40), summary.insights.first())
    }

    @Test
    fun `top apps are the three most used this week`() {
        val records = listOf(recordingSince) +
            app(today, 9, 50, "a") + app(today, 10, 10, "b") + app(today, 11, 30, "c") + app(today, 12, 5, "d") +
            app(today.minusDays(10), 9, 500, "old")
        val top = UsageInsights.summarize(records, today, nowMs, BedtimeLearning.Learning(0, 0)).topApps
        assertEquals(listOf("a" to 50, "c" to 30, "b" to 10), top)
        assertTrue(top.none { it.first == "old" })
    }
}
