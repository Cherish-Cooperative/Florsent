package app.florsent.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SaturationPlannerTest {

    @Test
    fun `level maps to saturation from 100 down to 0 in steps`() {
        assertEquals(100, SaturationPlanner.saturationFor(0f))
        assertEquals(50, SaturationPlanner.saturationFor(0.5f))
        assertEquals(0, SaturationPlanner.saturationFor(1f))
        assertEquals(0, SaturationPlanner.saturationFor(2f))
        assertEquals(100, SaturationPlanner.saturationFor(-1f))
        (0..100).map { SaturationPlanner.saturationFor(it / 100f) }.forEach {
            assertEquals("quantized", 0, it % SaturationPlanner.STEP)
        }
    }

    @Test
    fun `saturation never increases while the level rises`() {
        val values = (0..1000).map { SaturationPlanner.saturationFor(it / 1000f) }
        assertTrue(values.zipWithNext().all { (a, b) -> b <= a })
    }

    @Test
    fun `a 3 minute fade needs only about twenty shell calls`() {
        val distinct = (0..180).map { SaturationPlanner.saturationFor(Easing.smoothstep(it / 180f)) }.toSet()
        assertTrue("${distinct.size}", distinct.size <= 21)
    }

    @Test
    fun `nothing is desired when disabled, without a target or at full color`() {
        assertEquals(emptyMap<String, Int>(), SaturationPlanner.desired("a", 0.8f, enabled = false))
        assertEquals(emptyMap<String, Int>(), SaturationPlanner.desired(null, 0.8f, enabled = true))
        assertEquals(emptyMap<String, Int>(), SaturationPlanner.desired("a", 0.01f, enabled = true))
        assertEquals(mapOf("a" to 20), SaturationPlanner.desired("a", 0.8f, enabled = true))
    }

    @Test
    fun `restores come first, then changes, and unchanged levels are skipped`() {
        val commands = SaturationPlanner.commands(
            applied = mapOf("old" to 40, "same" to 30),
            desired = mapOf("same" to 30, "new" to 80),
        )
        assertEquals(
            listOf(SaturationCommand("old", 100), SaturationCommand("new", 80)),
            commands,
        )
    }

    @Test
    fun `an empty desire restores everything`() {
        val commands = SaturationPlanner.commands(mapOf("b" to 10, "a" to 50), emptyMap())
        assertEquals(listOf(SaturationCommand("a", 100), SaturationCommand("b", 100)), commands)
    }
}
