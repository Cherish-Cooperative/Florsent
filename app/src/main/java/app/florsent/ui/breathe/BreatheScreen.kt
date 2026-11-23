package app.florsent.ui.breathe

import android.os.Build
import android.view.HapticFeedbackConstants
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.LongState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.florsent.R
import app.florsent.domain.BreathPhaseKind
import app.florsent.domain.BreathingPattern
import app.florsent.domain.BreathingPatterns
import app.florsent.domain.BreathingTimeline
import app.florsent.ui.components.FlorsentTopBar
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first

@Composable
fun BreatheScreen(
    state: BreatheUiState,
    onBack: () -> Unit,
    onSelectPattern: (BreathingPattern) -> Unit,
    onAmbientChange: (Boolean) -> Unit,
    onCueChange: (Boolean) -> Unit,
    onStart: () -> Unit,
    onPhaseStarted: (BreathPhaseKind) -> Unit,
    onFinished: () -> Unit,
) {
    val reducedMotion = rememberReducedMotion()
    AnimatedContent(
        targetState = state.stage,
        transitionSpec = { fadeIn(tween(500)) togetherWith fadeOut(tween(300)) },
        label = "breathe-stage",
    ) { stage ->
        when (stage) {
            BreatheStage.Ready -> PatternPicker(
                state = state,
                reducedMotion = reducedMotion,
                onBack = onBack,
                onSelectPattern = onSelectPattern,
                onAmbientChange = onAmbientChange,
                onCueChange = onCueChange,
                onStart = onStart,
            )
            BreatheStage.Running -> BreathingSession(
                pattern = state.pattern,
                sessionId = state.sessionId,
                reducedMotion = reducedMotion,
                onPhaseStarted = onPhaseStarted,
                onFinished = onFinished,
            )
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Picker

@Composable
private fun PatternPicker(
    state: BreatheUiState,
    reducedMotion: Boolean,
    onBack: () -> Unit,
    onSelectPattern: (BreathingPattern) -> Unit,
    onAmbientChange: (Boolean) -> Unit,
    onCueChange: (Boolean) -> Unit,
    onStart: () -> Unit,
) {
    Scaffold(
        topBar = { FlorsentTopBar(stringResource(R.string.breathe_title), onBack) },
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            Column(
                Modifier
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(horizontal = 24.dp, vertical = 16.dp),
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(bottom = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
                ) {
                    SoundChip(stringResource(R.string.breathe_ambient_sound), state.ambientSound, onAmbientChange)
                    SoundChip(stringResource(R.string.breathe_cue_sound), state.cueSound, onCueChange)
                }
                Button(onClick = onStart, modifier = Modifier.fillMaxWidth().height(56.dp)) {
                    Text(stringResource(R.string.breathe_start), style = MaterialTheme.typography.titleMedium)
                }
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            val clock = rememberFrameClock(key = Unit, running = !reducedMotion)
            BreathingFlower(
                expansion = { if (reducedMotion) 0.45f else BreathingTimeline.idleExpansion(clock.longValue) },
                timeSeconds = { clock.longValue / 1000f },
                modifier = Modifier.size(200.dp),
            )
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.breathe_pick_rhythm),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(20.dp))
            Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                BreathingPatterns.all.forEach { pattern ->
                    PatternCard(pattern, selected = pattern.id == state.pattern.id) { onSelectPattern(pattern) }
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun PatternCard(pattern: BreathingPattern, selected: Boolean, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val (name, description) = patternTexts(pattern)
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = if (selected) scheme.primaryContainer.copy(alpha = 0.55f) else scheme.surfaceContainerLow,
        border = if (selected) BorderStroke(1.5.dp, scheme.primary.copy(alpha = 0.6f)) else null,
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick),
    ) {
        Column(Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Text(
                    pattern.rhythmLabel,
                    style = MaterialTheme.typography.labelLarge,
                    color = scheme.primary,
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                description,
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onSurfaceVariant,
            )
            Text(
                durationText(pattern),
                style = MaterialTheme.typography.bodySmall,
                color = scheme.outline,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}

@Composable
private fun SoundChip(label: String, selected: Boolean, onChange: (Boolean) -> Unit) {
    FilterChip(
        selected = selected,
        onClick = { onChange(!selected) },
        label = { Text(label) },
        shape = RoundedCornerShape(50),
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
        ),
    )
}

@Composable
private fun patternTexts(pattern: BreathingPattern): Pair<String, String> = when (pattern.id) {
    BreathingPatterns.Box.id -> stringResource(R.string.pattern_box) to stringResource(R.string.pattern_box_desc)
    BreathingPatterns.Relax478.id -> stringResource(R.string.pattern_478) to stringResource(R.string.pattern_478_desc)
    BreathingPatterns.Resonance.id ->
        stringResource(R.string.pattern_resonance) to stringResource(R.string.pattern_resonance_desc)
    else -> stringResource(R.string.pattern_equal) to stringResource(R.string.pattern_equal_desc)
}

@Composable
private fun durationText(pattern: BreathingPattern): String {
    val totalSeconds = (pattern.totalMs / 1000).toInt()
    return stringResource(R.string.pattern_duration, pattern.cycles, totalSeconds / 60, totalSeconds % 60)
}

// ---------------------------------------------------------------------------------------------
// Session

@Composable
private fun BreathingSession(
    pattern: BreathingPattern,
    sessionId: Int,
    reducedMotion: Boolean,
    onPhaseStarted: (BreathPhaseKind) -> Unit,
    onFinished: () -> Unit,
) {
    val clock = rememberFrameClock(key = sessionId, running = true)
    val display by remember(pattern, sessionId) {
        derivedStateOf { BreathingTimeline.displayAt(pattern, clock.longValue) }
    }
    val view = LocalView.current
    val latestOnPhase by rememberUpdatedState(onPhaseStarted)
    val latestOnFinished by rememberUpdatedState(onFinished)

    DisposableEffect(view) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }
    // Phase changes: gentle haptic tick + optional cue. Runs once per phase, not per frame.
    LaunchedEffect(pattern, sessionId) {
        snapshotFlow { display.cycleIndex to display.phaseIndex }
            .distinctUntilChanged()
            .collect {
                if (display.finished) return@collect
                view.performHapticFeedback(
                    if (Build.VERSION.SDK_INT >= 34) HapticFeedbackConstants.SEGMENT_TICK
                    else HapticFeedbackConstants.CLOCK_TICK,
                )
                latestOnPhase(display.kind)
            }
    }
    LaunchedEffect(pattern, sessionId) {
        snapshotFlow { display.finished }.first { it }
        latestOnFinished()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.weight(0.8f))
        Box(contentAlignment = Alignment.Center) {
            BreathingFlower(
                expansion = {
                    if (reducedMotion) 0.6f else BreathingTimeline.expansionAt(pattern, clock.longValue)
                },
                timeSeconds = { if (reducedMotion) 0f else clock.longValue / 1000f },
                progress = { BreathingTimeline.sessionProgress(pattern, clock.longValue) },
                modifier = Modifier.size(300.dp),
            )
        }
        Spacer(Modifier.height(40.dp))
        PhaseLabel(display.kind)
        Spacer(Modifier.height(4.dp))
        Text(
            if (display.finished) "" else "${display.secondsLeft}",
            style = MaterialTheme.typography.displaySmall,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            stringResource(R.string.breathe_cycle, display.cycleIndex + 1, pattern.cycles),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.weight(1f))
        TextButton(onClick = { latestOnFinished() }) {
            Text(stringResource(R.string.breathe_end_early), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun PhaseLabel(kind: BreathPhaseKind) {
    AnimatedContent(
        targetState = kind,
        transitionSpec = { fadeIn(tween(600)) togetherWith fadeOut(tween(400)) },
        label = "phase",
        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
    ) { phase ->
        Text(
            stringResource(
                when (phase) {
                    BreathPhaseKind.Inhale -> R.string.phase_inhale
                    BreathPhaseKind.HoldIn -> R.string.phase_hold_in
                    BreathPhaseKind.Exhale -> R.string.phase_exhale
                    BreathPhaseKind.HoldOut -> R.string.phase_hold_out
                },
            ),
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center,
        )
    }
}

// ---------------------------------------------------------------------------------------------
// Clock

private const val MAX_FRAME_GAP_NANOS = 250_000_000L

/**
 * Elapsed milliseconds on the monotonic frame clock (`withFrameNanos`), so the animation is in
 * lockstep with vsync. Gaps longer than 250 ms (app in background, screen off) are not counted,
 * which pauses the session instead of skipping ahead.
 */
@Composable
fun rememberFrameClock(key: Any?, running: Boolean): LongState {
    val elapsed = remember(key) { mutableLongStateOf(0L) }
    LaunchedEffect(key, running) {
        if (!running) return@LaunchedEffect
        var last = -1L
        var accumulated = elapsed.longValue * 1_000_000L
        while (true) {
            withFrameNanos { now ->
                if (last >= 0) {
                    val delta = now - last
                    if (delta in 0..MAX_FRAME_GAP_NANOS) accumulated += delta
                }
                last = now
                elapsed.longValue = accumulated / 1_000_000L
            }
        }
    }
    return elapsed
}
