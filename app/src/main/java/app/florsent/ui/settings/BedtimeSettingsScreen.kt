package app.florsent.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.florsent.AppContainer
import app.florsent.R
import app.florsent.data.UserSettings
import app.florsent.domain.BedtimeLearner
import app.florsent.domain.BedtimeLearning
import app.florsent.ui.components.BedtimePickerDialog
import app.florsent.ui.components.EqualChipGrid
import app.florsent.ui.components.FlorsentTopBar
import app.florsent.ui.components.PillOption
import app.florsent.ui.components.SoftCard
import app.florsent.ui.components.clockText
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class BedtimeUiState(val settings: UserSettings, val learning: BedtimeLearning?)

class BedtimeViewModel(private val container: AppContainer) : ViewModel() {
    val uiState: StateFlow<BedtimeUiState?> =
        combine(container.settings.settings, container.analysis.result) { s, analysis ->
            BedtimeUiState(s, analysis?.learning)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    init {
        viewModelScope.launch { container.analysis.refresh() }
    }

    fun setAnchor(minuteOfDay: Int?) = viewModelScope.launch { container.settings.setBedtimeAnchor(minuteOfDay) }
    fun setThreshold(minutes: Int) = viewModelScope.launch { container.settings.setBedtimeThresholdMinutes(minutes) }
}

@Composable
fun BedtimeSettingsScreen(state: BedtimeUiState?, vm: BedtimeViewModel, onBack: () -> Unit) {
    var picking by rememberSaveable { mutableStateOf(false) }
    Scaffold(
        topBar = { FlorsentTopBar(stringResource(R.string.bedtime_title), onBack) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        if (state == null) return@Scaffold
        val s = state.settings
        val learned = (state.learning as? BedtimeLearning.Confident)?.let { BedtimeLearner.roundTo5(it.minuteOfDay) }
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
        ) {
            Spacer(Modifier.height(8.dp))
            // What Florsent has noticed, in one sentence.
            Text(
                learningText(s, state.learning),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(16.dp))
            SoftCard {
                Column(Modifier.padding(20.dp)) {
                    val anchor = s.bedtimeAnchorMinute
                    Text(
                        if (anchor != null) {
                            stringResource(R.string.bedtime_current, clockText(anchor - 30), s.bedtimeThresholdMinutes)
                        } else {
                            stringResource(R.string.bedtime_current_off)
                        },
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.semantics { heading() },
                    )
                    if (anchor != null) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            stringResource(R.string.bedtime_note),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = { picking = true }, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(if (anchor == null) R.string.bedtime_choose_time else R.string.bedtime_change_time))
                    }
                    if (learned != null && learned != anchor) {
                        OutlinedButton(onClick = { vm.setAnchor(learned) }, modifier = Modifier.fillMaxWidth()) {
                            Text(stringResource(R.string.bedtime_use_learned, clockText(learned)))
                        }
                    }
                }
            }
            if (s.bedtimeAnchorMinute != null) {
                Spacer(Modifier.height(16.dp))
                SoftCard {
                    Column(Modifier.padding(20.dp)) {
                        Text(stringResource(R.string.bedtime_threshold), style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(10.dp))
                        EqualChipGrid {
                            UserSettings.BEDTIME_THRESHOLD_CHOICES.forEach { minutes ->
                                PillOption(
                                    label = stringResource(R.string.minutes_format, minutes),
                                    selected = s.bedtimeThresholdMinutes == minutes,
                                    onClick = { vm.setThreshold(minutes) },
                                )
                            }
                        }
                    }
                }
                TextButton(onClick = { vm.setAnchor(null) }, modifier = Modifier.padding(top = 12.dp)) {
                    Text(stringResource(R.string.bedtime_turn_off), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
    if (picking) {
        val initial = state?.settings?.bedtimeAnchorMinute
            ?: (state?.learning as? BedtimeLearning.Confident)?.let { BedtimeLearner.roundTo5(it.minuteOfDay) }
            ?: DEFAULT_PICKER_MINUTE
        BedtimePickerDialog(
            initialMinuteOfDay = initial,
            onConfirm = {
                picking = false
                vm.setAnchor(it)
            },
            onDismiss = { picking = false },
        )
    }
}

@Composable
private fun learningText(s: UserSettings, learning: BedtimeLearning?): String = when {
    !s.recordingAllowed -> stringResource(R.string.bedtime_status_no_record)
    learning is BedtimeLearning.Confident ->
        stringResource(R.string.bedtime_status_confident, clockText(BedtimeLearner.roundTo5(learning.minuteOfDay)))
    learning is BedtimeLearning.Irregular -> stringResource(R.string.bedtime_status_irregular)
    learning is BedtimeLearning.Learning && learning.nightsObserved > 0 ->
        pluralStringResource(R.plurals.bedtime_status_learning, learning.nightsObserved, learning.nightsObserved)
    else -> stringResource(R.string.bedtime_status_learning_start)
}

private const val DEFAULT_PICKER_MINUTE = 23 * 60
