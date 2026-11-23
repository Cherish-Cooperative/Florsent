package app.florsent.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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
        assertNull("share sheet", resolve("com.android.intentresolver"))
        assertNull("resolver dialog", resolve("android"))
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

    @Test
    fun `a late event from the app just left is ignored for two seconds only`() {
        val yt = "com.google.android.youtube"
        assertTrue(ForegroundPolicy.isHandoffEcho(yt, yt, leftAtMs = 1_000, nowMs = 1_000))
        assertTrue(ForegroundPolicy.isHandoffEcho(yt, yt, 1_000, 2_999))
        assertFalse("really going back counts", ForegroundPolicy.isHandoffEcho(yt, yt, 1_000, 3_000))
        assertFalse("other apps always count", ForegroundPolicy.isHandoffEcho("com.android.chrome", yt, 1_000, 1_500))
        assertFalse(ForegroundPolicy.isHandoffEcho(yt, null, 1_000, 1_500))
        assertFalse("clock went back", ForegroundPolicy.isHandoffEcho(yt, yt, 1_000, 500))
    }
}
