package app.florsent.ui.budget

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.florsent.AppContainer
import app.florsent.R
import app.florsent.data.UserSettings
import app.florsent.ui.components.FlorsentTopBar
import app.florsent.ui.components.SoftCard
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class BudgetViewModel(private val container: AppContainer) : ViewModel() {
    val settings: StateFlow<UserSettings?> = container.settings.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun setSession(minutes: Int) = viewModelScope.launch { container.settings.setSessionBudgetMinutes(minutes) }
    fun setDaily(minutes: Int) = viewModelScope.launch { container.settings.setDailyBudgetMinutes(minutes) }
    fun setCooldown(minutes: Int) = viewModelScope.launch { container.settings.setCooldownMinutes(minutes) }
}

@Composable
fun BudgetScreen(
    settings: UserSettings?,
    onBack: () -> Unit,
    onSessionChange: (Int) -> Unit,
    onDailyChange: (Int) -> Unit,
    onCooldownChange: (Int) -> Unit,
) {
    Scaffold(
        topBar = { FlorsentTopBar(stringResource(R.string.budget_title), onBack) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        if (settings == null) return@Scaffold
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                stringResource(R.string.budget_intro),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
            MinutesSlider(
                title = stringResource(R.string.budget_session),
                hint = stringResource(R.string.budget_session_hint),
                value = settings.sessionBudgetMinutes,
                range = 5..60,
                step = 5,
                onChange = onSessionChange,
            )
            MinutesSlider(
                title = stringResource(R.string.budget_daily),
                hint = stringResource(R.string.budget_daily_hint),
                value = settings.dailyBudgetMinutes,
                range = 15..240,
                step = 15,
                onChange = onDailyChange,
            )
            MinutesSlider(
                title = stringResource(R.string.budget_cooldown),
                hint = stringResource(R.string.budget_cooldown_hint),
                value = settings.cooldownMinutes,
                range = 5..60,
                step = 5,
                onChange = onCooldownChange,
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MinutesSlider(
    title: String,
    hint: String,
    value: Int,
    range: IntRange,
    step: Int,
    onChange: (Int) -> Unit,
) {
    // SliderState keeps dragging local and smooth; DataStore is written only when the drag ends.
    val state = remember(value, range, step) {
        SliderState(
            value.toFloat(),
            (range.last - range.first) / step - 1,
            range.first.toFloat()..range.last.toFloat(),
        )
    }
    SoftCard {
        Column(Modifier.padding(horizontal = 20.dp, vertical = 18.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(
                    stringResource(R.string.minutes_format, state.value.roundToInt()),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Text(
                hint,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Slider(
                state = state,
                onValueChange = { state.value = it },
                onValueChangeFinished = { onChange(state.value.roundToInt()) },
            )
        }
    }
}
