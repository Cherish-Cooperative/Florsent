package app.florsent.ui.grounding

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class PromptPickerTest {

    @Test
    fun `no prompt comes back until two thirds of the list has been shown`() {
        val size = 32
        val random = Random(7)
        var recent = emptyList<Int>()
        val picks = mutableListOf<Int>()
        repeat(500) {
            val next = PromptPicker.next(size, recent, random)
            picks += next
            recent = PromptPicker.remember(size, recent, next)
        }
        val window = PromptPicker.memory(size) + 1
        picks.windowed(window).forEach { assertEquals("a repeat inside $window picks", window, it.toSet().size) }
        assertEquals("every prompt gets its turn", size, picks.toSet().size)
    }

    @Test
    fun `stale indices from a longer list are ignored`() {
        val recent = listOf(40, 2, 99, 1)
        repeat(50) {
            val next = PromptPicker.next(3, recent, Random(it))
            assertTrue(next in 0 until 3)
            assertFalse(next == 2)
        }
        assertEquals(listOf(0, 2), PromptPicker.remember(3, recent, 0))
    }

    @Test
    fun `a single prompt is always picked`() {
        assertEquals(0, PromptPicker.next(1, listOf(0)))
    }
}
