package app.florsent.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import app.florsent.domain.GuidanceMode
import app.florsent.domain.GuidanceState
import java.io.IOException
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first

/**
 * Persists the durable part of [GuidanceState] (budgets used, mode, timers). Transient fields such
 * as the foreground app, screen state and an active preview are rebuilt at runtime.
 */
class GuidanceStateStore(private val dataStore: DataStore<Preferences>) {

    suspend fun load(): GuidanceState {
        val p = dataStore.data
            .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
            .first()
        return GuidanceState(
            epochDay = p[Keys.EPOCH_DAY] ?: 0,
            dailyUsedMs = p[Keys.DAILY_USED] ?: 0,
            sessionUsedMs = p[Keys.SESSION_USED] ?: 0,
            overBudgetMs = p[Keys.OVER_BUDGET] ?: 0,
            lastWatchedAtMs = p[Keys.LAST_WATCHED]?.takeIf { it >= 0 },
            mode = p[Keys.MODE]?.let { name -> GuidanceMode.entries.firstOrNull { it.name == name } }
                ?: GuidanceMode.Tracking,
            modeSinceMs = p[Keys.MODE_SINCE] ?: 0,
            cooldownUntilMs = p[Keys.COOLDOWN_UNTIL] ?: 0,
            nudgeSnoozedUntilMs = p[Keys.NUDGE_SNOOZED_UNTIL] ?: 0,
        )
    }

    suspend fun save(state: GuidanceState) {
        dataStore.edit { p ->
            p[Keys.EPOCH_DAY] = state.epochDay
            p[Keys.DAILY_USED] = state.dailyUsedMs
            p[Keys.SESSION_USED] = state.sessionUsedMs
            p[Keys.OVER_BUDGET] = state.overBudgetMs
            p[Keys.LAST_WATCHED] = state.lastWatchedAtMs ?: -1
            p[Keys.MODE] = state.mode.name
            p[Keys.MODE_SINCE] = state.modeSinceMs
            p[Keys.COOLDOWN_UNTIL] = state.cooldownUntilMs
            p[Keys.NUDGE_SNOOZED_UNTIL] = state.nudgeSnoozedUntilMs
        }
    }

    private object Keys {
        val EPOCH_DAY = longPreferencesKey("epoch_day")
        val DAILY_USED = longPreferencesKey("daily_used_ms")
        val SESSION_USED = longPreferencesKey("session_used_ms")
        val OVER_BUDGET = longPreferencesKey("over_budget_ms")
        val LAST_WATCHED = longPreferencesKey("last_watched_at_ms")
        val MODE = stringPreferencesKey("mode")
        val MODE_SINCE = longPreferencesKey("mode_since_ms")
        val COOLDOWN_UNTIL = longPreferencesKey("cooldown_until_ms")
        val NUDGE_SNOOZED_UNTIL = longPreferencesKey("nudge_snoozed_until_ms")
    }
}
