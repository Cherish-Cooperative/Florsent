package app.florsent.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ContrastAuditTest {

    @Test
    fun `ratio matches the WCAG reference values`() {
        assertEquals(21.0, ContrastAudit.ratio(0xFF000000.toInt(), 0xFFFFFFFF.toInt()), 0.01)
        assertEquals(1.0, ContrastAudit.ratio(0xFF777777.toInt(), 0xFF777777.toInt()), 0.001)
        assertEquals(4.48, ContrastAudit.ratio(0xFF777777.toInt(), 0xFFFFFFFF.toInt()), 0.02)
    }

    @Test
    fun `blending a tint over a base`() {
        val mid = ContrastAudit.blend(0xFFFFFFFF.toInt(), 0xFF000000.toInt(), 0.5f)
        assertEquals(0x7F, mid and 0xFF)
    }

    @Test
    fun `the brand light palette passes AA for every text pair`() {
        val failures = ContrastAudit.audit(LightColors.textPairs()).filterNot { it.passes }
        assertTrue("failing: $failures", failures.isEmpty())
    }

    @Test
    fun `the brand dark palette passes AA for every text pair`() {
        val failures = ContrastAudit.audit(DarkColors.textPairs()).filterNot { it.passes }
        assertTrue("failing: $failures", failures.isEmpty())
    }
}
