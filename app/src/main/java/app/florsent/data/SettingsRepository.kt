package app.florsent.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import app.florsent.domain.GuidanceConfig
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

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
) {
    fun toGuidanceConfig(): GuidanceConfig = GuidanceConfig(
        watchedPackages = watchedPackages,
        sessionBudgetMs = sessionBudgetMinutes * MINUTE_MS,
        dailyBudgetMs = dailyBudgetMinutes * MINUTE_MS,
        cooldownMs = cooldownMinutes * MINUTE_MS,
    )

    companion object {
        const val DEFAULT_SESSION_MINUTES = 15
        const val DEFAULT_DAILY_MINUTES = 60
        const val DEFAULT_COOLDOWN_MINUTES = 20
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
    }
}
