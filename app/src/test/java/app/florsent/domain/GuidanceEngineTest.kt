package app.florsent.domain

import app.florsent.domain.GuidanceEvent.BreathingCancelled
import app.florsent.domain.GuidanceEvent.BreathingCompleted
import app.florsent.domain.GuidanceEvent.ForegroundChanged
import app.florsent.domain.GuidanceEvent.NudgeAccepted
import app.florsent.domain.GuidanceEvent.NudgeDismissed
import app.florsent.domain.GuidanceEvent.PreviewStarted
import app.florsent.domain.GuidanceEvent.ScreenChanged
import app.florsent.domain.GuidanceEvent.Tick
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GuidanceEngineTest {

    private val social = "com.instagram.android"
    private val other = "com.android.chrome"
    private val config = GuidanceConfig(
        watchedPackages = setOf(social),
        sessionBudgetMs = 10 * MIN,
        dailyBudgetMs = 60 * MIN,
        fadeDurationMs = 2 * MIN,
        nudgeAtProgress = 0.5f,
        nudgeSnoozeMs = 1 * MIN,
        cooldownMs = 5 * MIN,
        sessionGapMs = 3 * MIN,
        breathingTimeoutMs = 10 * MIN,
    )

    /** Drives the engine like the runtime does: events plus one Tick per second. */
    private inner class Harness(startMs: Long = T0, cfg: GuidanceConfig = config) {
        val engine = GuidanceEngine(ZoneOffset.UTC)
        var config = cfg
        var now = startMs
        var state = GuidanceState()

        fun send(event: (Long) -> GuidanceEvent) {
            state = engine.reduce(state, event(now), config)
        }

        fun open(pkg: String?) = send { ForegroundChanged(pkg, it) }

        fun advance(ms: Long) {
            val end = now + ms
            while (now < end) {
                now = minOf(now + 1_000, end)
                if (engine.needsTick(state, config)) send { Tick(it) }
            }
        }

        val effect get() = engine.effect(state, config, now)
        val status get() = engine.status(state, config, now)
    }

    @Test
    fun `no guidance while under the session budget`() {
        val h = Harness()
        h.open(social)
        h.advance(9 * MIN)

        assertEquals(9 * MIN, h.state.sessionUsedMs)
        assertEquals(0L, h.state.overBudgetMs)
        assertEquals(GuidanceEffect.None, h.effect)
        assertEquals(GuidanceStatus.Idle, h.status)
    }

    @Test
    fun `fade ramps smoothly once the session budget is exceeded`() {
        val h = Harness()
        h.open(social)
        h.advance(10 * MIN)
        assertEquals(0f, h.effect.level, 0f)

        val levels = (1..120).map {
            h.advance(1_000)
            h.effect.level
        }
        assertTrue("level must never go down while over budget", levels.zipWithNext().all { (a, b) -> b >= a })
        assertTrue("first second is barely visible", levels.first() < 0.01f)
        assertEquals(1f, levels.last(), 0.0001f)
        assertEquals(social, h.effect.targetPackage)
        assertEquals(GuidanceStatus.Guiding, h.status)
    }

    @Test
    fun `time in non-watched apps is not counted and shows no effect`() {
        val h = Harness()
        h.open(social)
        h.advance(11 * MIN)
        assertTrue(h.effect.level > 0f)

        h.open(other)
        h.advance(2 * MIN)

        assertEquals(11 * MIN, h.state.dailyUsedMs)
        assertEquals(GuidanceEffect.None, h.effect)
        assertFalse(h.engine.needsTick(h.state, h.config))
    }

    @Test
    fun `short break resumes the session but a long break starts a new one`() {
        val h = Harness()
        h.open(social)
        h.advance(11 * MIN)
        val over = h.state.overBudgetMs

        h.open(other)
        h.advance(2 * MIN)
        h.open(social)
        assertEquals("under the 3 min gap: progress kept", over, h.state.overBudgetMs)
        assertTrue(h.effect.level > 0f)

        h.open(other)
        h.advance(4 * MIN)
        h.open(social)
        assertEquals(0L, h.state.sessionUsedMs)
        assertEquals(0L, h.state.overBudgetMs)
        assertEquals(GuidanceEffect.None, h.effect)
    }

    @Test
    fun `nudge appears halfway through the fade and can be snoozed`() {
        val h = Harness()
        h.open(social)
        h.advance(10 * MIN + 59_000)
        assertFalse(h.effect.showNudge)

        h.advance(1_000)
        assertTrue(h.effect.showNudge)

        h.send { NudgeDismissed(it) }
        assertFalse(h.effect.showNudge)
        assertTrue("fade keeps going while snoozed", h.effect.level > 0f)

        h.advance(1 * MIN)
        assertTrue(h.effect.showNudge)
    }

    @Test
    fun `accepting then completing breathing starts a cooldown that later resets the session`() {
        val h = Harness()
        h.open(social)
        h.advance(12 * MIN)

        h.send { NudgeAccepted(it) }
        h.open("app.florsent")
        assertEquals(GuidanceMode.Breathing, h.state.mode)
        assertEquals(GuidanceEffect.None, h.effect)

        h.advance(1 * MIN)
        h.send { BreathingCompleted(it) }
        assertEquals(GuidanceMode.Cooldown, h.state.mode)
        assertEquals(GuidanceStatus.Cooldown(h.now + 5 * MIN), h.status)

        // Back on social during cooldown: counted toward the day, but no guidance.
        h.open(social)
        h.advance(4 * MIN)
        assertEquals(GuidanceEffect.None, h.effect)
        assertEquals(16 * MIN, h.state.dailyUsedMs)

        h.advance(1 * MIN + 1_000)
        assertEquals(GuidanceMode.Tracking, h.state.mode)
        assertTrue("new session starts from zero", h.state.sessionUsedMs <= 1_000)
        assertEquals(GuidanceEffect.None, h.effect)
    }

    @Test
    fun `cancelling breathing brings the fade back where it was`() {
        val h = Harness()
        h.open(social)
        h.advance(11 * MIN)
        val level = h.effect.level

        h.send { NudgeAccepted(it) }
        h.open("app.florsent")
        h.send { BreathingCancelled(it) }
        h.open(social)

        assertEquals(GuidanceMode.Tracking, h.state.mode)
        assertEquals(level, h.effect.level, 0.0001f)
    }

    @Test
    fun `returning to a watched app abandons an unfinished breathing session`() {
        val h = Harness()
        h.open(social)
        h.advance(11 * MIN)
        h.send { NudgeAccepted(it) }

        h.open(social)

        assertEquals(GuidanceMode.Tracking, h.state.mode)
        assertTrue(h.effect.level > 0f)
    }

    @Test
    fun `breathing left open too long times out`() {
        val h = Harness()
        h.open(social)
        h.advance(11 * MIN)
        h.send { NudgeAccepted(it) }
        h.open("app.florsent")

        h.advance(10 * MIN)
        h.send { Tick(it) }

        assertEquals(GuidanceMode.Tracking, h.state.mode)
    }

    @Test
    fun `after the daily budget is used up, a new session gets a two minute grace period`() {
        val h = Harness(cfg = config.copy(sessionBudgetMs = 30 * MIN, dailyBudgetMs = 20 * MIN))
        h.open(social)
        h.advance(20 * MIN)
        h.open(other)
        h.advance(5 * MIN) // longer than the session gap: new session

        h.open(social)
        h.advance(2 * MIN)
        assertEquals("no fade during the grace period", 0L, h.state.overBudgetMs)
        assertEquals(GuidanceEffect.None, h.effect)

        h.advance(30_000)
        assertEquals(30_000L, h.state.overBudgetMs)
        assertTrue(h.effect.level > 0f)
        assertTrue("still sooner than the session budget", h.state.sessionUsedMs < h.config.sessionBudgetMs)
    }

    @Test
    fun `grace period also applies after a cooldown once the daily budget is gone`() {
        val h = Harness(cfg = config.copy(sessionBudgetMs = 10 * MIN, dailyBudgetMs = 10 * MIN))
        h.open(social)
        h.advance(11 * MIN)
        h.send { NudgeAccepted(it) }
        h.open("app.florsent")
        h.send { BreathingCompleted(it) }
        h.advance(5 * MIN) // cooldown ends

        h.open(social)
        h.advance(1 * MIN + 59_000)
        assertEquals(GuidanceEffect.None, h.effect)

        h.advance(2_000)
        assertTrue(h.effect.level > 0f)
    }

    @Test
    fun `crossing the daily budget mid-session starts the fade without an extra wait`() {
        val h = Harness(cfg = config.copy(sessionBudgetMs = 30 * MIN, dailyBudgetMs = 10 * MIN))
        h.open(social)
        h.advance(10 * MIN)
        assertEquals(0L, h.state.overBudgetMs)

        h.advance(30_000)

        assertEquals("already well past the grace period", 30_000L, h.state.overBudgetMs)
    }

    @Test
    fun `crossing the daily budget early in a session still waits out the grace period`() {
        val h = Harness(cfg = config.copy(sessionBudgetMs = 30 * MIN, dailyBudgetMs = 10 * MIN))
        h.open(social)
        h.advance(9 * MIN + 30_000)
        h.open(other)
        h.advance(4 * MIN) // new session
        h.open(social)
        h.advance(1 * MIN) // daily crossed 30 s into this session

        assertEquals(0L, h.state.overBudgetMs)
        h.advance(1 * MIN + 10_000)
        assertEquals(10_000L, h.state.overBudgetMs)
    }

    @Test
    fun `daily usage resets at local midnight`() {
        val beforeMidnight = DAY - 2 * MIN
        val h = Harness(startMs = beforeMidnight)
        h.open(social)
        h.advance(1 * MIN)
        assertEquals(1 * MIN, h.state.dailyUsedMs)

        h.advance(2 * MIN)

        assertEquals(1 * MIN, h.state.dailyUsedMs)
        assertEquals(1 * MIN, h.engine.dailyUsedMs(h.state, h.now))
    }

    @Test
    fun `screen off pauses counting and large gaps are capped`() {
        val h = Harness()
        h.open(social)
        h.advance(1 * MIN)

        h.send { ScreenChanged(false, it) }
        h.now += 2 * MIN // shorter than the session gap
        h.send { ScreenChanged(true, it) }
        assertEquals(1 * MIN, h.state.sessionUsedMs)

        // A missed tick (process frozen for a minute) only counts up to maxTickGapMs.
        h.now += 1 * MIN
        h.send { Tick(it) }
        assertEquals(1 * MIN + h.config.maxTickGapMs, h.state.sessionUsedMs)
    }

    @Test
    fun `preview fades over twenty seconds, shows the nudge and ends on its own`() {
        val h = Harness()
        h.open(other)
        h.send { PreviewStarted(it) }
        assertEquals(GuidanceStatus.Preview, h.status)

        h.advance(10_000)
        assertTrue(h.effect.isPreview)
        assertFalse(h.effect.showNudge)
        assertEquals(0.5f, h.effect.level, 0.01f)

        h.advance(10_000)
        assertTrue(h.effect.showNudge)
        assertEquals(1f, h.effect.level, 0.0001f)

        h.advance(10_000)
        assertEquals(GuidanceEffect.None, h.effect)
        assertEquals(null, h.state.previewStartedAtMs)
        assertEquals("preview does not touch real usage", 0L, h.state.dailyUsedMs)
    }

    @Test
    fun `breathing from the preview does not trigger a cooldown`() {
        val h = Harness()
        h.send { PreviewStarted(it) }
        h.advance(15_000)
        h.send { NudgeAccepted(it) }
        assertEquals(GuidanceMode.Breathing, h.state.mode)

        h.send { BreathingCompleted(it) }

        assertEquals(GuidanceMode.Tracking, h.state.mode)
        assertEquals(GuidanceStatus.Idle, h.status)
    }

    @Test
    fun `dismissing the preview nudge ends the preview`() {
        val h = Harness()
        h.send { PreviewStarted(it) }
        h.advance(13_000)

        h.send { NudgeDismissed(it) }

        assertEquals(GuidanceEffect.None, h.effect)
    }

    @Test
    fun `breathing started from home while over budget also earns a cooldown`() {
        val h = Harness()
        h.open(social)
        h.advance(11 * MIN)
        h.open("app.florsent")

        h.send { BreathingCompleted(it) }

        assertEquals(GuidanceMode.Cooldown, h.state.mode)
    }

    @Test
    fun `breathing while under budget changes nothing`() {
        val h = Harness()
        h.open(social)
        h.advance(2 * MIN)
        val before = h.state

        h.send { BreathingCompleted(it) }

        assertEquals(before.mode, h.state.mode)
        assertEquals(before.sessionUsedMs, h.state.sessionUsedMs)
    }

    @Test
    fun `smoothstep is clamped and symmetric`() {
        assertEquals(0f, Easing.smoothstep(-1f), 0f)
        assertEquals(1f, Easing.smoothstep(2f), 0f)
        assertEquals(0.5f, Easing.smoothstep(0.5f), 0.0001f)
        assertEquals(1f - Easing.smoothstep(0.2f), Easing.smoothstep(0.8f), 0.0001f)
    }

    private companion object {
        const val MIN = 60_000L
        const val DAY = 86_400_000L
        /** 2026-01-01T09:00Z, well away from midnight. */
        const val T0 = 20_454L * DAY + 9 * 60 * MIN
    }
}
