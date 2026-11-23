package app.florsent.data

import android.app.LocaleManager
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import java.util.Locale

/** The in-app language choice. [tag] is a BCP 47 tag; empty means "follow the system". */
enum class AppLanguage(val tag: String) {
    System(""),
    TraditionalChinese("zh-TW"),
    English("en"),
    ;

    companion object {
        /** Maps stored or system-reported tags ("zh-Hant-TW", "en-US", "") back to a choice. */
        fun fromTags(tags: String?): AppLanguage {
            val first = tags.orEmpty().split(',').first().trim()
            return when {
                first.isEmpty() -> System
                first.startsWith("zh", ignoreCase = true) -> TraditionalChinese
                first.startsWith("en", ignoreCase = true) -> English
                else -> System
            }
        }
    }
}

/**
 * Per-app language. Android 13+ uses the platform's per-app locale (also exposed in system
 * Settings > Apps > Florsent > Language, via res/xml/locales_config.xml) and applies it to the
 * whole process, including the accessibility service. Older releases have no such API: the choice
 * is kept in a small SharedPreferences file and applied by wrapping each component's context.
 */
object AppLocales {

    private const val PREFS = "app_language"
    private const val KEY_TAGS = "tags"

    fun current(context: Context): AppLanguage = AppLanguage.fromTags(storedTags(context))

    /** Applies [language]. On Android 13+ the system recreates visible activities by itself. */
    fun set(context: Context, language: AppLanguage) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.getSystemService(LocaleManager::class.java).applicationLocales =
                LocaleList.forLanguageTags(language.tag)
        } else {
            prefs(context).edit().putString(KEY_TAGS, language.tag).apply()
        }
    }

    /** Whether the caller must recreate its activity to show the new language (Android 12 and older). */
    val needsManualRecreate: Boolean get() = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU

    /** Android 12 and older: returns [base] configured with the stored language, if any. */
    fun wrap(base: Context): Context {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) return base
        val tag = prefs(base).getString(KEY_TAGS, "").orEmpty()
        if (tag.isEmpty()) return base
        val locale = Locale.forLanguageTag(tag)
        Locale.setDefault(locale)
        val config = Configuration(base.resources.configuration).apply { setLocales(LocaleList(locale)) }
        return base.createConfigurationContext(config)
    }

    private fun storedTags(context: Context): String =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.getSystemService(LocaleManager::class.java).applicationLocales.toLanguageTags()
        } else {
            prefs(context).getString(KEY_TAGS, "").orEmpty()
        }

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
