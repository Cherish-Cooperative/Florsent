package app.florsent.service.audio

import app.florsent.service.effect.VisualEffect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FadeSoundControllerTest {

    private inner class FakePlayer(var grant: Boolean = true) : FadeSoundPlayer {
        val calls = mutableListOf<String>()
        val reclaimedAt = mutableListOf<Long>()
        var grantReclaim = true
        override fun start(fadeInMs: Long): Boolean {
            calls += "start($fadeInMs)"
            return grant
        }
        override fun reclaimFocus(): Boolean {
            calls += "reclaim"
            reclaimedAt += now
            return grantReclaim
        }
        override fun stop(fadeOutMs: Long) { calls += "stop($fadeOutMs)" }
        override fun release() { calls += "release" }
    }

    private var enabled = true
    private var inCall = false
    private val player = FakePlayer()

    /** A manual clock and timer queue: [advance] runs whatever falls due. */
    private var now = 0L
    private val timers = mutableListOf<Pair<Long, () -> Unit>>()
    private val scheduler = DelayScheduler { delayMs, action ->
        val timer = now + delayMs to action
        timers += timer
        ({ timers.remove(timer) })
    }

    private fun advance(ms: Long) {
        val end = now + ms
        while (true) {
            val due = timers.filter { it.first <= end }.minByOrNull { it.first } ?: break
            timers.remove(due)
            now = due.first
            due.second()
        }
        now = end
    }

    private val controller = FadeSoundController(player, { enabled }, { inCall }, scheduler, { now })

    private fun level(l: Float) = controller.render(VisualEffect(l, "com.google.android.youtube"))

    @Test
    fun `starts once when the fade begins, rising over 15 seconds, and fades out in 2 seconds`() {
        level(0f)
        level(0.01f)
        level(0.2f)
        level(0.9f)
        level(0f)
        assertEquals(listOf("start(15000)", "stop(2000)"), player.calls)
    }

    @Test
    fun `tapping the nudge lets the rain linger to overlap the breathing ambience`() {
        level(0.6f)
        controller.handOffToBreathing()
        level(0f)
        // A later, ordinary exit uses the short fade again.
        level(0.4f)
        level(0f)
        assertEquals(listOf("start(15000)", "stop(3500)", "start(15000)", "stop(2000)"), player.calls)
    }

    @Test
    fun `a hand-off request without sound playing changes nothing`() {
        controller.handOffToBreathing()
        level(0.3f)
        level(0f)
        assertEquals(listOf("start(15000)", "stop(2000)"), player.calls)
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
        assertEquals(listOf("start(15000)", "stop(2000)"), player.calls)
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
        assertEquals(listOf("start(15000)", "stop(2000)"), player.calls)
    }

    @Test
    fun `a new Reel taking focus is answered by asking again within a quarter second`() {
        level(0.3f)
        advance(5_000)
        controller.onFocusLost() // the next Reel starts playing
        advance(200)
        assertEquals("still in the cooldown", listOf("start(15000)"), player.calls)
        advance(50)
        assertEquals("focus requested again, ducking the Reel", listOf("start(15000)", "reclaim"), player.calls)

        // Every swipe takes focus again; each one is answered the same way, the rain never stops.
        repeat(3) {
            advance(6_000)
            controller.onFocusLost()
            advance(250)
        }
        assertEquals(listOf("start(15000)") + List(4) { "reclaim" }, player.calls)
    }

    @Test
    fun `a burst of losses during the cooldown is answered with a single request`() {
        level(0.3f)
        advance(5_000)
        repeat(10) {
            controller.onFocusLost()
            advance(20)
        }
        assertTrue(controller.isReclaimPending)
        advance(50)
        assertEquals(listOf("start(15000)", "reclaim"), player.calls)
        assertFalse(controller.isReclaimPending)
    }

    @Test
    fun `an app that grabs focus right back gets slower answers, then the rain stops asking but plays on`() {
        level(0.3f)
        advance(5_000)
        val lostAt = mutableListOf<Long>()
        lostAt += now
        controller.onFocusLost()
        while (controller.isReclaimPending) {
            assertTrue("the fight has to end", lostAt.size <= 20)
            advance(timers.minOf { it.first } - now) // runs the request
            advance(100) // and the other app takes focus back 100 ms later
            lostAt += now
            controller.onFocusLost()
        }
        val cooldowns = player.reclaimedAt.zip(lostAt) { reclaim, lost -> reclaim - lost }
        assertEquals("0.25 s, then doubling, capped", listOf(250L, 500L, 1_000L, 2_000L, 4_000L), cooldowns)
        assertFalse("the rain keeps its level", player.calls.any { it.startsWith("stop") })

        // No more requests for the rest of the fade, however many losses arrive.
        controller.onFocusLost()
        advance(30_000)
        assertEquals(5, player.calls.count { it == "reclaim" })
        level(0.6f)
        assertEquals(1, player.calls.count { it.startsWith("start") })

        // The next fade asks again.
        level(0f)
        level(0.1f)
        advance(5_000)
        controller.onFocusLost()
        advance(250)
        assertEquals(6, player.calls.count { it == "reclaim" })
    }

    @Test
    fun `a refused second request counts as pushback and is retried later, not at once`() {
        level(0.3f)
        advance(5_000)
        player.grantReclaim = false
        controller.onFocusLost()
        advance(250)
        assertEquals(listOf("start(15000)", "reclaim"), player.calls)
        advance(499)
        assertEquals("waits the doubled cooldown", 1, player.calls.count { it == "reclaim" })
        player.grantReclaim = true
        advance(1)
        assertEquals(2, player.calls.count { it == "reclaim" })
        assertFalse(controller.isReclaimPending)
    }

    @Test
    fun `a call taking focus stops the rain for the rest of this fade, with no retry`() {
        level(0.3f)
        advance(5_000)
        inCall = true // ringing
        controller.onFocusLost()
        assertFalse(controller.isReclaimPending)
        advance(30_000)
        inCall = false
        level(0.5f)
        assertEquals(listOf("start(15000)", "stop(2000)"), player.calls)
    }

    @Test
    fun `a call that starts during the cooldown cancels the request`() {
        level(0.3f)
        advance(5_000)
        controller.onFocusLost()
        inCall = true
        advance(250)
        assertEquals(listOf("start(15000)", "stop(2000)"), player.calls)
    }

    @Test
    fun `leaving the app or tapping the nudge cancels a pending request`() {
        level(0.3f)
        advance(5_000)
        controller.onFocusLost()
        level(0f) // left the app
        advance(5_000)
        assertEquals(listOf("start(15000)", "stop(2000)"), player.calls)

        level(0.3f)
        advance(5_000)
        controller.onFocusLost()
        controller.handOffToBreathing()
        controller.onFocusLost() // the breathing screen takes focus during the hand-off
        level(0f)
        advance(5_000)
        assertEquals(listOf("start(15000)", "stop(2000)", "start(15000)", "stop(3500)"), player.calls)
    }

    @Test
    fun `focus coming back on its own cancels the pending request`() {
        level(0.3f)
        advance(5_000)
        controller.onFocusLost()
        advance(100)
        controller.onFocusGained()
        advance(5_000)
        assertEquals(listOf("start(15000)"), player.calls)
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
