package app.florsent.ui.insights

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.florsent.AppContainer
import app.florsent.R
import app.florsent.domain.DayUsage
import app.florsent.domain.Insight
import app.florsent.domain.UsageSummary
import app.florsent.ui.apps.AppIcon
import app.florsent.ui.components.FlorsentTopBar
import app.florsent.ui.components.SoftCard
import app.florsent.ui.components.clockText
import app.florsent.ui.components.durationText
import java.time.DayOfWeek
import java.time.format.TextStyle
import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class InsightsUiState(
    val recordingAllowed: Boolean = true,
    val summary: UsageSummary? = null,
    val labels: Map<String, String> = emptyMap(),
)

class InsightsViewModel(private val container: AppContainer) : ViewModel() {
    private val labels = MutableStateFlow<Map<String, String>>(emptyMap())

    val uiState: StateFlow<InsightsUiState> =
        combine(container.settings.settings, container.analysis.result, labels, container.guidance.state) { s, analysis, names, state ->
            // Today's total comes from the engine (it counts all of today, the log only since consent),
            // so the home card and this page always agree.
            val engineToday = (container.guidance.engine.dailyUsedMs(state, container.guidance.now()) / 60_000).toInt()
            val summary = analysis?.summary?.let { sum ->
                val today = sum.today.copy(minutes = maxOf(engineToday, sum.today.minutes ?: 0))
                sum.copy(today = today, lastSevenDays = sum.lastSevenDays.dropLast(1) + today)
            }
            InsightsUiState(s.recordingAllowed, summary, names)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), InsightsUiState())

    init {
        viewModelScope.launch { container.analysis.refresh(force = true) }
        viewModelScope.launch {
            labels.value = container.installedApps.launchableApps().associate { it.packageName to it.label }
        }
    }

    suspend fun icon(packageName: String, sizePx: Int): Bitmap? = container.installedApps.icon(packageName, sizePx)
}

/** Calm, factual, no red, no percentages, no streaks. */
@Composable
fun InsightsScreen(state: InsightsUiState, onBack: () -> Unit, loadIcon: suspend (String, Int) -> Bitmap?) {
    Scaffold(
        topBar = { FlorsentTopBar(stringResource(R.string.insights_title), onBack) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
        ) {
            if (!state.recordingAllowed) {
                Text(
                    stringResource(R.string.insights_recording_off),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 16.dp),
                )
                return@Column
            }
            val summary = state.summary ?: return@Column
            Spacer(Modifier.height(8.dp))
            summary.insights.forEachIndexed { i, insight ->
                Text(
                    insightText(insight),
                    style = if (i == 0) MaterialTheme.typography.titleLarge else MaterialTheme.typography.bodyLarge,
                    color = if (i == 0) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
            }
            Spacer(Modifier.height(16.dp))
            TodayCard(summary.today)
            Spacer(Modifier.height(12.dp))
            WeekCard(summary.lastSevenDays)
            if (summary.topApps.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                TopAppsCard(summary.topApps, state.labels, loadIcon)
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun CardTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.semantics { heading() },
    )
}

@Composable
private fun TodayCard(today: DayUsage) {
    SoftCard {
        Column(Modifier.padding(20.dp)) {
            CardTitle(stringResource(R.string.insights_today))
            Spacer(Modifier.height(6.dp))
            Text(
                today.minutes?.let { durationText(it) } ?: stringResource(R.string.insights_no_record_day),
                style = MaterialTheme.typography.headlineMedium,
            )
        }
    }
}

@Composable
private fun WeekCard(days: List<DayUsage>) {
    val scheme = MaterialTheme.colorScheme
    val maxMinutes = (days.mapNotNull { it.minutes }.maxOrNull() ?: 0).coerceAtLeast(1)
    val labels = days.mapIndexed { i, d -> if (i == days.lastIndex) stringResource(R.string.insights_today) else weekday(d.date.dayOfWeek) }
    val description = days.mapIndexed { i, d ->
        stringResource(
            R.string.insights_bar_description, labels[i],
            d.minutes?.let { durationText(it) } ?: stringResource(R.string.insights_no_record_day),
        )
    }.joinToString("；")
    SoftCard {
        Column(Modifier.padding(20.dp)) {
            CardTitle(stringResource(R.string.insights_last_seven))
            Spacer(Modifier.height(16.dp))
            Canvas(
                Modifier
                    .fillMaxWidth()
                    .height(96.dp)
                    .clearAndSetSemantics { contentDescription = description },
            ) {
                val slot = size.width / days.size
                val barWidth = slot * 0.46f
                val radius = CornerRadius(barWidth / 2, barWidth / 2)
                val dash = PathEffect.dashPathEffect(floatArrayOf(6f, 6f))
                days.forEachIndexed { i, d ->
                    val left = slot * i + (slot - barWidth) / 2
                    val minutes = d.minutes
                    if (minutes == null) {
                        // No record that day: an outline, never a zero.
                        val h = size.height * 0.18f
                        drawRoundRect(
                            scheme.outline, topLeft = Offset(left, size.height - h), size = Size(barWidth, h),
                            cornerRadius = radius, style = Stroke(width = 1.5f, pathEffect = dash),
                        )
                    } else {
                        val h = (size.height * minutes / maxMinutes).coerceAtLeast(barWidth)
                        drawRoundRect(
                            if (i == days.lastIndex) scheme.primary else scheme.primary.copy(alpha = 0.4f),
                            topLeft = Offset(left, size.height - h), size = Size(barWidth, h), cornerRadius = radius,
                        )
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth()) {
                labels.forEach {
                    Text(
                        it,
                        style = MaterialTheme.typography.labelSmall,
                        color = scheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f).clearAndSetSemantics { },
                    )
                }
            }
        }
    }
}

@Composable
private fun TopAppsCard(
    apps: List<Pair<String, Int>>,
    labels: Map<String, String>,
    loadIcon: suspend (String, Int) -> Bitmap?,
) {
    SoftCard {
        Column(Modifier.padding(20.dp)) {
            CardTitle(stringResource(R.string.insights_top_apps))
            Spacer(Modifier.height(8.dp))
            apps.forEach { (pkg, minutes) ->
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 8.dp).semantics(mergeDescendants = true) {},
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    AppIcon(pkg, loadIcon)
                    Text(labels[pkg] ?: pkg, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    Text(durationText(minutes), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
fun insightText(insight: Insight): String = when (insight) {
    is Insight.PutsPhoneDownAround -> stringResource(R.string.insight_puts_down, clockText(insight.minuteOfDay))
    is Insight.LessThanLastWeek -> stringResource(R.string.insight_less, durationText(insight.minutes))
    Insight.MoreThanLastWeek -> stringResource(R.string.insight_more)
    Insight.SimilarToLastWeek -> stringResource(R.string.insight_similar)
    Insight.NotEnoughData -> stringResource(R.string.insight_not_enough)
}

private fun weekday(day: DayOfWeek): String = day.getDisplayName(TextStyle.NARROW, Locale.getDefault())
