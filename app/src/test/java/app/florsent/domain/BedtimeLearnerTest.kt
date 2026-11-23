package app.florsent.domain

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The learner on synthetic nights (Taiwan, UTC+8 unless noted). Every edge case from the design
 * review is pinned here.
 */
class BedtimeLearnerTest {

    private val tw = 8 * 3600
    private val base = LocalDate.of(2026, 11, 1).toEpochDay()

    /** Epoch seconds for local [h]:[m] on day [d] (day 0 = 2026-11-01) at UTC offset [offset]. */
    private fun at(d: Int, h: Int, m: Int = 0, offset: Int = tw) = (base + d) * 86_400 + h * 3600 + m * 60 - offset

    private fun screen(d: Int, h: Int, m: Int, minutes: Int, offset: Int = tw) =
        UsageRecord.Screen(at(d, h, m, offset), minutes * 60L, offset)

    private fun app(d: Int, h: Int, m: Int, minutes: Int) = UsageRecord.App(at(d, h, m), minutes * 60L, tw, "com.instagram.android")

    /** A plain day ending with evening scrolling that stops at [offH]:[offM] (may be after midnight). */
    private fun day(d: Int, offH: Int = 23, offM: Int = 40, offset: Int = tw): List<UsageRecord> {
        val endDay = if (offH < 12) d + 1 else d
        val startOfScroll = (offH * 60 + offM - 70 + 24 * 60) % (24 * 60)
        val scrollDay = if (offH < 12 && startOfScroll > 12 * 60) d else endDay
        return listOf(
            screen(d, 7, 30, 20, offset),
            screen(d, 12, 30, 15, offset),
            screen(d, 19, 0, 30, offset),
            UsageRecord.Screen(at(scrollDay, startOfScroll / 60, startOfScroll % 60, offset), 70 * 60L, offset),
        )
    }

    private fun begin(d: Int = 0) = UsageRecord.Begin(at(d, 0, 0), tw)

    private fun nights(records: List<UsageRecord>, nowDay: Int, nowH: Int = 13) =
        BedtimeLearner.observe(records.sortedBy { it.startSec }, at(nowDay, nowH) * 1000)

    private fun learn(records: List<UsageRecord>, nowDay: Int, nowH: Int = 13): BedtimeLearning {
        val now = at(nowDay, nowH) * 1000
        val sorted = records.sortedBy { it.startSec }
        return BedtimeLearner.estimate(BedtimeLearner.observe(sorted, now), sorted, now, tw)
    }

    private fun minute(h: Int, m: Int) = h * 60 + m

    @Test
    fun `regular nights give a confident estimate at the usual time`() {
        val records = listOf(begin()) + (0..5).flatMap { day(it) } + screen(6, 7, 30, 20)
        val learning = learn(records, nowDay = 6)
        assertTrue(learning is BedtimeLearning.Confident)
        assertEquals(minute(23, 40), (learning as BedtimeLearning.Confident).minuteOfDay)
    }

    @Test
    fun `cold start says learning, not a guess`() {
        val records = listOf(begin()) + day(0) + day(1) + screen(2, 7, 30, 20)
        assertEquals(BedtimeLearning.Learning(nightsObserved = 2, daysRecorded = 2), learn(records, nowDay = 2))
    }

    @Test
    fun `checking the alarm at 02 00 and 05 30 does not count as waking up`() {
        val records = listOf(begin()) + day(0) + screen(1, 2, 0, 1) + screen(1, 5, 30, 1) + screen(1, 7, 30, 20)
        val obs = nights(records, nowDay = 1)
        assertEquals(1, obs.size)
        assertEquals(minute(11, 40), obs[0].minuteOfNight) // 23:40 is 11 h 40 after noon
    }

    @Test
    fun `answering a message for five minutes in the night is absorbed too`() {
        val records = listOf(begin()) + day(0) + screen(1, 2, 0, 5) + screen(1, 7, 30, 20)
        assertEquals(minute(11, 40), nights(records, nowDay = 1).single().minuteOfNight)
    }

    @Test
    fun `a real wake-up longer than ten minutes splits the night (documented limitation)`() {
        val records = listOf(begin()) + day(0) + screen(1, 2, 0, 20) + screen(1, 7, 30, 20)
        // 23:40-02:00 (2h20) vs 02:20-07:30 (5h10): the longer one wins.
        assertEquals(minute(14, 20), nights(records, nowDay = 1).single().minuteOfNight)
    }

    @Test
    fun `falling asleep with a video playing makes the night uncertain`() {
        val records = listOf(begin()) + day(0).dropLast(1) + screen(0, 23, 0, 200) + screen(1, 7, 30, 20)
        assertTrue(nights(records, nowDay = 1).isEmpty())
    }

    @Test
    fun `night owls are learned, day sleepers become irregular instead of being guessed`() {
        val owl = listOf(begin()) + (0..4).flatMap { day(it, offH = 3, offM = 0) } + screen(5, 11, 0, 20)
        val owlLearning = learn(owl, nowDay = 5, nowH = 13)
        assertEquals(minute(3, 0), (owlLearning as BedtimeLearning.Confident).minuteOfDay)

        // Night shift: on the phone every two hours through the night, asleep 08:30-15:00. The only
        // long gap starts in the morning, so no 20:00-05:00 onset is ever learned.
        val shift = listOf(begin()) + (0..8).flatMap { d ->
            listOf(screen(d, 15, 0, 60), screen(d, 18, 0, 60), screen(d, 20, 0, 30), screen(d, 22, 0, 30)) +
                listOf(0, 2, 4, 6, 8).map { h -> screen(d + 1, h, 0, 30) }
        }
        assertEquals(BedtimeLearning.Irregular(0), learn(shift, nowDay = 9))
    }

    @Test
    fun `a killed process is unknown, never a dark screen`() {
        val records = listOf(begin()) + day(0) +
            UsageRecord.Unknown(at(1, 1, 0), 2 * 3600L, tw) + UsageRecord.Begin(at(1, 3, 0), tw) + screen(1, 7, 30, 20)
        assertTrue(nights(records, nowDay = 1).isEmpty())
    }

    @Test
    fun `nights while recording was off are skipped`() {
        val records = listOf(begin()) + day(0) + UsageRecord.End(at(0, 23, 50), tw) +
            UsageRecord.Begin(at(1, 9, 0), tw) + screen(1, 9, 5, 20)
        assertTrue(nights(records, nowDay = 1).isEmpty())
    }

    @Test
    fun `a night is only final after the next noon`() {
        val records = listOf(begin()) + day(0) + screen(1, 7, 30, 20)
        assertTrue(nights(records, nowDay = 1, nowH = 11).isEmpty())
        assertEquals(1, nights(records, nowDay = 1, nowH = 12).size)
    }

    @Test
    fun `past nights keep their own wall-clock time after travelling`() {
        // Three nights in Taiwan, then three in New York (UTC-5), all at 23:40 local.
        val ny = -5 * 3600
        val records = listOf(begin()) + (0..2).flatMap { day(it) } + (3..5).flatMap { day(it, offset = ny) } +
            screen(6, 7, 30, 20, ny)
        val now = at(6, 13, offset = ny) * 1000
        val obs = BedtimeLearner.observe(records.sortedBy { it.startSec }, now)
        assertTrue(obs.size >= 5)
        assertTrue(obs.all { it.minuteOfNight == minute(11, 40) })
    }

    @Test
    fun `one all-nighter does not move the weighted median`() {
        val records = listOf(begin()) + (0..5).flatMap { day(it) } + day(6, offH = 4, offM = 50) + screen(7, 11, 0, 20)
        assertEquals(minute(23, 40), (learn(records, nowDay = 7) as BedtimeLearning.Confident).minuteOfDay)
    }

    @Test
    fun `recent nights weigh more than old ones`() {
        val records = listOf(begin()) + (0..9).flatMap { day(it, offH = 0, offM = 30) } +
            (10..14).flatMap { day(it, offH = 23, offM = 0) } + screen(15, 7, 30, 20)
        assertEquals(minute(23, 0), (learn(records, nowDay = 15) as BedtimeLearning.Confident).minuteOfDay)
    }

    @Test
    fun `scattered nights stay learning for a week, then irregular`() {
        val times = listOf(21 to 0, 2 to 30, 22 to 0, 4 to 0, 20 to 30, 3 to 30, 21 to 30, 1 to 0)
        val records = listOf(begin()) + times.flatMapIndexed { d, (h, m) -> day(d, h, m) } + screen(8, 11, 0, 20)
        assertTrue(learn(records, nowDay = 4) is BedtimeLearning.Learning)
        assertTrue(learn(records, nowDay = 8) is BedtimeLearning.Irregular)
    }

    @Test
    fun `pre-sleep social minutes are measured in the two hours before onset`() {
        val records = listOf(begin()) + day(0) + app(0, 22, 40, 50) + app(0, 19, 0, 30) + screen(1, 7, 30, 20)
        assertEquals(50, nights(records, nowDay = 1).single().preSleepAppMinutes)
    }

    @Test
    fun `the learned value drifts with behaviour but never changes an accepted window`() {
        // Onset moves 5 minutes earlier every night (the nudge works).
        val records = listOf(begin()) + (0..29).flatMap { d ->
            val total = 23 * 60 + 40 - 5 * (d % 30)
            day(d, total / 60, total % 60)
        } + screen(30, 7, 30, 20)
        val learned = learn(records, nowDay = 30) as BedtimeLearning.Confident
        assertTrue("learner follows the trend", learned.minuteOfDay < minute(23, 0))

        // The window depends only on the anchor the user accepted, so it does not chase itself.
        val config = BedtimeConfig(anchorMinuteOfDay = minute(23, 40))
        val zone = java.time.ZoneOffset.ofHours(8)
        val starts = (0..60).map { d ->
            val w = BedtimeWindows.forAnchorDate(LocalDate.of(2026, 11, 1).plusDays(d.toLong()), config, zone)
            java.time.Instant.ofEpochMilli(w.startMs).atZone(zone).toLocalTime()
        }.toSet()
        assertEquals(setOf(java.time.LocalTime.of(23, 10)), starts)
    }

    @Test
    fun `weighted median basics`() {
        assertEquals(2.0, BedtimeLearner.weightedMedian(listOf(1.0, 2.0, 3.0), listOf(1.0, 1.0, 1.0)), 0.0)
        assertEquals(3.0, BedtimeLearner.weightedMedian(listOf(1.0, 2.0, 3.0), listOf(1.0, 1.0, 5.0)), 0.0)
        assertEquals(1420, BedtimeLearner.roundTo5(1418))
        assertEquals(0, BedtimeLearner.roundTo5(1439))
    }
}
