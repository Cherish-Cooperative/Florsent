package app.florsent.data

import app.florsent.domain.GuidanceMode
import app.florsent.domain.GuidanceState
import app.florsent.testing.testPreferences
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class)
class GuidanceStateStoreTest {

    @get:Rule val tmp = TemporaryFolder()

    @Test
    fun `durable fields survive a restart, transient ones do not`() = runTest {
        val scope = TestScope(UnconfinedTestDispatcher(testScheduler) + Job())
        val saved = GuidanceState(
            foregroundPackage = "com.instagram.android",
            screenOn = false,
            epochDay = 20454,
            dailyUsedMs = 123_000,
            sessionUsedMs = 45_000,
            overBudgetMs = 6_000,
            lastWatchedAtMs = 999,
            lastTickAtMs = 1_000,
            mode = GuidanceMode.Cooldown,
            modeSinceMs = 500,
            cooldownUntilMs = 2_000,
            nudgeSnoozedUntilMs = 700,
            previewStartedAtMs = 800,
        )
        GuidanceStateStore(testPreferences(tmp.root, scope)).save(saved)

        // A fresh process reading what was written to disk (copied, since DataStore allows only one
        // active instance per file).
        java.io.File(tmp.root, "test.preferences_pb").copyTo(java.io.File(tmp.root, "restarted.preferences_pb"))
        val loaded = GuidanceStateStore(
            testPreferences(tmp.root, TestScope(UnconfinedTestDispatcher(testScheduler) + Job()), name = "restarted"),
        ).load()

        assertEquals(
            saved.copy(foregroundPackage = null, screenOn = true, lastTickAtMs = null, previewStartedAtMs = null),
            loaded,
        )
    }

    @Test
    fun `a missing last-watched time stays missing`() = runTest {
        val store = GuidanceStateStore(testPreferences(tmp.root, TestScope(UnconfinedTestDispatcher(testScheduler) + Job())))
        store.save(GuidanceState(lastWatchedAtMs = null))
        assertEquals(null, store.load().lastWatchedAtMs)
    }

    @Test
    fun `an empty store loads defaults`() = runTest {
        val store = GuidanceStateStore(testPreferences(tmp.root, TestScope(UnconfinedTestDispatcher(testScheduler) + Job()), name = "empty"))
        assertEquals(GuidanceState(), store.load())
    }
}
