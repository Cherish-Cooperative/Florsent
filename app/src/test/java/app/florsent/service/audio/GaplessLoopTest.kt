package app.florsent.service.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class GaplessLoopTest {

    private class FakeSegment(val id: Int) : LoopSegment {
        override var isPlaying = false
        var lastVolume = -1f
        var queuedNext: LoopSegment? = null
        var released = false
        private var onDone: (() -> Unit)? = null
        override fun start() { isPlaying = true }
        override fun pause() { isPlaying = false }
        override fun setVolume(volume: Float) { lastVolume = volume }
        override fun setNext(next: LoopSegment?) { queuedNext = next }
        override fun setOnCompletion(listener: () -> Unit) { onDone = listener }
        override fun release() { released = true; isPlaying = false }

        /** What the framework does at the end of a pass with a next player set. */
        fun finish() {
            isPlaying = false
            (queuedNext as? FakeSegment)?.isPlaying = true
            onDone?.invoke()
        }
    }

    private val made = mutableListOf<FakeSegment>()
    private fun loop() = GaplessLoop { FakeSegment(made.size).also { made += it } }

    @Test
    fun `the next pass is always queued behind the playing one`() {
        val loop = loop()
        loop.start()
        assertEquals(2, made.size)
        assertSame(made[1], made[0].queuedNext)
        assertTrue(loop.isPlaying)
    }

    @Test
    fun `at the end of a pass the queued one takes over without a gap and a new one is queued`() {
        val loop = loop()
        loop.start()
        made[0].finish()
        assertTrue("old pass freed", made[0].released)
        assertTrue("next pass already playing", made[1].isPlaying)
        assertSame(made[2], made[1].queuedNext)
        assertTrue(loop.isPlaying)

        made[1].finish()
        assertTrue(made[1].released)
        assertSame(made[3], made[2].queuedNext)
    }

    @Test
    fun `volume reaches the playing and the queued pass, and survives the hand-over`() {
        val loop = loop()
        loop.setVolume(0.3f)
        assertEquals(0.3f, made[0].lastVolume, 0f)
        assertEquals(0.3f, made[1].lastVolume, 0f)
        made[0].finish()
        assertEquals("freshly queued pass starts at the current volume", 0.3f, made[2].lastVolume, 0f)
    }

    @Test
    fun `pause, resume and release act on the right passes`() {
        val loop = loop()
        loop.start()
        loop.pause()
        assertFalse(loop.isPlaying)
        loop.start()
        assertTrue(loop.isPlaying)
        loop.release()
        assertTrue(made.all { it.released })
        assertFalse(loop.isPlaying)
    }

    @Test
    fun `a file that cannot be opened gives an invalid, harmless loop`() {
        val loop = GaplessLoop { null }
        assertFalse(loop.isValid)
        loop.start()
        loop.setVolume(1f)
        loop.release()
        assertFalse(loop.isPlaying)
    }
}
