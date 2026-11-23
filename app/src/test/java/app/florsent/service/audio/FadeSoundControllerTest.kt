package app.florsent.service.audio

import app.florsent.service.effect.VisualEffect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FadeSoundControllerTest {

    private class FakePlayer(var grant: Boolean = true) : FadeSoundPlayer {
        val calls = mutableListOf<String>()
        override fun start(fadeInMs: Long): Boolean {
            calls += "start($fadeInMs)"
            return grant
        }
        override fun stop(fadeOutMs: Long) { calls += "stop($fadeOutMs)" }
        override fun release() { calls += "release" }
    }

    private var enabled = true
    private var inCall = false
    private val player = FakePlayer()
    private val controller = FadeSoundController(player, { enabled }, { inCall })

    private fun level(l: Float) = controller.render(VisualEffect(l, "com.google.android.youtube"))

    @Test
    fun `starts once when the fade begins, rising over 15 seconds, and fades out in 1_5 seconds`() {
        level(0f)
        level(0.01f)
        level(0.2f)
        level(0.9f)
        level(0f)
        assertEquals(listOf("start(15000)", "stop(1500)"), player.calls)
    }

    @Test
    fun `tapping the nudge lets the rain linger to overlap the breathing ambience`() {
        level(0.6f)
        controller.handOffToBreathing()
        level(0f)
        // A later, ordinary exit uses the short fade again.
        level(0.4f)
        level(0f)
        assertEquals(listOf("start(15000)", "stop(3500)", "start(15000)", "stop(1500)"), player.calls)
    }

    @Test
    fun `a hand-off request without sound playing changes nothing`() {
        controller.handOffToBreathing()
        level(0.3f)
        level(0f)
        assertEquals(listOf("start(15000)", "stop(1500)"), player.calls)
    }

    @Test
    fun `stays silent when the setting is off`() {
        enabled = false
        level(0.5f)
        assertTrue(player.calls.isEmpty())
    }

    @Test
    fun `turning the setting off mid-fade fades the sound out`() {
        level(0.3f)
        enabled = false
        level(0.3f)
        assertEquals(listOf("start(15000)", "stop(1500)"), player.calls)
    }

    @Test
    fun `never plays during a call, and a call starting mid-fade stops it`() {
        inCall = true
        level(0.4f)
        assertTrue(player.calls.isEmpty())

        inCall = false
        level(0.5f)
        inCall = true
        level(0.6f)
        assertEquals(listOf("start(15000)", "stop(1500)"), player.calls)
    }

    @Test
    fun `losing focus keeps it quiet for the rest of this fade, then the next fade plays again`() {
        level(0.2f)
        controller.onFocusLost() // e.g. the user started music in another app
        level(0.4f)
        level(0.8f)
        assertEquals("no fighting for focus", listOf("start(15000)"), player.calls)

        level(0f) // fade over
        level(0.1f) // next fade
        assertEquals(listOf("start(15000)", "start(15000)"), player.calls)
    }

    @Test
    fun `a refused focus request is not retried every second`() {
        player.grant = false
        level(0.1f)
        level(0.2f)
        level(0.3f)
        assertEquals(listOf("start(15000)"), player.calls)
    }

    @Test
    fun `release frees the player`() {
        level(0.5f)
        controller.release()
        assertEquals(listOf("start(15000)", "release"), player.calls)
    }

    @Test
    fun `fade-in curve starts barely audible and ends at the target`() {
        assertEquals(0f, FadeSoundCurve.volumeAt(0f), 0f)
        assertTrue(FadeSoundCurve.volumeAt(0.25f) < 0.1f * FadeSoundCurve.TARGET_VOLUME)
        assertEquals(FadeSoundCurve.TARGET_VOLUME, FadeSoundCurve.volumeAt(1f), 0f)
        assertEquals(FadeSoundCurve.TARGET_VOLUME, FadeSoundCurve.volumeAt(3f), 0f)
        var previous = -1f
        for (i in 0..100) {
            val v = FadeSoundCurve.volumeAt(i / 100f)
            assertTrue(v >= previous)
            previous = v
        }
    }
}
