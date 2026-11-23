package app.florsent.domain

import app.florsent.service.effect.DirtyPackageStore
import app.florsent.service.effect.SaturationBackend
import app.florsent.service.effect.SaturationSession
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SaturationSessionTest {

    private class FakeBackend : SaturationBackend {
        var ready = true
        var failNext = 0
        var failAll = false
        val levels = mutableMapOf<String, Int>() // what the "system" currently shows
        val calls = mutableListOf<Pair<String, Int>>()

        override fun isReady() = ready
        override fun setLayerSaturation(callerPackage: String, targetPackage: String, saturation: Int): Boolean {
            calls += targetPackage to saturation
            if (failAll) return false
            if (failNext > 0) { failNext--; return false }
            if (saturation >= 100) levels.remove(targetPackage) else levels[targetPackage] = saturation
            return true
        }
        fun grayPackages() = levels.keys
    }

    private class FakeDirty(initial: Set<String> = emptySet()) : DirtyPackageStore {
        val set = initial.toMutableSet()
        override fun all() = set.toSet()
        override fun add(packageName: String) { set += packageName }
        override fun remove(packageName: String) { set -= packageName }
    }

    private val ig = "com.instagram.android"
    private val yt = "com.google.android.youtube"
    private val self = "app.florsent"

    private fun session(backend: FakeBackend, dirty: FakeDirty = FakeDirty()) =
        SaturationSession(backend, dirty, self)

    @Test
    fun `fades the target from 100 towards 0`() {
        val b = FakeBackend()
        val s = session(b)
        s.render(ig, 0.5f, enabled = true)
        assertEquals(50, b.levels[ig])
        s.render(ig, 1f, enabled = true)
        assertEquals(0, b.levels[ig])
    }

    @Test
    fun `leaving the app, finishing breathing or switching off restores 100`() {
        val b = FakeBackend()
        val s = session(b)
        s.render(ig, 0.9f, enabled = true)
        s.render(null, 0f, enabled = true) // effect None: left the app / breathing / cooldown
        assertTrue(b.grayPackages().isEmpty())

        s.render(ig, 0.9f, enabled = true)
        s.render(ig, 0.9f, enabled = false) // toggle switched off
        assertTrue(b.grayPackages().isEmpty())
    }

    @Test
    fun `switching target restores the previous app before graying the next`() {
        val b = FakeBackend()
        val s = session(b)
        s.render(ig, 0.6f, enabled = true)
        b.calls.clear()
        s.render(yt, 0.6f, enabled = true)
        assertEquals(listOf(ig to 100, yt to 40), b.calls)
        assertEquals(setOf(yt), b.grayPackages())
    }

    @Test
    fun `identical renders do not spawn shell calls`() {
        val b = FakeBackend()
        val s = session(b)
        s.render(ig, 0.5f, enabled = true)
        val before = b.calls.size
        repeat(10) { s.render(ig, 0.5f, enabled = true) }
        assertEquals(before, b.calls.size)
    }

    @Test
    fun `never desaturates florsent itself`() {
        val b = FakeBackend()
        session(b).render(self, 1f, enabled = true)
        assertTrue(b.calls.isEmpty())
    }

    @Test
    fun `a package is marked dirty before it is grayed and cleared after restore`() {
        val b = FakeBackend()
        val dirty = FakeDirty()
        val s = session(b, dirty)
        s.render(ig, 0.7f, enabled = true)
        assertEquals(setOf(ig), dirty.set)
        s.render(null, 0f, enabled = true)
        assertTrue(dirty.set.isEmpty())
    }

    @Test
    fun `after a crash the next session restores leftovers on recover`() {
        val b = FakeBackend().apply { levels[ig] = 0 } // the system still shows Instagram gray
        val dirty = FakeDirty(setOf(ig))
        val s = session(b, dirty) // fresh process: nothing in memory
        assertTrue(s.recover())
        assertTrue(b.grayPackages().isEmpty())
        assertTrue(dirty.set.isEmpty())
    }

    @Test
    fun `when the backend is down nothing is lost and recover cleans up later`() {
        val b = FakeBackend()
        val dirty = FakeDirty()
        val s = session(b, dirty)
        s.render(ig, 0.8f, enabled = true)
        b.ready = false // Shizuku stopped mid-fade
        s.render(null, 0f, enabled = true)
        assertEquals("cannot restore yet, still tracked", setOf(ig), dirty.set)
        assertFalse(s.recover())

        b.ready = true // Shizuku is back
        assertTrue(s.recover())
        assertTrue(b.grayPackages().isEmpty())
        assertTrue(dirty.set.isEmpty())
    }

    @Test
    fun `a failure degrades the layer and restores what it touched`() {
        val b = FakeBackend()
        val s = session(b)
        s.render(ig, 0.5f, enabled = true)
        b.failNext = 1
        s.render(ig, 0.8f, enabled = true) // this call fails
        assertTrue(s.degraded)
        assertTrue("restored after the failure", b.grayPackages().isEmpty())

        s.render(ig, 1f, enabled = true)
        assertTrue("stays off while degraded", b.grayPackages().isEmpty())

        assertTrue(s.recover())
        assertFalse(s.degraded)
        s.render(ig, 1f, enabled = true)
        assertEquals(0, b.levels[ig])
    }

    @Test
    fun `a failed restore keeps the package dirty for the next recover`() {
        val b = FakeBackend()
        val dirty = FakeDirty()
        val s = session(b, dirty)
        s.render(ig, 1f, enabled = true)
        b.failAll = true
        s.render(null, 0f, enabled = true)
        assertEquals(setOf(ig), dirty.set)
        assertTrue(s.pendingRestores.contains(ig))

        b.failAll = false
        assertTrue(s.recover())
        assertTrue(dirty.set.isEmpty())
        assertTrue(b.grayPackages().isEmpty())
    }
}
