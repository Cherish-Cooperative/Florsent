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
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TextButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SliderState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.florsent.AppContainer
import app.florsent.BuildConfig
import app.florsent.R
import app.florsent.data.UsageConsent
import app.florsent.data.UserSettings
import app.florsent.ui.components.clockText
import app.florsent.service.effect.ZenGrayscale
import app.florsent.service.shizuku.ShizukuSaturation
import app.florsent.service.shizuku.ShizukuState
import app.florsent.ui.theme.supportsDynamicColor
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

class SettingsViewModel(
    private val container: AppContainer,
    private val appContext: Context,
) : ViewModel() {
    val uiState: StateFlow<SettingsUiState?> =
        combine(container.settings.settings, container.guidance.serviceRunning, ::SettingsUiState)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val repo get() = container.settings
    fun setSession(minutes: Int) = viewModelScope.launch { repo.setSessionBudgetMinutes(minutes) }
    fun setDaily(minutes: Int) = viewModelScope.launch { repo.setDailyBudgetMinutes(minutes) }
    fun setCooldown(minutes: Int) = viewModelScope.launch { repo.setCooldownMinutes(minutes) }
    fun setAmbient(on: Boolean) = viewModelScope.launch { repo.setAmbientSound(on) }
    fun setCue(on: Boolean) = viewModelScope.launch { repo.setCueSound(on) }
    fun setFadeSound(on: Boolean) = viewModelScope.launch { repo.setFadeSound(on) }
    fun setReturnHome(on: Boolean) = viewModelScope.launch { repo.setReturnToHomeScreen(on) }
    fun setDynamicColor(on: Boolean) = viewModelScope.launch { repo.setDynamicColor(on) }

    fun hasZenAccess(): Boolean = ZenGrayscale.hasAccess(appContext)

    val shizukuState: StateFlow<ShizukuState> = container.shizuku.state
    val shizukuDegraded: StateFlow<Boolean> = container.shizuku.degraded
    val shizukuPendingRestore: StateFlow<Boolean> = container.shizuku.pendingRestore

    fun refreshShizuku() = container.shizuku.refresh()
    fun requestShizukuPermission() = container.shizuku.requestPermission()
    fun retryShizuku() = container.shizuku.retry()

    fun setShizuku(on: Boolean) {
        viewModelScope.launch { repo.setShizukuSaturation(on) }
        // Restore right away even if the accessibility service is not running to do it.
        if (!on) container.shizuku.restoreAll()
    }

    fun setRecording(on: Boolean) = viewModelScope.launch {
        repo.setUsageConsent(if (on) UsageConsent.Granted else UsageConsent.Denied)
    }

    fun clearHistory() = container.clearUsageHistory()

    fun setZenGrayscale(on: Boolean) = viewModelScope.launch {
        repo.setZenGrayscaleFinish(on)
        if (!on) ZenGrayscale.removeRule(appContext)
    }
}

enum class SettingsPage { Budget, Bedtime, Breathing, Advanced }

/** First level: five rows; everything else is one tap further (progressive disclosure). */
@Composable
fun SettingsHomeScreen(
    state: SettingsUiState?,
    onBack: () -> Unit,
    onOpenWatchedApps: () -> Unit,
    onOpen: (SettingsPage) -> Unit,
) {
    SettingsScaffold(stringResource(R.string.settings_title), onBack) {
        if (state == null) return@SettingsScaffold
        val s = state.settings
        Spacer(Modifier.height(8.dp))
        SoftCard {
            Column {
                NavigationRow(
                    stringResource(R.string.home_watched_apps),
                    stringResource(R.string.home_watched_apps_value, s.watchedPackages.size),
                    onOpenWatchedApps,
                )
                CardDivider()
                NavigationRow(
                    stringResource(R.string.settings_row_budget),
                    stringResource(R.string.settings_row_budget_value, s.sessionBudgetMinutes, s.dailyBudgetMinutes),
                ) { onOpen(SettingsPage.Budget) }
                CardDivider()
                NavigationRow(
                    stringResource(R.string.settings_row_bedtime),
                    s.bedtimeAnchorMinute?.let { stringResource(R.string.settings_row_bedtime_on, clockText(it - 30)) }
                        ?: stringResource(R.string.settings_row_bedtime_off),
                ) { onOpen(SettingsPage.Bedtime) }
                CardDivider()
                NavigationRow(stringResource(R.string.settings_row_breathing)) { onOpen(SettingsPage.Breathing) }
                CardDivider()
                NavigationRow(stringResource(R.string.settings_row_advanced)) { onOpen(SettingsPage.Advanced) }
            }
        }
        Footer()
    }
}

@Composable
fun BudgetSettingsScreen(state: SettingsUiState?, vm: SettingsViewModel, onBack: () -> Unit) {
    SettingsScaffold(stringResource(R.string.settings_row_budget), onBack) {
        if (state == null) return@SettingsScaffold
        val s = state.settings
        Spacer(Modifier.height(8.dp))
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
    }
}

@Composable
fun BreathingSettingsScreen(state: SettingsUiState?, vm: SettingsViewModel, onBack: () -> Unit) {
    SettingsScaffold(stringResource(R.string.settings_row_breathing), onBack) {
        if (state == null) return@SettingsScaffold
        val s = state.settings
        Spacer(Modifier.height(8.dp))
        SoftCard {
            Column {
                SwitchRow(stringResource(R.string.settings_ambient),
                    stringResource(R.string.settings_ambient_hint), s.ambientSound) { vm.setAmbient(it) }
                CardDivider()
                SwitchRow(stringResource(R.string.settings_fade_sound),
                    stringResource(R.string.settings_fade_sound_hint), s.fadeSound) { vm.setFadeSound(it) }
                CardDivider()
                SwitchRow(stringResource(R.string.breathe_cue_sound),
                    stringResource(R.string.settings_cue_hint), s.cueSound) { vm.setCue(it) }
                CardDivider()
                SwitchRow(stringResource(R.string.settings_return_home), null, s.returnToHomeScreen) {
                    vm.setReturnHome(it)
                }
            }
        }
    }
}

@Composable
fun AdvancedSettingsScreen(
    state: SettingsUiState?,
    vm: SettingsViewModel,
    onBack: () -> Unit,
    onOpenAccessibilitySettings: () -> Unit,
    onOpenZenAccessSettings: () -> Unit,
) {
    var confirmClear by rememberSaveable { mutableStateOf(false) }
    SettingsScaffold(stringResource(R.string.settings_row_advanced), onBack) {
        if (state == null) return@SettingsScaffold
        val s = state.settings

        if (supportsDynamicColor) {
            SectionTitle(stringResource(R.string.settings_section_appearance))
            SoftCard {
                SwitchRow(
                    stringResource(R.string.settings_dynamic_color),
                    stringResource(R.string.settings_dynamic_color_hint),
                    s.dynamicColor,
                ) { vm.setDynamicColor(it) }
            }
        }

        SectionTitle(stringResource(R.string.settings_section_guidance))
        SoftCard {
            Column {
                NavigationRow(
                    title = stringResource(R.string.settings_service),
                    value = stringResource(
                        if (state.serviceRunning) R.string.settings_service_on else R.string.settings_service_off,
                    ),
                    onClick = onOpenAccessibilitySettings,
                )
                if (ZenGrayscale.isSupported) {
                    CardDivider()
                    ZenGrayscaleRow(enabled = s.zenGrayscaleFinish, vm = vm, onOpenAccessSettings = onOpenZenAccessSettings)
                }
                CardDivider()
                ShizukuRow(enabled = s.shizukuSaturation, vm = vm)
            }
        }

        SectionTitle(stringResource(R.string.settings_section_records))
        SoftCard {
            Column {
                SwitchRow(
                    stringResource(R.string.settings_recording),
                    stringResource(R.string.settings_recording_hint),
                    s.recordingAllowed,
                ) { vm.setRecording(it) }
                CardDivider()
                NavigationRow(stringResource(R.string.settings_clear_history)) { confirmClear = true }
            }
        }
        Footer()
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text(stringResource(R.string.clear_history_title)) },
            text = { Text(stringResource(R.string.clear_history_body)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmClear = false
                    vm.clearHistory()
                }) { Text(stringResource(R.string.clear_history_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmClear = false }) { Text(stringResource(R.string.dialog_cancel)) }
            },
        )
    }
}

@Composable
private fun SettingsScaffold(title: String, onBack: () -> Unit, content: @Composable () -> Unit) {
    Scaffold(
        topBar = { FlorsentTopBar(title, onBack) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
        ) {
            content()
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun Footer() {
    Text(
        stringResource(R.string.settings_footer, BuildConfig.VERSION_NAME),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.fillMaxWidth().padding(vertical = 28.dp),
        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
    )
}

/**
 * Advanced per-app desaturation through Shizuku. The switch only works once Shizuku is running and
 * Florsent is allowed; every other state shows one plain sentence and one action.
 */
@Composable
private fun ShizukuRow(enabled: Boolean, vm: SettingsViewModel) {
    val shizukuState by vm.shizukuState.collectAsStateWithLifecycle()
    val degraded by vm.shizukuDegraded.collectAsStateWithLifecycle()
    val pendingRestore by vm.shizukuPendingRestore.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LifecycleResumeEffect(Unit) {
        vm.refreshShizuku()
        onPauseOrDispose { }
    }

    val ready = shizukuState == ShizukuState.Ready
    val status = when {
        pendingRestore && !ready -> R.string.shizuku_status_pending_restore
        ready && degraded -> R.string.shizuku_status_degraded
        else -> when (shizukuState) {
            ShizukuState.NotInstalled -> R.string.shizuku_status_not_installed
            ShizukuState.NotRunning -> R.string.shizuku_status_not_running
            ShizukuState.TooOld -> R.string.shizuku_status_too_old
            ShizukuState.NoPermission -> R.string.shizuku_status_no_permission
            ShizukuState.Ready -> R.string.shizuku_status_ready
        }
    }
    val action: Pair<Int, () -> Unit>? = when {
        ready && degraded -> R.string.shizuku_action_retry to vm::retryShizuku
        shizukuState == ShizukuState.NotInstalled -> R.string.shizuku_action_get to { openShizukuDownload(context) }
        shizukuState == ShizukuState.NotRunning || shizukuState == ShizukuState.TooOld ->
            R.string.shizuku_action_open to { openShizukuApp(context) }
        shizukuState == ShizukuState.NoPermission -> R.string.shizuku_action_grant to vm::requestShizukuPermission
        else -> null
    }

    Column {
        SwitchRow(
            title = stringResource(R.string.shizuku_title),
            hint = stringResource(R.string.shizuku_hint),
            checked = enabled && ready,
            enabled = ready,
        ) { vm.setShizuku(it) }
        Row(
            Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                stringResource(status),
                style = MaterialTheme.typography.bodySmall,
                color = if (ready && !degraded) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f).padding(vertical = 12.dp),
            )
            action?.let { (label, onClick) ->
                TextButton(onClick = onClick) { Text(stringResource(label)) }
            }
        }
    }
}

/** Opens the Shizuku website in the browser (Florsent itself has no network access). */
private fun openShizukuDownload(context: Context) {
    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(ShizukuSaturation.DOWNLOAD_URL))) }
}

private fun openShizukuApp(context: Context) {
    val intent = context.packageManager.getLaunchIntentForPackage(ShizukuSaturation.MANAGER_PACKAGE) ?: return
    runCatching { context.startActivity(intent) }
}

/**
 * True-grayscale finish (Android 15+). Turning it on without notification-policy access explains
 * why the access is needed, sends the user to system settings, and completes on return.
 */
@Composable
private fun ZenGrayscaleRow(enabled: Boolean, vm: SettingsViewModel, onOpenAccessSettings: () -> Unit) {
    var hasAccess by remember { mutableStateOf(vm.hasZenAccess()) }
    var pendingEnable by rememberSaveable { mutableStateOf(false) }
    var showExplain by rememberSaveable { mutableStateOf(false) }

    LifecycleResumeEffect(Unit) {
        hasAccess = vm.hasZenAccess()
        if (pendingEnable) {
            pendingEnable = false
            if (hasAccess) vm.setZenGrayscale(true)
        }
        onPauseOrDispose { }
    }

    val effective = enabled && hasAccess
    SwitchRow(
        title = stringResource(R.string.settings_zen_grayscale),
        hint = stringResource(
            if (enabled && !hasAccess) R.string.settings_zen_grayscale_lost else R.string.settings_zen_grayscale_hint,
        ),
        checked = effective,
    ) { on ->
        when {
            !on -> vm.setZenGrayscale(false)
            hasAccess -> vm.setZenGrayscale(true)
            else -> showExplain = true
        }
    }

    if (showExplain) {
        AlertDialog(
            onDismissRequest = { showExplain = false },
            title = { Text(stringResource(R.string.zen_access_title)) },
            text = { Text(stringResource(R.string.zen_access_body)) },
            confirmButton = {
                TextButton(onClick = {
                    showExplain = false
                    pendingEnable = true
                    onOpenAccessSettings()
                }) { Text(stringResource(R.string.zen_access_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { showExplain = false }) { Text(stringResource(R.string.zen_access_cancel)) }
            },
        )
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 24.dp, bottom = 10.dp, start = 4.dp).semantics { heading() },
    )
}

@Composable
private fun CardDivider() {
    HorizontalDivider(Modifier.padding(horizontal = 20.dp), color = MaterialTheme.colorScheme.outlineVariant)
}

@Composable
private fun SwitchRow(
    title: String,
    hint: String?,
    checked: Boolean,
    enabled: Boolean = true,
    onChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(value = checked, enabled = enabled, role = Role.Switch, onValueChange = onChange)
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            if (hint != null) {
                Text(hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Switch(checked = checked, onCheckedChange = null, enabled = enabled)
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
    val valueText = stringResource(R.string.minutes_format, state.value.roundToInt())
    Column(Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(
                valueText,
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
            // TalkBack reads "單次額度, 15 分鐘" instead of a percentage.
            modifier = Modifier.semantics {
                contentDescription = title
                stateDescription = valueText
            },
            // The dense step dots add noise; the value label already shows the step.
            colors = SliderDefaults.colors(
                activeTickColor = Color.Transparent,
                inactiveTickColor = Color.Transparent,
            ),
        )
    }
}
