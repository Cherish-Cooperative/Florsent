package app.florsent.service

import app.florsent.data.GuidanceStatePersistence
import app.florsent.domain.GuidanceConfig
import app.florsent.domain.GuidanceEngine
import app.florsent.domain.GuidanceEvent
import app.florsent.domain.GuidanceState
import java.time.ZoneOffset
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The coordinator on virtual time: real engine, real tick scheduling, fake persistence. Verifies
 * the battery and storage promises (no polling, throttled writes) as well as restore-on-start.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class GuidanceCoordinatorTest {

    private val social = "com.instagram.android"
    private val config = GuidanceConfig(
        watchedPackages = setOf(social),
        sessionBudgetMs = 5 * MIN,
        dailyBudgetMs = 60 * MIN,
        fadeDurationMs = 2 * MIN,
    )

    private class FakePersistence(var stored: GuidanceState = GuidanceState()) : GuidanceStatePersistence {
        val saves = mutableListOf<GuidanceState>()
        override suspend fun load() = stored
        override suspend fun save(state: GuidanceState) {
            saves += state
            stored = state
        }
    }

    private fun TestScope.coordinator(persistence: FakePersistence = FakePersistence()) = GuidanceCoordinator(
        scope = backgroundScope,
        engine = GuidanceEngine { ZoneOffset.UTC },
        configFlow = MutableStateFlow(config),
        store = persistence,
        clock = { T0 + testScheduler.currentTime },
    )

    private fun TestScope.countStateChanges(c: GuidanceCoordinator): () -> Int {
        var count = 0
        backgroundScope.launch { c.state.collect { count++ } }
        runCurrent()
        val base = count
        return { count - base }
    }

    @Test
    fun `restores persisted usage and replays events sent before loading`() = runTest {
        val persisted = GuidanceState(epochDay = DAY_INDEX, dailyUsedMs = 7 * MIN)
        val c = coordinator(FakePersistence(persisted))
        c.dispatch(GuidanceEvent.ForegroundChanged(social, c.now())) // before the load completes
        runCurrent()

        assertEquals(7 * MIN, c.state.value.dailyUsedMs)
        assertEquals(social, c.state.value.foregroundPackage)
    }

    @Test
    fun `under budget it sleeps in long intervals instead of ticking every second`() = runTest {
        val c = coordinator()
        runCurrent()
        val changes = countStateChanges(c)
        c.dispatch(GuidanceEvent.ForegroundChanged(social, c.now()))
        advanceTimeBy(4 * MIN)
        runCurrent()

        assertTrue("about 4 min / 15 s, got ${changes()}", changes() in 10..25)
        val used = c.state.value.sessionUsedMs
        assertTrue("usage still counted exactly up to the last tick, got $used", used in (4 * MIN - 15_000)..(4 * MIN))
    }

    @Test
    fun `while fading it ticks every second and reaches full guidance with the nudge`() = runTest {
        val c = coordinator()
        runCurrent()
        c.dispatch(GuidanceEvent.ForegroundChanged(social, c.now()))
        advanceTimeBy(5 * MIN + 10_000)
        runCurrent()
        val changes = countStateChanges(c)
        advanceTimeBy(30_000)
        runCurrent()
        assertTrue("about 30 ticks in 30 s, got ${changes()}", changes() in 25..35)
        assertTrue(c.effect.value.level > 0f)

        advanceTimeBy(2 * MIN)
        runCurrent()
        assertEquals(1f, c.effect.value.level, 0.0001f)
        assertTrue(c.effect.value.showNudge)
        assertEquals(social, c.effect.value.targetPackage)
    }

    @Test
    fun `with the screen off nothing runs at all`() = runTest {
        val persistence = FakePersistence()
        val c = coordinator(persistence)
        runCurrent()
        c.dispatch(GuidanceEvent.ForegroundChanged(social, c.now()))
        advanceTimeBy(1 * MIN)
        c.dispatch(GuidanceEvent.ScreenChanged(false, c.now()))
        runCurrent()
        val state = c.state.value
        val saves = persistence.saves.size
        val changes = countStateChanges(c)

        advanceTimeBy(60 * MIN)
        runCurrent()

        assertEquals(0, changes())
        assertEquals(state, c.state.value)
        assertEquals("no writes while idle", saves, persistence.saves.size)
    }

    @Test
    fun `writes are throttled while counting but immediate when counting stops`() = runTest {
        val persistence = FakePersistence()
        val c = coordinator(persistence)
        runCurrent()
        c.dispatch(GuidanceEvent.ForegroundChanged(social, c.now()))
        advanceTimeBy(10 * MIN)
        runCurrent()
        assertTrue("at most one routine write per 30 s, got ${persistence.saves.size}", persistence.saves.size <= 22)

        c.dispatch(GuidanceEvent.ScreenChanged(false, c.now()))
        runCurrent()
        assertEquals("screen-off is saved right away", c.state.value.dailyUsedMs, persistence.saves.last().dailyUsedMs)
        assertEquals("transient fields are not persisted", null, persistence.saves.last().foregroundPackage)
    }

    @Test
    fun `mode changes are saved immediately`() = runTest {
        val persistence = FakePersistence()
        val c = coordinator(persistence)
        runCurrent()
        c.dispatch(GuidanceEvent.ForegroundChanged(social, c.now()))
        advanceTimeBy(6 * MIN)
        c.dispatch(GuidanceEvent.NudgeAccepted(c.now()))
        runCurrent()
        assertEquals(app.florsent.domain.GuidanceMode.Breathing, persistence.saves.last().mode)
    }

    @Test
    fun `service running flag`() = runTest {
        val c = coordinator()
        assertFalse(c.serviceRunning.value)
        c.setServiceRunning(true)
        assertTrue(c.serviceRunning.value)
    }

    private companion object {
        const val MIN = 60_000L
        const val DAY_INDEX = 20454L
        const val T0 = DAY_INDEX * 86_400_000L + 9 * 60 * MIN
    }
}
