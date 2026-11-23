package app.florsent.service.effect

import org.junit.Assert.assertEquals
import org.junit.Test

class ZenGrayscaleControllerTest {

    private class FakeGateway : ZenRuleGateway {
        var access = true
        var throwOnActivate = false
        val calls = mutableListOf<String>()
        override fun hasAccess() = access
        override fun activate() {
            calls += "on"
            if (throwOnActivate) throw SecurityException("revoked")
        }
        override fun deactivate() {
            calls += "off"
        }
    }

    private var enabled = true
    private fun controller(g: FakeGateway) = ZenGrayscaleController(g) { enabled }
    private fun level(l: Float) = VisualEffect(l, "com.instagram.android")

    @Test
    fun `first render always syncs the platform off (rule may outlive a crash)`() {
        val g = FakeGateway()
        controller(g).render(level(0f))
        assertEquals(listOf("off"), g.calls)
    }

    @Test
    fun `turns on at the top and stays on through small dips (hysteresis)`() {
        val g = FakeGateway()
        val c = controller(g)
        c.render(level(0.5f))
        c.render(level(0.97f))
        c.render(level(0.98f))
        c.render(level(0.95f))
        c.render(level(1f))
        assertEquals(listOf("off", "on"), g.calls)

        c.render(level(0.89f))
        assertEquals(listOf("off", "on", "off"), g.calls)
    }

    @Test
    fun `disabled setting or missing access never activates`() {
        val g = FakeGateway()
        enabled = false
        val c = controller(g)
        c.render(level(1f))
        g.access = false
        enabled = true
        c.render(level(1f))
        assertEquals("only the initial sync", listOf("off"), g.calls)
    }

    @Test
    fun `without access it does not even try to deactivate`() {
        val g = FakeGateway().apply { access = false }
        controller(g).render(level(0f))
        assertEquals(emptyList<String>(), g.calls)
    }

    @Test
    fun `turning the setting off while gray switches back`() {
        val g = FakeGateway()
        val c = controller(g)
        c.render(level(1f))
        enabled = false
        c.render(level(1f))
        assertEquals(listOf("on", "off"), g.calls)
    }

    @Test
    fun `a failing platform call neither crashes nor retries every tick`() {
        val g = FakeGateway().apply { throwOnActivate = true }
        val c = controller(g)
        repeat(5) { c.render(level(1f)) }
        assertEquals(listOf("on"), g.calls)
    }

    @Test
    fun `release switches off`() {
        val g = FakeGateway()
        val c = controller(g)
        c.render(level(1f))
        c.release()
        assertEquals(listOf("on", "off"), g.calls)
    }
}
