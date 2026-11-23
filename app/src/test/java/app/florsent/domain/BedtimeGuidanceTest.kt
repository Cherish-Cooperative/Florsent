package app.florsent.domain

import app.florsent.domain.GuidanceEvent.BreathingCompleted
import app.florsent.domain.GuidanceEvent.ForegroundChanged
import app.florsent.domain.GuidanceEvent.NudgeAccepted
import app.florsent.domain.GuidanceEvent.Tick
import java.time.LocalDate
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Bedtime wind-down inside the engine (UTC+8, anchor 23:40 → window 23:10-05:00). */
class BedtimeGuidanceTest {

    private val zone = ZoneOffset.ofHours(8)
    private val social = "com.instagram.android"
    private val bedtime = BedtimeConfig(anchorMinuteOfDay = 23 * 60 + 40)
    private val config = GuidanceConfig(
        watchedPackages = setOf(social),
        sessionBudgetMs = 60 * MIN, // high, so bedtime is what triggers
        dailyBudgetMs = 600 * MIN,
        fadeDurationMs = 2 * MIN,
        bedtime = bedtime,
    )

    private inner class Harness(startH: Int, startM: Int = 0, startS: Int = 0, var cfg: GuidanceConfig = config) {
        val engine = GuidanceEngine { zone }
        var now = LocalDate.of(2026, 11, 24).atTime(startH, startM, startS).atZone(zone).toInstant().toEpochMilli()
        var state = GuidanceState()
        fun send(e: (Long) -> GuidanceEvent) { state = engine.reduce(state, e(now), cfg) }
        fun open(pkg: String?) = send { ForegroundChanged(pkg, it) }
        fun advance(ms: Long) {
            val end = now + ms
            while (now < end) {
                now = minOf(now + 1_000, end)
                if (engine.needsTick(state, cfg)) send { Tick(it) }
            }
        }
        val effect get() = engine.effect(state, cfg, now)
    }

    @Test
    fun `window edges, including late anchors`() {
        val day = LocalDate.of(2026, 11, 24)
        fun local(ms: Long) = java.time.Instant.ofEpochMilli(ms).atZone(zone).toLocalDateTime()
        val w = BedtimeWindows.forAnchorDate(day, bedtime, zone)
        assertEquals(day.atTime(23, 10), local(w.startMs))
        assertEquals(day.plusDays(1).atTime(5, 0), local(w.endMs))

        val late = BedtimeWindows.forAnchorDate(day, BedtimeConfig(anchorMinuteOfDay = 4 * 60), zone)
        assertEquals("anchor 04:00 belongs to the night before", day.minusDays(1).atTime(3, 30).plusDays(1), local(late.startMs))
        assertEquals("still two hours after the anchor", day.atTime(6, 0), local(late.endMs))

        val early = BedtimeWindows.forAnchorDate(day, BedtimeConfig(anchorMinuteOfDay = 21 * 60), zone)
        assertEquals(day.atTime(20, 30), local(early.startMs))
        assertEquals(day.plusDays(1).atTime(5, 0), local(early.endMs))
    }

    @Test
    fun `before the window nothing bedtime-related happens`() {
        val h = Harness(22, 0)
        h.open(social)
        h.advance(60 * MIN) // until 23:00
        assertEquals(0L, h.state.bedtimeUsedMs)
        assertEquals(GuidanceEffect.None, h.effect)
    }

    @Test
    fun `15 minutes of cumulative use inside the window starts a bedtime fade and nudge`() {
        val h = Harness(23, 10)
        h.open(social)
        h.advance(8 * MIN)
        h.open("com.android.chrome")
        h.advance(10 * MIN) // a pause does not reset the count
        h.open(social)
        h.advance(7 * MIN)
        assertEquals(15 * MIN, h.state.bedtimeUsedMs)
        h.advance(60 * 1000)
        assertTrue(h.effect.level > 0f)
        assertEquals(GuidanceContext.Bedtime, h.effect.context)
        assertTrue("nudge at half fade", h.effect.showNudge)
    }

    @Test
    fun `the threshold choice is respected`() {
        val h = Harness(23, 10, cfg = config.copy(bedtime = bedtime.copy(thresholdMs = 10 * MIN)))
        h.open(social)
        h.advance(10 * MIN + 30_000)
        assertEquals(GuidanceContext.Bedtime, h.effect.context)
    }

    @Test
    fun `two nudges per night at most, then only the fade`() {
        val h = Harness(23, 10)
        h.open(social)
        h.advance(16 * MIN)
        assertTrue(h.effect.showNudge)

        // First: breathe, then 30 minutes of bedtime quiet.
        h.send { NudgeAccepted(it) }
        h.open("app.florsent")
        h.send { BreathingCompleted(it) }
        assertEquals(GuidanceMode.Cooldown, h.state.mode)
        assertEquals(h.now + 30 * MIN, h.state.cooldownUntilMs)
        h.advance(30 * MIN)

        // Second: 7 more minutes is enough.
        h.open(social)
        h.advance(7 * MIN + 60_000)
        assertTrue(h.effect.showNudge)
        assertEquals(GuidanceContext.Bedtime, h.effect.context)
        h.send { NudgeAccepted(it) }
        h.open("app.florsent")
        h.send { BreathingCompleted(it) }
        h.advance(30 * MIN)

        // Third: it still dims, but the nudge stays away.
        h.open(social)
        h.advance(7 * MIN + 2 * MIN)
        assertTrue(h.effect.level > 0.5f)
        assertFalse(h.effect.showNudge)
    }

    @Test
    fun `accepting the bedtime nudge clears every effect while breathing`() {
        val h = Harness(23, 10)
        h.open(social)
        h.advance(16 * MIN)
        assertTrue(h.effect.showNudge)
        h.send { NudgeAccepted(it) }
        h.open("app.florsent")
        assertEquals(GuidanceMode.Breathing, h.state.mode)
        assertEquals(GuidanceEffect.None, h.effect)
        h.advance(90_000)
        assertEquals(GuidanceEffect.None, h.effect)
        h.send { BreathingCompleted(it) }
        assertEquals(GuidanceEffect.None, h.effect)
    }

    @Test
    fun `a daytime fade that is already running keeps its daytime wording`() {
        val h = Harness(22, 0, cfg = config.copy(sessionBudgetMs = 60 * MIN))
        h.open(social)
        h.advance(61 * MIN) // day fade starts 23:00, before the window
        assertEquals(GuidanceContext.Day, h.effect.context)
        h.advance(20 * MIN) // now deep inside the window
        assertEquals(GuidanceContext.Day, h.effect.context)
    }

    @Test
    fun `midnight does not reset tonight's count`() {
        val h = Harness(23, 50)
        h.open(social)
        h.advance(8 * MIN)
        h.open(null)
        h.advance(10 * MIN) // past midnight
        h.open(social)
        h.advance(7 * MIN)
        assertEquals(15 * MIN, h.state.bedtimeUsedMs)
    }

    @Test
    fun `the window ends in the morning and the counters start fresh next night`() {
        val h = Harness(4, 50)
        h.open(social)
        h.advance(5 * MIN) // until 04:55
        assertTrue(h.state.bedtimeWindowStartMs != null)
        h.advance(10 * MIN) // 05:05
        assertNull(h.state.bedtimeWindowStartMs)
        assertEquals(0L, h.state.bedtimeUsedMs)
    }

    @Test
    fun `without a bedtime there is no window at all`() {
        val h = Harness(23, 30, cfg = config.copy(bedtime = null))
        h.open(social)
        h.advance(30 * MIN)
        assertEquals(0L, h.state.bedtimeUsedMs)
        assertEquals(GuidanceEffect.None, h.effect)
    }

    @Test
    fun `ticks wake for the window start and for the bedtime threshold`() {
        val h = Harness(23, 9, 50)
        h.open(social)
        assertEquals(10_000L, h.engine.nextTickDelayMs(h.state, h.cfg, h.now)) // window starts in 10 s

        val inside = Harness(23, 10)
        inside.open(social)
        inside.advance(14 * MIN + 55_000)
        assertEquals(5_000L, inside.engine.nextTickDelayMs(inside.state, inside.cfg, inside.now))
    }

    private companion object {
        const val MIN = 60_000L
    }
}
