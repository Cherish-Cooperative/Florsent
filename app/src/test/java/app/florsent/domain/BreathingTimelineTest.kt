package app.florsent.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BreathingTimelineTest {

    private val equal = BreathingPatterns.Equal
    private val box = BreathingPatterns.Box
    private val relax = BreathingPatterns.Relax478
    private val resonance = BreathingPatterns.Resonance

    @Test
    fun `built-in patterns match their names`() {
        assertEquals("4 · 4 · 4", equal.rhythmLabel)
        assertEquals("4 · 4 · 4 · 4", box.rhythmLabel)
        assertEquals("4 · 7 · 8", relax.rhythmLabel)
        assertEquals("5.5 · 5.5", resonance.rhythmLabel)
        assertEquals("4-7-8 is limited to four cycles", 4, relax.cycles)
        assertEquals(4, BreathingPatterns.all.map { it.id }.toSet().size)
    }

    @Test
    fun `every pattern is a short session of about a minute`() {
        BreathingPatterns.all.forEach { p ->
            assertTrue("${p.id}: ${p.totalMs}", p.totalMs in 60_000..90_000)
        }
        assertEquals(76_000L, relax.totalMs)
        assertEquals(77_000L, resonance.totalMs)
    }

    @Test
    fun `unknown ids fall back to the default pattern`() {
        assertEquals(BreathingPatterns.default, BreathingPatterns.byId(null))
        assertEquals(BreathingPatterns.default, BreathingPatterns.byId("gone"))
        assertEquals(box, BreathingPatterns.byId(box.id))
    }

    @Test
    fun `expansion opens on inhale, stays during hold and closes on exhale`() {
        assertEquals(0f, BreathingTimeline.expansionAt(equal, 0), 0.0001f)
        assertEquals(0.5f, BreathingTimeline.expansionAt(equal, 2_000), 0.0001f)
        assertEquals(1f, BreathingTimeline.expansionAt(equal, 4_000), 0.0001f)
        assertEquals(1f, BreathingTimeline.expansionAt(equal, 7_999), 0.0001f)
        assertEquals(0.5f, BreathingTimeline.expansionAt(equal, 10_000), 0.0001f)
        assertEquals("next cycle starts closed", 0f, BreathingTimeline.expansionAt(equal, 12_000), 0.0001f)
    }

    @Test
    fun `box breathing holds empty at the end of each cycle`() {
        assertEquals(0f, BreathingTimeline.expansionAt(box, 14_000), 0.0001f)
        assertEquals(BreathPhaseKind.HoldOut, BreathingTimeline.displayAt(box, 14_000).kind)
    }

    @Test
    fun `expansion is continuous across phase boundaries`() {
        BreathingPatterns.all.forEach { p ->
            var previous = BreathingTimeline.expansionAt(p, 0)
            var t = 0L
            while (t < p.totalMs) {
                val e = BreathingTimeline.expansionAt(p, t)
                assertTrue("${p.id} jumped at $t: $previous -> $e", kotlin.math.abs(e - previous) < 0.02f)
                previous = e
                t += 16
            }
        }
    }

    @Test
    fun `display counts down whole seconds and advances phases and cycles`() {
        val start = BreathingTimeline.displayAt(relax, 0)
        assertEquals(BreathPhaseKind.Inhale, start.kind)
        assertEquals(4, start.secondsLeft)
        assertEquals(0, start.cycleIndex)

        assertEquals(1, BreathingTimeline.displayAt(relax, 3_001).secondsLeft)

        val hold = BreathingTimeline.displayAt(relax, 4_000)
        assertEquals(BreathPhaseKind.HoldIn, hold.kind)
        assertEquals(7, hold.secondsLeft)

        val exhale = BreathingTimeline.displayAt(relax, 11_500)
        assertEquals(BreathPhaseKind.Exhale, exhale.kind)
        assertEquals(8, exhale.secondsLeft)

        val secondCycle = BreathingTimeline.displayAt(relax, 19_000)
        assertEquals(1, secondCycle.cycleIndex)
        assertEquals(0, secondCycle.phaseIndex)
    }

    @Test
    fun `resonance uses half-second phases`() {
        assertEquals(BreathPhaseKind.Inhale, BreathingTimeline.displayAt(resonance, 5_499).kind)
        assertEquals(BreathPhaseKind.Exhale, BreathingTimeline.displayAt(resonance, 5_500).kind)
        assertEquals(6, BreathingTimeline.displayAt(resonance, 0).secondsLeft)
    }

    @Test
    fun `session finishes exactly at the total duration`() {
        assertFalse(BreathingTimeline.displayAt(box, box.totalMs - 1).finished)
        val end = BreathingTimeline.displayAt(box, box.totalMs)
        assertTrue(end.finished)
        assertEquals(box.cycles - 1, end.cycleIndex)
        assertEquals(1f, BreathingTimeline.sessionProgress(box, box.totalMs + 5_000), 0f)
        assertEquals(0.5f, BreathingTimeline.sessionProgress(box, box.totalMs / 2), 0.0001f)
    }

    @Test
    fun `idle expansion stays in a gentle range`() {
        (0L..16_000L step 250).forEach { t ->
            val e = BreathingTimeline.idleExpansion(t)
            assertTrue("$t -> $e", e in 0.25f..0.55f)
        }
    }

    @Test
    fun `completion starts a cooldown only for real guidance`() {
        val engine = GuidanceEngine()
        assertTrue(engine.completionStartsCooldown(GuidanceState(mode = GuidanceMode.Breathing)))
        assertFalse(
            engine.completionStartsCooldown(GuidanceState(mode = GuidanceMode.Breathing, breathingFromPreview = true)),
        )
        assertTrue(engine.completionStartsCooldown(GuidanceState(overBudgetMs = 1)))
        assertFalse(engine.completionStartsCooldown(GuidanceState()))
        assertFalse(engine.completionStartsCooldown(GuidanceState(mode = GuidanceMode.Cooldown)))
    }
}
