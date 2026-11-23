package app.florsent.domain

import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * P0 on Pixel 10 Pro / Android 17: after the first screen-off, nothing was ever timed again,
 * because the engine only learned about the unlock from USER_PRESENT, which never arrived.
 */
class ScreenUnlockRegressionTest {

    private val ig = "com.instagram.android"
    private val config = GuidanceConfig(watchedPackages = setOf(ig))
    private val engine = GuidanceEngine { ZoneOffset.UTC }
    private var now = 20454L * 86_400_000 + 9 * 3_600_000
    private var state = GuidanceState()

    private fun send(e: GuidanceEvent) { state = engine.reduce(state, e, config) }
    private fun runFor(ms: Long) {
        val end = now + ms
        while (now < end) {
            now = minOf(now + 1_000, end)
            if (engine.needsTick(state, config)) send(GuidanceEvent.Tick(now))
        }
    }

    @Test
    fun `without an unlock signal the engine times nothing, which is why the service must provide one`() {
        send(GuidanceEvent.ForegroundChanged(ig, now))
        runFor(10_000)
        send(GuidanceEvent.ScreenChanged(false, now)) // phone locked
        runFor(60_000)

        // Unlock on Android 17: SCREEN_ON on the lock screen (ignored), no USER_PRESENT, then IG.
        send(GuidanceEvent.ForegroundChanged(ig, now))
        runFor(120_000)
        assertEquals("only the first 10 s were timed", 10_000L, engine.dailyUsedMs(state, now))

        // The fix: the first window event after unlocking reports the screen in use.
        if (ScreenPolicy.unlockNotYetSeen(state.screenOn, interactive = true, keyguardLocked = false)) {
            send(GuidanceEvent.ScreenChanged(true, now))
        }
        runFor(120_000)
        assertEquals(130_000L, engine.dailyUsedMs(state, now))
    }
}
