package app.florsent.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UsageLogTest {

    private val off = 8 * 3600

    @Test
    fun `codec round-trips every record and rejects damaged lines`() {
        val records = listOf(
            UsageRecord.App(100, 60, off, "com.instagram.android"),
            UsageRecord.Screen(200, 300, off),
            UsageRecord.Begin(1, -5 * 3600),
            UsageRecord.End(2, 0),
            UsageRecord.Unknown(3, 4, off),
        )
        records.forEach { assertEquals(it, UsageCodec.decode(UsageCodec.encode(it))) }
        listOf("", "A\t1\t2", "S\tx\t1\t0", "A\t1\t2\t0\t", "Z\t1\t2\t0", "S\t1\t-5\t0", "S\t1\t2\t999999").forEach {
            assertNull("should reject '$it'", UsageCodec.decode(it))
        }
    }

    @Test
    fun `screen and app spans are recorded when they end`() {
        var open = UsageRecorder.start(OpenSpans(), 0, off).open
        open = UsageRecorder.update(open, screenInUse = true, watchedApp = null, nowMs = 1_000, offsetSec = off).open
        open = UsageRecorder.update(open, true, "com.instagram.android", 61_000, off).open
        val switched = UsageRecorder.update(open, true, "com.google.android.youtube", 121_000, off)
        assertEquals(listOf(UsageRecord.App(61, 60, off, "com.instagram.android")), switched.records)
        val off2 = UsageRecorder.update(switched.open, false, null, 181_000, off)
        assertEquals(
            listOf(UsageRecord.App(121, 60, off, "com.google.android.youtube"), UsageRecord.Screen(1, 180, off)),
            off2.records,
        )
    }

    @Test
    fun `a clean stop closes spans and ends the stretch`() {
        var open = UsageRecorder.start(OpenSpans(), 0, off).open
        open = UsageRecorder.update(open, true, "a.b", 10_000, off).open
        val stop = UsageRecorder.stop(open, 70_000, off)
        assertEquals(
            listOf(UsageRecord.App(10, 60, off, "a.b"), UsageRecord.Screen(10, 60, off), UsageRecord.End(70, off)),
            stop.records,
        )
        assertTrue(!stop.open.running)
    }

    @Test
    fun `a process killed while the screen was on leaves an unknown interval, never a gap`() {
        val left = OpenSpans(running = true, screenStartMs = 3_600_000, lastEventMs = 3_700_000)
        val start = UsageRecorder.start(left, 7_200_000, off)
        assertEquals(
            listOf(UsageRecord.Unknown(3_600, 3_600, off), UsageRecord.Begin(7_200, off)),
            start.records,
        )
    }

    @Test
    fun `a process killed with the screen off is unknown from the last thing we saw`() {
        val left = OpenSpans(running = true, lastEventMs = 1_000_000)
        val start = UsageRecorder.start(left, 2_000_000, off)
        assertEquals(UsageRecord.Unknown(1_000, 1_000, off), start.records.first())
    }

    @Test
    fun `flickers shorter than a second are not recorded`() {
        var open = UsageRecorder.start(OpenSpans(), 0, off).open
        open = UsageRecorder.update(open, true, null, 1_000, off).open
        assertTrue(UsageRecorder.update(open, false, null, 1_500, off).records.isEmpty())
    }

    @Test
    fun `nothing is recorded while not running`() {
        val step = UsageRecorder.update(OpenSpans(), true, "a.b", 1_000, off)
        assertTrue(step.records.isEmpty())
    }
}
