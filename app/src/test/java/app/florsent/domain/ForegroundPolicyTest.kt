package app.florsent.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ForegroundPolicyTest {

    private val self = "app.florsent"
    private val ime = setOf("com.google.android.inputmethod.latin")

    private fun resolve(pkg: String?, cls: String? = "$pkg.MainActivity") =
        ForegroundPolicy.resolve(pkg, cls, self, ime)

    @Test
    fun `regular apps become the foreground`() {
        assertEquals("com.instagram.android", resolve("com.instagram.android"))
        assertEquals("com.instagram.android", resolve("com.instagram.android", "android.widget.FrameLayout"))
    }

    @Test
    fun `system ui and keyboards do not replace the foreground app`() {
        assertNull(resolve("com.android.systemui"))
        assertNull(resolve("com.google.android.permissioncontroller"))
        assertNull(resolve("com.google.android.inputmethod.latin"))
        assertNull(resolve(null))
        assertNull(resolve(""))
    }

    @Test
    fun `only our activities count, not our overlay windows`() {
        assertEquals(self, resolve(self, "app.florsent.MainActivity"))
        assertNull(resolve(self, "android.widget.FrameLayout"))
        assertNull(resolve(self, null))
    }
}
