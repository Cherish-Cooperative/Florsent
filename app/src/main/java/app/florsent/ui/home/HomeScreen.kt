package app.florsent.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.florsent.R
import app.florsent.domain.GuidanceStatus
import app.florsent.ui.components.NavigationRow
import app.florsent.ui.components.SoftCard
import app.florsent.ui.theme.FlorsentTheme

@Composable
fun HomeScreen(
    state: HomeUiState,
    onBreathe: () -> Unit,
    onPreview: () -> Unit,
    onOpenWatchedApps: () -> Unit,
    onOpenBudget: () -> Unit,
    onAcceptDisclosure: () -> Unit,
    onOpenAccessibilitySettings: () -> Unit,
) {
    var showDisclosure by rememberSaveable { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(horizontal = 24.dp),
    ) {
        Spacer(Modifier.height(40.dp))
        Text(
            stringResource(R.string.app_name),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            stringResource(R.string.home_tagline),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(32.dp))

        if (state.loaded && !state.serviceRunning) {
            SetupCard(onEnable = { showDisclosure = true })
        } else {
            TodayCard(state)
        }

        Spacer(Modifier.height(24.dp))
        Button(
            onClick = onBreathe,
            modifier = Modifier.fillMaxWidth().height(56.dp),
        ) {
            Text(stringResource(R.string.home_breathe_now), style = MaterialTheme.typography.titleMedium)
        }
        Spacer(Modifier.height(12.dp))
        FilledTonalButton(
            onClick = onPreview,
            enabled = state.serviceRunning,
            modifier = Modifier.fillMaxWidth().height(52.dp),
        ) {
            Text(stringResource(R.string.home_preview))
        }
        if (state.loaded && !state.serviceRunning) {
            Text(
                stringResource(R.string.home_preview_needs_service),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )
        }

        Spacer(Modifier.height(28.dp))
        SoftCard {
            Column {
                NavigationRow(
                    title = stringResource(R.string.home_watched_apps),
                    value = stringResource(R.string.home_watched_apps_value, state.watchedCount),
                    onClick = onOpenWatchedApps,
                )
                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 20.dp),
                    color = MaterialTheme.colorScheme.outlineVariant,
                )
                NavigationRow(
                    title = stringResource(R.string.home_budget),
                    value = stringResource(
                        R.string.home_budget_value,
                        state.sessionBudgetMinutes,
                        state.dailyBudgetMinutes,
                    ),
                    onClick = onOpenBudget,
                )
            }
        }
        Spacer(Modifier.height(32.dp))
    }

    if (showDisclosure) {
        AlertDialog(
            onDismissRequest = { showDisclosure = false },
            title = { Text(stringResource(R.string.disclosure_title)) },
            text = { Text(stringResource(R.string.disclosure_body)) },
            confirmButton = {
                TextButton(onClick = {
                    showDisclosure = false
                    onAcceptDisclosure()
                    onOpenAccessibilitySettings()
                }) { Text(stringResource(R.string.disclosure_accept)) }
            },
            dismissButton = {
                TextButton(onClick = { showDisclosure = false }) {
                    Text(stringResource(R.string.disclosure_decline))
                }
            },
        )
    }
}

@Composable
private fun SetupCard(onEnable: () -> Unit) {
    SoftCard {
        Column(Modifier.padding(24.dp)) {
            Text(stringResource(R.string.setup_title), style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.setup_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(20.dp))
            Button(onClick = onEnable) { Text(stringResource(R.string.setup_action)) }
        }
    }
}

@Composable
private fun TodayCard(state: HomeUiState) {
    SoftCard {
        Column(Modifier.padding(24.dp)) {
            Text(
                stringResource(R.string.today_label),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("${state.todayUsedMinutes}", style = MaterialTheme.typography.displaySmall)
                Text(
                    stringResource(R.string.today_of_budget, state.dailyBudgetMinutes),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 6.dp),
                )
            }
            Spacer(Modifier.height(16.dp))
            LinearProgressIndicator(
                progress = { state.todayProgress },
                modifier = Modifier.fillMaxWidth().height(6.dp),
                trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                strokeCap = StrokeCap.Round,
                gapSize = 0.dp,
                drawStopIndicator = {},
            )
            Spacer(Modifier.height(16.dp))
            Text(
                statusText(state),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun statusText(state: HomeUiState): String = when (state.status) {
    GuidanceStatus.Idle -> stringResource(R.string.status_idle)
    GuidanceStatus.Guiding -> stringResource(R.string.status_guiding)
    GuidanceStatus.Breathing -> stringResource(R.string.status_breathing)
    GuidanceStatus.Preview -> stringResource(R.string.status_preview)
    is GuidanceStatus.Cooldown -> stringResource(R.string.status_cooldown, state.cooldownRemainingMinutes)
}

@Preview(showBackground = true)
@Composable
private fun HomePreview() {
    FlorsentTheme {
        HomeScreen(
            state = HomeUiState(
                loaded = true, serviceRunning = true, todayUsedMinutes = 23, dailyBudgetMinutes = 60,
                sessionBudgetMinutes = 15, watchedCount = 4,
            ),
            onBreathe = {}, onPreview = {}, onOpenWatchedApps = {}, onOpenBudget = {},
            onAcceptDisclosure = {}, onOpenAccessibilitySettings = {},
        )
    }
}
