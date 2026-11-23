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
import androidx.compose.foundation.layout.size
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.florsent.R
import app.florsent.domain.GuidanceStatus
import app.florsent.ui.breathe.BreathingFlower
import java.time.LocalTime
import androidx.compose.foundation.clickable
import androidx.compose.material3.Icon
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import app.florsent.ui.components.BedtimePickerDialog
import app.florsent.ui.components.CardShape
import app.florsent.ui.components.clockText
import app.florsent.ui.components.NavigationRow
import app.florsent.ui.components.SoftCard
import app.florsent.ui.theme.FlorsentTheme

@Composable
fun HomeScreen(
    state: HomeUiState,
    onBreathe: () -> Unit,
    onPreview: () -> Unit,
    onOpenInsights: () -> Unit,
    onOpenSettings: () -> Unit,
    onAcceptDisclosure: () -> Unit,
    onOpenAccessibilitySettings: () -> Unit,
    onAnswerConsent: (Boolean) -> Unit,
    onAcceptInvite: (Int) -> Unit,
    onDeclineInvite: () -> Unit,
) {
    var showDisclosure by rememberSaveable { mutableStateOf(false) }
    var pickingBedtime by rememberSaveable { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(horizontal = 24.dp),
    ) {
        Spacer(Modifier.height(36.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.app_name),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.height(6.dp))
                Text(greeting(), style = MaterialTheme.typography.headlineMedium)
            }
            BreathingFlower(expansion = { 0.7f }, timeSeconds = { 0f }, modifier = Modifier.size(76.dp))
        }
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.home_tagline),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(28.dp))

        if (state.loaded && !state.serviceRunning) {
            // The disclosure was already accepted (onboarding or earlier): go straight to settings.
            SetupCard(onEnable = {
                if (state.disclosureAccepted) onOpenAccessibilitySettings() else showDisclosure = true
            })
        } else {
            TodayCard(state, onOpenInsights)
        }
        // At most one gentle card at a time: consent first (existing users), then the invitation.
        if (state.askConsent) {
            Spacer(Modifier.height(12.dp))
            ChoiceCard(
                title = stringResource(R.string.consent_title),
                body = stringResource(R.string.consent_body),
                primary = stringResource(R.string.consent_accept) to { onAnswerConsent(true) },
                secondary = stringResource(R.string.consent_decline) to { onAnswerConsent(false) },
            )
        } else {
            state.invite?.let { invite ->
                Spacer(Modifier.height(12.dp))
                when (invite) {
                    is BedtimeInvite.Learned -> ChoiceCard(
                        title = stringResource(R.string.invite_title),
                        body = stringResource(R.string.invite_body, clockText(invite.minuteOfDay)),
                        primary = stringResource(R.string.invite_accept) to { onAcceptInvite(invite.minuteOfDay) },
                        secondary = stringResource(R.string.invite_choose) to { pickingBedtime = true },
                        tertiary = stringResource(R.string.invite_decline) to onDeclineInvite,
                    )
                    BedtimeInvite.ChooseTime -> ChoiceCard(
                        title = stringResource(R.string.invite_title),
                        body = stringResource(R.string.invite_irregular_body),
                        primary = stringResource(R.string.invite_irregular_choose) to { pickingBedtime = true },
                        secondary = stringResource(R.string.invite_decline) to onDeclineInvite,
                    )
                }
            }
        }

        Spacer(Modifier.height(24.dp))
        Button(
            onClick = onBreathe,
            modifier = Modifier.fillMaxWidth().height(56.dp),
        ) {
            Text(stringResource(R.string.home_breathe_now), style = MaterialTheme.typography.titleMedium)
        }
        // The preview needs the service; until then the setup card is the one call to action.
        if (state.serviceRunning) {
            Spacer(Modifier.height(12.dp))
            FilledTonalButton(onClick = onPreview, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                Text(stringResource(R.string.home_preview))
            }
        }

        Spacer(Modifier.height(28.dp))
        SoftCard {
            NavigationRow(
                title = stringResource(R.string.home_settings_row),
                value = stringResource(R.string.home_settings_row_value),
                onClick = onOpenSettings,
            )
        }
        Spacer(Modifier.height(32.dp))
    }

    if (pickingBedtime) {
        BedtimePickerDialog(
            initialMinuteOfDay = (state.invite as? BedtimeInvite.Learned)?.minuteOfDay ?: (23 * 60),
            onConfirm = {
                pickingBedtime = false
                onAcceptInvite(it)
            },
            onDismiss = { pickingBedtime = false },
        )
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
private fun greeting(): String {
    val hour = remember { LocalTime.now().hour }
    return stringResource(
        when (hour) {
            in 5..10 -> R.string.greeting_morning
            in 11..16 -> R.string.greeting_afternoon
            in 17..22 -> R.string.greeting_evening
            else -> R.string.greeting_night
        },
    )
}

/** A quiet one-time question with up to three answers. */
@Composable
private fun ChoiceCard(
    title: String,
    body: String,
    primary: Pair<String, () -> Unit>,
    secondary: Pair<String, () -> Unit>,
    tertiary: Pair<String, () -> Unit>? = null,
) {
    SoftCard {
        Column(Modifier.padding(20.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
            Spacer(Modifier.height(6.dp))
            Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Button(onClick = primary.second) { Text(primary.first) }
                Spacer(Modifier.size(8.dp))
                TextButton(onClick = secondary.second) { Text(secondary.first) }
                tertiary?.let { (label, action) ->
                    TextButton(onClick = action) {
                        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun SetupCard(onEnable: () -> Unit) {
    SoftCard {
        Column(Modifier.padding(24.dp)) {
            Text(
                stringResource(R.string.setup_title),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.semantics { heading() },
            )
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
private fun TodayCard(state: HomeUiState, onOpenInsights: () -> Unit) {
    val status = statusText(state)
    val summary = stringResource(R.string.today_summary, state.todayUsedMinutes, state.dailyBudgetMinutes, status)
    val openLabel = stringResource(R.string.home_open_insights)
    SoftCard(
        Modifier
            .clip(CardShape)
            .clickable(role = Role.Button, onClickLabel = openLabel, onClick = onOpenInsights),
    ) {
        // One TalkBack node instead of "23", "/ 60 分鐘", "38%" fragments.
        Column(Modifier.padding(24.dp).semantics(mergeDescendants = true) { contentDescription = summary }) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.today_label),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    painterResource(R.drawable.ic_chevron_right),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
            }
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
                status,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            state.bedtimeHintMinute?.let {
                Spacer(Modifier.height(6.dp))
                Text(
                    stringResource(R.string.home_bedtime_hint, clockText(it)),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

@Composable
private fun statusText(state: HomeUiState): String = when (state.status) {
    GuidanceStatus.Idle ->
        stringResource(if (state.watchedCount == 0) R.string.status_no_apps else R.string.status_idle)
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
            onBreathe = {}, onPreview = {}, onOpenInsights = {}, onOpenSettings = {},
            onAcceptDisclosure = {}, onOpenAccessibilitySettings = {},
            onAnswerConsent = {}, onAcceptInvite = {}, onDeclineInvite = {},
        )
    }
}
