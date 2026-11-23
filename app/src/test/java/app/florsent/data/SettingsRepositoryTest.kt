package app.florsent.data

import app.florsent.testing.testPreferences
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsRepositoryTest {

    @get:Rule val tmp = TemporaryFolder()

    private fun TestScope.repository() = SettingsRepository(
        testPreferences(tmp.root, TestScope(UnconfinedTestDispatcher(testScheduler) + Job())),
    )

    @Test
    fun `defaults are calm and private`() = runTest {
        val s = repository().settings.first()
        assertEquals(15, s.sessionBudgetMinutes)
        assertEquals(60, s.dailyBudgetMinutes)
        assertEquals(20, s.cooldownMinutes)
        assertFalse("sounds off by default", s.ambientSound || s.cueSound)
        assertFalse("advanced layers off by default", s.zenGrayscaleFinish || s.shizukuSaturation)
        assertFalse("brand colours by default", s.dynamicColor)
        assertTrue(s.returnToHomeScreen)
        assertFalse(s.onboardingCompleted)
    }

    @Test
    fun `the watch list is preselected once and never overwrites the user's choice`() = runTest {
        val repo = repository()
        repo.initializeWatchedIfNeeded(setOf("a", "b"))
        assertEquals(setOf("a", "b"), repo.settings.first().watchedPackages)

        repo.setWatched("a", false)
        repo.setWatched("c", true)
        repo.initializeWatchedIfNeeded(setOf("a", "b", "d"))

        assertEquals(setOf("b", "c"), repo.settings.first().watchedPackages)
    }

    @Test
    fun `editing the list before initialization also counts as initialized`() = runTest {
        val repo = repository()
        repo.setWatched("x", true)
        repo.initializeWatchedIfNeeded(setOf("a"))
        assertEquals(setOf("x"), repo.settings.first().watchedPackages)
    }

    @Test
    fun `every setting round-trips`() = runTest {
        val repo = repository()
        repo.setSessionBudgetMinutes(5)
        repo.setDailyBudgetMinutes(30)
        repo.setCooldownMinutes(10)
        repo.setAmbientSound(true)
        repo.setCueSound(true)
        repo.setReturnToHomeScreen(false)
        repo.setBreathingPattern("box_4444")
        repo.setDisclosureAccepted(true)
        repo.setOnboardingCompleted()
        repo.setZenGrayscaleFinish(true)
        repo.setShizukuSaturation(true)
        repo.setDynamicColor(true)

        val s = repo.settings.first()
        assertEquals(listOf(5, 30, 10), listOf(s.sessionBudgetMinutes, s.dailyBudgetMinutes, s.cooldownMinutes))
        assertTrue(s.ambientSound && s.cueSound && !s.returnToHomeScreen)
        assertEquals("box_4444", s.breathingPatternId)
        assertTrue(s.disclosureAccepted && s.onboardingCompleted)
        assertTrue(s.zenGrayscaleFinish && s.shizukuSaturation && s.dynamicColor)
    }

    @Test
    fun `settings convert to engine config in milliseconds`() = runTest {
        val repo = repository()
        repo.setWatched("a", true)
        repo.setSessionBudgetMinutes(5)
        repo.setDailyBudgetMinutes(45)
        repo.setCooldownMinutes(15)
        val config = repo.settings.first().toGuidanceConfig()
        assertEquals(setOf("a"), config.watchedPackages)
        assertEquals(5 * 60_000L, config.sessionBudgetMs)
        assertEquals(45 * 60_000L, config.dailyBudgetMs)
        assertEquals(15 * 60_000L, config.cooldownMs)
    }
}
