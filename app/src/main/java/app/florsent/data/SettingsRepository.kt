package app.florsent.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import app.florsent.domain.BedtimeConfig
import app.florsent.domain.GuidanceConfig
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/** Consent to keep usage history; existing users are asked once after upgrading. */
enum class UsageConsent { Unknown, Granted, Denied }

data class UserSettings(
    val watchedPackages: Set<String> = emptySet(),
    val watchedInitialized: Boolean = false,
    val sessionBudgetMinutes: Int = DEFAULT_SESSION_MINUTES,
    val dailyBudgetMinutes: Int = DEFAULT_DAILY_MINUTES,
    val cooldownMinutes: Int = DEFAULT_COOLDOWN_MINUTES,
    val disclosureAccepted: Boolean = false,
    val breathingPatternId: String? = null,
    val ambientSound: Boolean = false,
    val cueSound: Boolean = false,
    val returnToHomeScreen: Boolean = true,
    val onboardingCompleted: Boolean = false,
    /** Android 15+: switch to true grayscale (Modes device effect) when the fade is complete. */
    val zenGrayscaleFinish: Boolean = false,
    /** Advanced: per-app desaturation through Shizuku. */
    val shizukuSaturation: Boolean = false,
    /** Android 12+: use the wallpaper-based Material You palette instead of Florsent's sage. */
    val dynamicColor: Boolean = false,
    /** Whether the user agreed to keep 30 days of usage history on the phone. */
    val usageConsent: UsageConsent = UsageConsent.Unknown,
    /** Intended bedtime as minutes after midnight; null = no bedtime guidance. */
    val bedtimeAnchorMinute: Int? = null,
    val bedtimeThresholdMinutes: Int = DEFAULT_BEDTIME_THRESHOLD,
    /** The one-time home invitation was answered ("not now" or a time was set). */
    val bedtimeInviteAnswered: Boolean = false,
) {
    val recordingAllowed: Boolean get() = usageConsent == UsageConsent.Granted

    fun toGuidanceConfig(): GuidanceConfig = GuidanceConfig(
        watchedPackages = watchedPackages,
        sessionBudgetMs = sessionBudgetMinutes * MINUTE_MS,
        dailyBudgetMs = dailyBudgetMinutes * MINUTE_MS,
        cooldownMs = cooldownMinutes * MINUTE_MS,
        bedtime = bedtimeAnchorMinute?.let {
            BedtimeConfig(anchorMinuteOfDay = it, thresholdMs = bedtimeThresholdMinutes * MINUTE_MS)
        },
    )

    companion object {
        const val DEFAULT_SESSION_MINUTES = 15
        const val DEFAULT_DAILY_MINUTES = 60
        const val DEFAULT_COOLDOWN_MINUTES = 20
        const val DEFAULT_BEDTIME_THRESHOLD = 15
        val BEDTIME_THRESHOLD_CHOICES = listOf(10, 15, 20)
        private const val MINUTE_MS = 60_000L
    }
}

class SettingsRepository(private val dataStore: DataStore<Preferences>) {

    val settings: Flow<UserSettings> = dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { p ->
            UserSettings(
                watchedPackages = p[Keys.WATCHED] ?: emptySet(),
                watchedInitialized = p[Keys.WATCHED_INITIALIZED] ?: false,
                sessionBudgetMinutes = p[Keys.SESSION_MINUTES] ?: UserSettings.DEFAULT_SESSION_MINUTES,
                dailyBudgetMinutes = p[Keys.DAILY_MINUTES] ?: UserSettings.DEFAULT_DAILY_MINUTES,
                cooldownMinutes = p[Keys.COOLDOWN_MINUTES] ?: UserSettings.DEFAULT_COOLDOWN_MINUTES,
                disclosureAccepted = p[Keys.DISCLOSURE_ACCEPTED] ?: false,
                breathingPatternId = p[Keys.PATTERN_ID],
                ambientSound = p[Keys.AMBIENT_SOUND] ?: false,
                cueSound = p[Keys.CUE_SOUND] ?: false,
                returnToHomeScreen = p[Keys.RETURN_HOME] ?: true,
                onboardingCompleted = p[Keys.ONBOARDING_COMPLETED] ?: false,
                zenGrayscaleFinish = p[Keys.ZEN_GRAYSCALE] ?: false,
                shizukuSaturation = p[Keys.SHIZUKU_SATURATION] ?: false,
                dynamicColor = p[Keys.DYNAMIC_COLOR] ?: false,
                usageConsent = UsageConsent.entries.getOrElse(p[Keys.USAGE_CONSENT] ?: 0) { UsageConsent.Unknown },
                bedtimeAnchorMinute = p[Keys.BEDTIME_ANCHOR]?.takeIf { it in 0 until 24 * 60 },
                bedtimeThresholdMinutes = p[Keys.BEDTIME_THRESHOLD] ?: UserSettings.DEFAULT_BEDTIME_THRESHOLD,
                bedtimeInviteAnswered = p[Keys.BEDTIME_INVITE_ANSWERED] ?: false,
            )
        }
        .distinctUntilChanged()

    suspend fun setWatched(packageName: String, watched: Boolean) {
        dataStore.edit { p ->
            val current = p[Keys.WATCHED] ?: emptySet()
            p[Keys.WATCHED] = if (watched) current + packageName else current - packageName
            p[Keys.WATCHED_INITIALIZED] = true
        }
    }

    /** Preselects [defaults] the first time only; later edits by the user are never overwritten. */
    suspend fun initializeWatchedIfNeeded(defaults: Set<String>) {
        dataStore.edit { p ->
            if (p[Keys.WATCHED_INITIALIZED] != true) {
                p[Keys.WATCHED] = defaults
                p[Keys.WATCHED_INITIALIZED] = true
            }
        }
    }

    suspend fun setSessionBudgetMinutes(minutes: Int) = dataStore.edit { it[Keys.SESSION_MINUTES] = minutes }
    suspend fun setDailyBudgetMinutes(minutes: Int) = dataStore.edit { it[Keys.DAILY_MINUTES] = minutes }
    suspend fun setCooldownMinutes(minutes: Int) = dataStore.edit { it[Keys.COOLDOWN_MINUTES] = minutes }
    suspend fun setDisclosureAccepted(accepted: Boolean) =
        dataStore.edit { it[Keys.DISCLOSURE_ACCEPTED] = accepted }

    suspend fun setBreathingPattern(id: String) = dataStore.edit { it[Keys.PATTERN_ID] = id }
    suspend fun setAmbientSound(on: Boolean) = dataStore.edit { it[Keys.AMBIENT_SOUND] = on }
    suspend fun setCueSound(on: Boolean) = dataStore.edit { it[Keys.CUE_SOUND] = on }
    suspend fun setReturnToHomeScreen(on: Boolean) = dataStore.edit { it[Keys.RETURN_HOME] = on }

    suspend fun setOnboardingCompleted() = dataStore.edit { it[Keys.ONBOARDING_COMPLETED] = true }
    suspend fun setZenGrayscaleFinish(on: Boolean) = dataStore.edit { it[Keys.ZEN_GRAYSCALE] = on }
    suspend fun setShizukuSaturation(on: Boolean) = dataStore.edit { it[Keys.SHIZUKU_SATURATION] = on }
    suspend fun setDynamicColor(on: Boolean) = dataStore.edit { it[Keys.DYNAMIC_COLOR] = on }
    suspend fun setUsageConsent(consent: UsageConsent) = dataStore.edit { it[Keys.USAGE_CONSENT] = consent.ordinal }

    /** Sets (or with null, turns off) bedtime guidance; either way the invitation is answered. */
    suspend fun setBedtimeAnchor(minuteOfDay: Int?) = dataStore.edit {
        if (minuteOfDay == null) it.remove(Keys.BEDTIME_ANCHOR) else it[Keys.BEDTIME_ANCHOR] = minuteOfDay
        it[Keys.BEDTIME_INVITE_ANSWERED] = true
    }

    suspend fun setBedtimeThresholdMinutes(minutes: Int) = dataStore.edit { it[Keys.BEDTIME_THRESHOLD] = minutes }
    suspend fun dismissBedtimeInvite() = dataStore.edit { it[Keys.BEDTIME_INVITE_ANSWERED] = true }

    private object Keys {
        val WATCHED = stringSetPreferencesKey("watched_packages")
        val WATCHED_INITIALIZED = booleanPreferencesKey("watched_initialized")
        val SESSION_MINUTES = intPreferencesKey("session_budget_minutes")
        val DAILY_MINUTES = intPreferencesKey("daily_budget_minutes")
        val COOLDOWN_MINUTES = intPreferencesKey("cooldown_minutes")
        val DISCLOSURE_ACCEPTED = booleanPreferencesKey("a11y_disclosure_accepted")
        val PATTERN_ID = stringPreferencesKey("breathing_pattern_id")
        val AMBIENT_SOUND = booleanPreferencesKey("ambient_sound")
        val CUE_SOUND = booleanPreferencesKey("cue_sound")
        val RETURN_HOME = booleanPreferencesKey("return_to_home_screen")
        val ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")
        val ZEN_GRAYSCALE = booleanPreferencesKey("zen_grayscale_finish")
        val SHIZUKU_SATURATION = booleanPreferencesKey("shizuku_saturation")
        val DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
        val USAGE_CONSENT = intPreferencesKey("usage_consent")
        val BEDTIME_ANCHOR = intPreferencesKey("bedtime_anchor_minute")
        val BEDTIME_THRESHOLD = intPreferencesKey("bedtime_threshold_minutes")
        val BEDTIME_INVITE_ANSWERED = booleanPreferencesKey("bedtime_invite_answered")
    }
}
