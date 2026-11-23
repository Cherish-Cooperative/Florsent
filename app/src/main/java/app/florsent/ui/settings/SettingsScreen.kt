package app.florsent.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.florsent.AppContainer
import app.florsent.BuildConfig
import app.florsent.R
import app.florsent.data.UserSettings
import app.florsent.ui.components.FlorsentTopBar
import app.florsent.ui.components.NavigationRow
import app.florsent.ui.components.SoftCard
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SettingsUiState(val settings: UserSettings, val serviceRunning: Boolean)

class SettingsViewModel(private val container: AppContainer) : ViewModel() {
    val uiState: StateFlow<SettingsUiState?> =
        combine(container.settings.settings, container.guidance.serviceRunning, ::SettingsUiState)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val repo get() = container.settings
    fun setSession(minutes: Int) = viewModelScope.launch { repo.setSessionBudgetMinutes(minutes) }
    fun setDaily(minutes: Int) = viewModelScope.launch { repo.setDailyBudgetMinutes(minutes) }
    fun setCooldown(minutes: Int) = viewModelScope.launch { repo.setCooldownMinutes(minutes) }
    fun setAmbient(on: Boolean) = viewModelScope.launch { repo.setAmbientSound(on) }
    fun setCue(on: Boolean) = viewModelScope.launch { repo.setCueSound(on) }
    fun setReturnHome(on: Boolean) = viewModelScope.launch { repo.setReturnToHomeScreen(on) }
}

@Composable
fun SettingsScreen(
    state: SettingsUiState?,
    vm: SettingsViewModel,
    onBack: () -> Unit,
    onOpenAccessibilitySettings: () -> Unit,
) {
    Scaffold(
        topBar = { FlorsentTopBar(stringResource(R.string.settings_title), onBack) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        if (state == null) return@Scaffold
        val s = state.settings
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
        ) {
            SectionTitle(stringResource(R.string.settings_section_budget))
            Text(
                stringResource(R.string.budget_intro),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 12.dp),
            )
            SoftCard {
                Column {
                    MinutesSlider(
                        stringResource(R.string.budget_session), stringResource(R.string.budget_session_hint),
                        s.sessionBudgetMinutes, 5..60, 5, { vm.setSession(it) },
                    )
                    CardDivider()
                    MinutesSlider(
                        stringResource(R.string.budget_daily), stringResource(R.string.budget_daily_hint),
                        s.dailyBudgetMinutes, 15..240, 15, { vm.setDaily(it) },
                    )
                    CardDivider()
                    MinutesSlider(
                        stringResource(R.string.budget_cooldown), stringResource(R.string.budget_cooldown_hint),
                        s.cooldownMinutes, 5..60, 5, { vm.setCooldown(it) },
                    )
                }
            }

            SectionTitle(stringResource(R.string.settings_section_breathing))
            SoftCard {
                Column {
                    SwitchRow(stringResource(R.string.breathe_ambient_sound),
                        stringResource(R.string.settings_ambient_hint), s.ambientSound) { vm.setAmbient(it) }
                    CardDivider()
                    SwitchRow(stringResource(R.string.breathe_cue_sound),
                        stringResource(R.string.settings_cue_hint), s.cueSound) { vm.setCue(it) }
                    CardDivider()
                    SwitchRow(stringResource(R.string.settings_return_home),
                        stringResource(R.string.settings_return_home_hint), s.returnToHomeScreen) { vm.setReturnHome(it) }
                }
            }

            SectionTitle(stringResource(R.string.settings_section_guidance))
            SoftCard {
                NavigationRow(
                    title = stringResource(R.string.settings_service),
                    value = stringResource(
                        if (state.serviceRunning) R.string.settings_service_on else R.string.settings_service_off,
                    ),
                    onClick = onOpenAccessibilitySettings,
                )
            }

            Text(
                stringResource(R.string.settings_footer, BuildConfig.VERSION_NAME),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.fillMaxWidth().padding(vertical = 28.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 24.dp, bottom = 10.dp, start = 4.dp),
    )
}

@Composable
private fun CardDivider() {
    HorizontalDivider(Modifier.padding(horizontal = 20.dp), color = MaterialTheme.colorScheme.outlineVariant)
}

@Composable
private fun SwitchRow(title: String, hint: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(value = checked, role = Role.Switch, onValueChange = onChange)
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = null)
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
    Column(Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(
                stringResource(R.string.minutes_format, state.value.roundToInt()),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Text(hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(4.dp))
        Slider(
            state = state,
            onValueChange = { state.value = it },
            onValueChangeFinished = { onChange(state.value.roundToInt()) },
        )
    }
}
