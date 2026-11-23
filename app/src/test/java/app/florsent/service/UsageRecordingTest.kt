package app.florsent.service

import app.florsent.data.UsageLogStore
import app.florsent.domain.UsageRecord
import java.io.File
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class)
class UsageRecordingTest {

    @get:Rule val tmp = TemporaryFolder()
    private val off = 8 * 3600
    private val t0 = 1_795_000_000_000L

    private fun TestScope.store(dir: File = tmp.root) = UsageLogStore(dir, StandardTestDispatcher(testScheduler))

    private suspend fun UsageLogStore.all() = read(0, Long.MAX_VALUE / 2000)

    @Test
    fun `records spans from state changes and ends cleanly`() = runTest {
        val store = store()
        val rec = UsageRecording(store, this) { off }
        rec.submit(UsageRecording.Input(true, screenInUse = true, watchedApp = null), t0)
        rec.submit(UsageRecording.Input(true, true, "com.instagram.android"), t0 + 60_000)
        rec.submit(UsageRecording.Input(true, false, null), t0 + 180_000)
        rec.submit(UsageRecording.Input(false, false, null), t0 + 200_000)
        advanceUntilIdle()

        val records = store.all()
        assertEquals(UsageRecord.Begin(t0 / 1000, off), records.first())
        assertTrue(records.contains(UsageRecord.App((t0 + 60_000) / 1000, 120, off, "com.instagram.android")))
        assertTrue(records.contains(UsageRecord.Screen(t0 / 1000, 180, off)))
        assertEquals(UsageRecord.End((t0 + 200_000) / 1000, off), records.last())
    }

    @Test
    fun `a new process after a kill writes an unknown interval before anything else`() = runTest {
        val store = store()
        val first = UsageRecording(store, this) { off }
        first.submit(UsageRecording.Input(true, true, null), t0)
        advanceUntilIdle()
        // Process dies here: no stop, open state still says "running, screen on since t0".

        val second = UsageRecording(store(), this) { off }
        second.submit(UsageRecording.Input(true, true, null), t0 + 3_600_000)
        advanceUntilIdle()

        val records = store.all()
        assertTrue(records.contains(UsageRecord.Unknown(t0 / 1000, 3_600, off)))
        assertEquals(2, records.count { it is UsageRecord.Begin })
        assertTrue("no fake screen span across the dead time", records.none { it is UsageRecord.Screen })
    }

    @Test
    fun `a restart does not write a stop and start pair while the service connects`() = runTest {
        val store = store()
        UsageRecording(store, this) { off }.apply { submit(UsageRecording.Input(true, false, null), t0) }
        advanceUntilIdle()

        // New process: "not recording" for a moment until the service connects.
        val second = UsageRecording(store(), this) { off }
        second.submit(UsageRecording.Input(false, false, null), t0 + 60_000)
        second.submit(UsageRecording.Input(true, false, null), t0 + 60_800)
        advanceUntilIdle()

        val records = store.all()
        assertTrue("no End at all", records.none { it is UsageRecord.End })
        assertEquals(2, records.count { it is UsageRecord.Begin })
        assertTrue(records.contains(UsageRecord.Unknown(t0 / 1000, 60, off)))
    }

    @Test
    fun `a restart without the service closes the old stretch after the grace period`() = runTest {
        val store = store()
        UsageRecording(store, this) { off }.apply { submit(UsageRecording.Input(true, false, null), t0) }
        advanceUntilIdle()

        val second = UsageRecording(store(), this) { off }
        second.submit(UsageRecording.Input(false, false, null), t0 + 60_000)
        advanceUntilIdle()

        val records = store.all()
        assertEquals(1, records.count { it is UsageRecord.Begin })
        assertEquals(
            listOf(UsageRecord.Unknown(t0 / 1000, 70, off), UsageRecord.End((t0 + 70_000) / 1000, off)),
            records.takeLast(2),
        )
    }

    @Test
    fun `without consent nothing is written`() = runTest {
        val store = store()
        val rec = UsageRecording(store, this) { off }
        rec.submit(UsageRecording.Input(false, true, "a.b"), t0)
        rec.submit(UsageRecording.Input(false, false, null), t0 + 60_000)
        advanceUntilIdle()
        assertTrue(store.all().isEmpty())
    }

    @Test
    fun `clearing history starts a fresh stretch`() = runTest {
        val store = store()
        val rec = UsageRecording(store, this) { off }
        rec.submit(UsageRecording.Input(true, true, null), t0)
        rec.submit(UsageRecording.Input(true, false, null), t0 + 60_000)
        advanceUntilIdle()
        rec.clearHistory(t0 + 120_000, UsageRecording.Input(true, false, null))
        advanceUntilIdle()
        assertEquals(listOf<UsageRecord>(UsageRecord.Begin((t0 + 120_000) / 1000, off)), store.all())
    }

    @Test
    fun `store skips damaged lines and prunes old days`() = runTest {
        val store = store()
        store.append(listOf(UsageRecord.Screen(86_400L * 100, 60, 0), UsageRecord.Screen(86_400L * 140, 60, 0)))
        File(tmp.root, "1970-05-21.tsv").appendText("S\tbroken\n")
        assertEquals(2, store.all().size)
        store.prune(nowSec = 86_400L * 141, keepDays = 30)
        assertEquals(listOf<UsageRecord>(UsageRecord.Screen(86_400L * 140, 60, 0)), store.all())
    }
}
