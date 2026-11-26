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
        val engine = GuidanceEngine { ZoneOffset.UTC }
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
        assertEquals("but the screen starts clear", 0f, h.effect.level, 0f)
        h.advance(8_000)
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
        assertEquals("builds up again from a clear screen", 0f, h.effect.level, 0f)
        h.advance(8_000)
        assertTrue("back where it was (plus the 8 s of use)", h.effect.level >= level)
    }

    @Test
    fun `reopening a watched app builds the fade up from nothing over eight seconds, then the nudge`() {
        val h = Harness()
        h.open(social)
        h.advance(11 * MIN + 30_000) // well past the nudge point
        val before = h.effect
        assertTrue(before.showNudge)

        h.open(other)
        h.advance(20_000)
        assertEquals(GuidanceEffect.None, h.effect)

        h.open(social)
        val levels = (0..8).map { second ->
            if (second > 0) h.advance(1_000)
            h.effect
        }
        assertEquals("clear at first", 0f, levels.first().level, 0f)
        levels.zipWithNext { a, b -> assertTrue("only rises", b.level > a.level) }
        assertTrue("no jump: under a third after two seconds", levels[2].level < before.level / 3f)
        assertTrue("at least the old level after eight", levels[8].level >= before.level)
        assertFalse("the nudge waits for the build-up", levels[7].showNudge)
        assertTrue(levels[8].showNudge)
        assertEquals("ticks every second while building up", 1_000L, h.engine.nextTickDelayMs(h.state, h.config, h.now))
    }

    @Test
    fun `switching between two watched apps does not restart the build-up`() {
        val second = "com.facebook.katana"
        val h = Harness(cfg = config.copy(watchedPackages = setOf(social, second)))
        h.open(social)
        h.advance(11 * MIN)
        val level = h.effect.level
        h.open(second)
        assertTrue(h.effect.level >= level)
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
    fun `ticks are fast only while the fade moves, otherwise they wait for the next deadline`() {
        val h = Harness()
        assertEquals("nothing to do with no watched app in front", null, h.engine.nextTickDelayMs(h.state, h.config, h.now))

        h.open(social)
        assertEquals("under budget: sleep, capped", h.config.maxTickIntervalMs, h.engine.nextTickDelayMs(h.state, h.config, h.now))
        repeat(39) { // the real cadence: 39 x 15 s = 9:45
            h.now += h.config.maxTickIntervalMs
            h.send { Tick(it) }
        }
        h.now += 10_000
        h.send { Tick(it) } // 9:55 used
        assertEquals("wake exactly when the budget runs out", 5_000L, h.engine.nextTickDelayMs(h.state, h.config, h.now))

        h.advance(10_000) // now fading
        assertEquals(1_000L, h.engine.nextTickDelayMs(h.state, h.config, h.now))

        h.advance(3 * MIN) // fully faded
        assertEquals(h.config.maxTickIntervalMs, h.engine.nextTickDelayMs(h.state, h.config, h.now))

        h.send { NudgeDismissed(it) }
        assertEquals("wake when the snooze ends", h.config.maxTickIntervalMs, h.engine.nextTickDelayMs(h.state, h.config, h.now))
        h.now += 50_000
        h.send { Tick(it) }
        assertEquals(10_000L, h.engine.nextTickDelayMs(h.state, h.config, h.now))

        h.send { ScreenChanged(false, it) }
        assertEquals(null, h.engine.nextTickDelayMs(h.state, h.config, h.now))
    }

    @Test
    fun `slow ticks still count usage exactly`() {
        val h = Harness()
        h.open(social)
        repeat(36) { // 36 x 15 s = 9 min without 1 s ticks
            h.now += h.config.maxTickIntervalMs
            h.send { Tick(it) }
        }
        assertEquals(9 * MIN, h.state.sessionUsedMs)
        assertEquals(9 * MIN, h.state.dailyUsedMs)
    }

    @Test
    fun `moving the clock back cannot stretch a cooldown or a snooze`() {
        val h = Harness()
        h.open(social)
        h.advance(11 * MIN)
        h.send { NudgeAccepted(it) }
        h.open("app.florsent")
        h.send { BreathingCompleted(it) }
        assertEquals(GuidanceMode.Cooldown, h.state.mode)

        h.now -= DAY // user sets the clock back a day
        h.send { Tick(it) }
        assertTrue(h.state.cooldownUntilMs <= h.now + h.config.cooldownMs)

        h.now += h.config.cooldownMs
        h.send { Tick(it) }
        assertEquals(GuidanceMode.Tracking, h.state.mode)
    }

    @Test
    fun `a preview started in the future (clock moved back) is dropped`() {
        val h = Harness()
        h.send { PreviewStarted(it) }
        h.now -= 10 * MIN
        h.send { Tick(it) }
        assertEquals(null, h.state.previewStartedAtMs)
        assertFalse(h.engine.needsTick(h.state, h.config))
    }

    @Test
    fun `last watched time in the future is treated as a stale session`() {
        val h = Harness()
        h.open(social)
        h.advance(8 * MIN)
        h.open(other)
        h.now -= 1 * DAY
        h.open(social)
        assertEquals(0L, h.state.sessionUsedMs)
    }

    @Test
    fun `flying west across midnight does not hand out a second daily budget`() {
        var zone: java.time.ZoneId = ZoneOffset.ofHours(9)
        val engine = GuidanceEngine { zone }
        val cfg = config
        // 00:30 on day N+1 in UTC+9 is 15:30 on day N in UTC.
        var now = DAY + 30 * MIN - 9 * 60 * MIN
        var s = engine.reduce(GuidanceState(), ForegroundChanged(social, now), cfg)
        repeat(60) { now += 1_000; s = engine.reduce(s, Tick(now), cfg) }
        val usedBefore = s.dailyUsedMs

        zone = ZoneOffset.ofHours(-8) // land on the previous calendar day
        now += 1_000
        s = engine.reduce(s, Tick(now), cfg)

        assertTrue("usage kept across the backwards date change", s.dailyUsedMs >= usedBefore)
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
