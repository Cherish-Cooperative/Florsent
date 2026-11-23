package app.florsent.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SocialAppMatcherTest {

    private fun app(pkg: String, label: String) = AppLabel(pkg, label)

    // The real Pixel 10 Pro case: official YouTube disabled (so not launchable), Morphe in daily use.
    private val pixel = listOf(
        app("app.morphe.android.youtube", "YouTube Morphe"),
        app("com.instagram.android", "Instagram"),
        app("com.instagram.barcelona", "Threads"),
        app("com.facebook.katana", "Facebook"),
        app("com.twitter.android", "X"),
        app("com.google.android.apps.youtube.music", "YouTube Music"),
        app("com.android.chrome", "Chrome"),
    )

    @Test
    fun `first launch picks the client in use, never a disabled official app`() {
        val picked = SocialAppMatcher.suggest(pixel)
        assertEquals(
            setOf(
                "app.morphe.android.youtube", "com.instagram.android", "com.instagram.barcelona",
                "com.facebook.katana", "com.twitter.android",
            ),
            picked,
        )
    }

    @Test
    fun `existing users get the client they use when the watched official app cannot open`() {
        val watched = setOf(
            "com.google.android.youtube", // disabled on that phone
            "com.instagram.android", "com.instagram.barcelona", "com.facebook.katana", "com.twitter.android",
        )
        val repaired = SocialAppMatcher.repair(watched, pixel)
        assertEquals(watched + "app.morphe.android.youtube", repaired)
    }

    @Test
    fun `repair never adds brands the user did not choose, nor removes anything`() {
        val onlyInstagram = setOf("com.instagram.android")
        assertEquals(onlyInstagram, SocialAppMatcher.repair(onlyInstagram, pixel))
        val custom = setOf("com.google.android.youtube", "com.example.custom")
        assertEquals(custom + "app.morphe.android.youtube", SocialAppMatcher.repair(custom, pixel))
    }

    @Test
    fun `when the official app still opens, the list is left as it is`() {
        val launchable = pixel + app("com.google.android.youtube", "YouTube")
        val watched = setOf("com.google.android.youtube")
        assertEquals(watched, SocialAppMatcher.repair(watched, launchable))
    }

    @Test
    fun `brand by label, excluding companion apps that are not a feed`() {
        assertEquals("youtube", SocialAppMatcher.brandOf(app("app.revanced.android.youtube", "YouTube ReVanced")))
        assertEquals("instagram", SocialAppMatcher.brandOf(app("com.example.ig", "Instagram Pro")))
        assertEquals("xiaohongshu", SocialAppMatcher.brandOf(app("com.example.xhs", "小紅書")))
        assertNull(SocialAppMatcher.brandOf(app("com.google.android.apps.youtube.music", "YouTube Music")))
        assertNull(SocialAppMatcher.brandOf(app("com.google.android.apps.youtube.creator", "YouTube Studio")))
        assertNull(SocialAppMatcher.brandOf(app("com.google.android.apps.youtube.kids", "YouTube Kids")))
        assertNull(SocialAppMatcher.brandOf(app("com.android.chrome", "Chrome")))
        assertEquals("by package even with an unusual label", "twitter", SocialAppMatcher.brandOf(app("com.twitter.android", "X")))
    }
}
