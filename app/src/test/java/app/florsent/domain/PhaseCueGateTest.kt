package app.florsent.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PhaseCueGateTest {

    @Test
    fun `each phase of a session is cued exactly once`() {
        val gate = PhaseCueGate()
        assertTrue(gate.tryCue(1, 0, 0))
        assertFalse(gate.tryCue(1, 0, 0))
        assertFalse(gate.tryCue(1, 0, 0))
        assertTrue(gate.tryCue(1, 0, 1))
        assertFalse(gate.tryCue(1, 0, 1))
    }

    @Test
    fun `only forward, so a phase that went back is never cued again`() {
        val gate = PhaseCueGate()
        gate.tryCue(1, 2, 3)
        assertFalse(gate.tryCue(1, 2, 2))
        assertFalse(gate.tryCue(1, 1, 3))
        assertTrue(gate.tryCue(1, 3, 0))
    }

    @Test
    fun `a whole session cues every phase once, in order`() {
        val gate = PhaseCueGate()
        val pattern = BreathingPatterns.Box
        var cues = 0
        // Simulate a UI that reports every phase twice (e.g. a recreated screen).
        for (cycle in 0 until pattern.cycles) for (phase in pattern.phases.indices) repeat(2) {
            if (gate.tryCue(7, cycle, phase)) cues++
        }
        assertEquals(pattern.cycles * pattern.phases.size, cues)
    }

    @Test
    fun `a new session starts over, and reset forgets everything`() {
        val gate = PhaseCueGate()
        gate.tryCue(1, 5, 2)
        assertTrue(gate.tryCue(2, 0, 0))
        gate.reset()
        assertTrue(gate.tryCue(2, 0, 0))
    }

    @Test
    fun `nonsense indices are ignored`() {
        val gate = PhaseCueGate()
        assertFalse(gate.tryCue(1, -1, 0))
        assertFalse(gate.tryCue(1, 0, -1))
        assertTrue(gate.tryCue(1, 0, 0))
    }
}
