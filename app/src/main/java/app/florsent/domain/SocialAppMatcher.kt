package app.florsent.domain

/** A launchable app as seen in the launcher: package name plus its user-visible label. */
data class AppLabel(val packageName: String, val label: String)

/**
 * Finds the social apps a person actually uses. Package names alone are not enough: people run
 * modified or alternative clients (ReVanced/Morphe YouTube, Instagram mods, …) under other package
 * names, and the official app is often disabled. A disabled app is still "installed", so checking
 * installation alone can watch an app that never opens while the one in daily use is never timed.
 *
 * Rules: only launchable apps count; an app matches by its known package or by a brand word in its
 * label, excluding companion apps that are not the feed (YouTube Music, Studio, Kids, …).
 */
object SocialAppMatcher {

    private class Brand(val id: String, val officialPackages: Set<String>, val words: List<String>)

    private val brands = listOf(
        Brand("youtube", setOf("com.google.android.youtube"), listOf("youtube")),
        Brand("instagram", setOf("com.instagram.android"), listOf("instagram")),
        Brand("threads", setOf("com.instagram.barcelona"), listOf("threads")),
        Brand("facebook", setOf("com.facebook.katana", "com.facebook.lite"), listOf("facebook")),
        Brand("tiktok", setOf("com.zhiliaoapp.musically", "com.ss.android.ugc.trill"), listOf("tiktok", "抖音")),
        Brand("twitter", setOf("com.twitter.android"), listOf("twitter")),
        Brand("reddit", setOf("com.reddit.frontpage"), listOf("reddit")),
        Brand("snapchat", setOf("com.snapchat.android"), listOf("snapchat")),
        Brand("pinterest", setOf("com.pinterest"), listOf("pinterest")),
        Brand("twitch", setOf("tv.twitch.android.app"), listOf("twitch")),
        Brand("xiaohongshu", setOf("com.xingin.xhs"), listOf("小紅書", "小红书", "xiaohongshu", "rednote")),
    )

    /** Companion apps that share a brand word but are not an endless feed. */
    private val notTheFeed = listOf(
        "music", "studio", "kids", "create", "creator", "business", "manager", "ads", "edits", "tv", "vanced manager",
    )

    /** The brand an app belongs to, by package first, then by label; null if it is not a social feed. */
    fun brandOf(app: AppLabel): String? {
        brands.firstOrNull { app.packageName in it.officialPackages }?.let { return it.id }
        val label = app.label.lowercase()
        if (notTheFeed.any { Regex("""\b${Regex.escape(it)}\b""").containsMatchIn(label) }) return null
        return brands.firstOrNull { brand -> brand.words.any { it in label } }?.id
    }

    /** First-launch preselection: every launchable app of a known social brand. */
    fun suggest(launchable: List<AppLabel>): Set<String> =
        launchable.filter { brandOf(it) != null }.mapTo(linkedSetOf()) { it.packageName }

    /**
     * One-time repair of an existing watch list: for each brand the user watches only through apps
     * that cannot be launched (disabled or uninstalled), add the launchable apps of that brand.
     * Never removes anything and never adds brands the user did not choose.
     */
    fun repair(watched: Set<String>, launchable: List<AppLabel>): Set<String> {
        val launchablePackages = launchable.mapTo(hashSetOf()) { it.packageName }
        val brandsWatchedButUnreachable = brands
            .filter { brand ->
                val watchedOfBrand = watched.filter { pkg -> brandOf(AppLabel(pkg, "")) == brand.id }
                watchedOfBrand.isNotEmpty() && watchedOfBrand.none { it in launchablePackages }
            }
            .mapTo(hashSetOf()) { it.id }
        if (brandsWatchedButUnreachable.isEmpty()) return watched
        val additions = launchable.filter { brandOf(it) in brandsWatchedButUnreachable }.map { it.packageName }
        return watched + additions
    }
}
