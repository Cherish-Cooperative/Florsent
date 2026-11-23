package app.florsent.ui.breathe

import android.os.Build
import android.view.HapticFeedbackConstants
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.coerceAtMost
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import app.florsent.R
import app.florsent.domain.BreathDisplay
import app.florsent.domain.BreathPhaseKind
import app.florsent.domain.BreathingPattern
import app.florsent.domain.BreathingPatterns
import app.florsent.domain.BreathingTimeline
import app.florsent.ui.components.FlorsentTopBar
import kotlinx.coroutines.delay
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
    onWarmUpSound: () -> Unit,
    onBackground: () -> Unit,
    onForeground: () -> Unit,
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
                onWarmUpSound = onWarmUpSound,
            )
            BreatheStage.Running -> BreathingSession(
                pattern = state.pattern,
                sessionId = state.sessionId,
                reducedMotion = reducedMotion,
                onPhaseStarted = onPhaseStarted,
                onFinished = onFinished,
                onBackground = onBackground,
                onForeground = onForeground,
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
    onWarmUpSound: () -> Unit,
) {
    // Load the cue early so the very first "inhale" bell is not skipped while SoundPool decodes.
    LaunchedEffect(state.cueSound) { if (state.cueSound) onWarmUpSound() }
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
        val scroll = rememberScrollState()
        Box(Modifier.fillMaxSize().padding(padding)) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scroll)
                    .padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                val clock = rememberFrameClock(key = Unit, running = !reducedMotion, idle = true)
                BreathingFlower(
                    expansion = { if (reducedMotion) 0.45f else BreathingTimeline.idleExpansion(clock.longValue) },
                    timeSeconds = { clock.longValue / 1000f },
                    modifier = Modifier.size(140.dp),
                )
                Spacer(Modifier.height(16.dp))
                Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    BreathingPatterns.all.forEach { pattern ->
                        PatternCard(pattern, selected = pattern.id == state.pattern.id) { onSelectPattern(pattern) }
                    }
                }
                Spacer(Modifier.height(20.dp))
            }
            // Soft fade at the bottom edge, only while more content is below.
            val background = MaterialTheme.colorScheme.background
            if (scroll.canScrollForward) {
                Box(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(32.dp)
                        .background(Brush.verticalGradient(listOf(background.copy(alpha = 0f), background))),
                )
            }
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
        Column(Modifier.padding(horizontal = 20.dp, vertical = 13.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Text(
                    pattern.rhythmLabel,
                    style = MaterialTheme.typography.labelLarge,
                    // primary on the tinted selected card falls just under 4.5:1.
                    color = if (selected) scheme.onPrimaryContainer else scheme.primary,
                )
            }
            Spacer(Modifier.height(2.dp))
            Text(
                description,
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onSurfaceVariant,
            )
            Text(
                durationText(pattern),
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onSurfaceVariant,
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
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return if (seconds == 0) {
        stringResource(R.string.pattern_duration_even, pattern.cycles, minutes)
    } else {
        stringResource(R.string.pattern_duration, pattern.cycles, minutes, seconds)
    }
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
    onBackground: () -> Unit,
    onForeground: () -> Unit,
) {
    val clock = rememberFrameClock(key = sessionId, running = true)
    val display by remember(pattern, sessionId) {
        derivedStateOf { BreathingTimeline.displayAt(pattern, clock.longValue) }
    }
    val view = LocalView.current
    val latestOnPhase by rememberUpdatedState(onPhaseStarted)
    val latestOnFinished by rememberUpdatedState(onFinished)
    // Survives rotation so a restored session does not replay the current phase's cue.
    var lastCuedPhase by rememberSaveable(sessionId) { mutableStateOf(-1) }

    DisposableEffect(view) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }
    // The frame clock already pauses in the background; pause the ambient loop with it.
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { onBackground() }
    LifecycleEventEffect(Lifecycle.Event.ON_START) { onForeground() }

    // Phase changes: gentle haptic tick + optional cue. Runs once per phase, not per frame.
    LaunchedEffect(pattern, sessionId) {
        snapshotFlow { display.cycleIndex * PHASE_KEY_STRIDE + display.phaseIndex }
            .distinctUntilChanged()
            .collect { key ->
                if (display.finished || key == lastCuedPhase) return@collect
                lastCuedPhase = key
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

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        // Fits the flower to the space (landscape, small phones, large fonts); scrolls as a last resort.
        val flowerSize = (maxHeight * 0.38f).coerceAtMost(300.dp)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .heightIn(min = maxHeight)
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.weight(0.8f))
            BreathingFlower(
                expansion = {
                    if (reducedMotion) {
                        // No continuous motion: open while breathing in / holding, closed otherwise.
                        if (display.kind == BreathPhaseKind.Inhale || display.kind == BreathPhaseKind.HoldIn) 0.9f
                        else 0.35f
                    } else {
                        BreathingTimeline.expansionAt(pattern, clock.longValue)
                    }
                },
                // Petal drift runs on breathing time only, so the flower is completely still while holding.
                timeSeconds = { if (reducedMotion) 0f else BreathingTimeline.movingMsAt(pattern, clock.longValue) / 1000f },
                progress = { BreathingTimeline.sessionProgress(pattern, clock.longValue) },
                modifier = Modifier.size(flowerSize),
            )
            Spacer(Modifier.height(32.dp))
            PhaseLabel(display.kind)
            Spacer(Modifier.height(4.dp))
            SessionCounters(display, pattern.cycles)
            Spacer(Modifier.weight(1f))
            TextButton(
                onClick = { latestOnFinished() },
                modifier = Modifier.padding(top = 16.dp, bottom = 16.dp),
            ) {
                Text(stringResource(R.string.breathe_end_early), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** Seconds and cycle; its own scope so only this recomposes when the second changes. */
@Composable
private fun SessionCounters(display: BreathDisplay, cycles: Int) {
    val description = pluralStringResource(
        R.plurals.breathe_countdown_description, display.secondsLeft,
        display.secondsLeft, display.cycleIndex + 1, cycles,
    )
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        // One quiet node for TalkBack; not a live region, a per-second announcement would be noise.
        modifier = Modifier.clearAndSetSemantics { contentDescription = description },
    ) {
        Text(
            if (display.finished) "" else "${display.secondsLeft}",
            style = MaterialTheme.typography.displaySmall,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            stringResource(R.string.breathe_cycle, display.cycleIndex + 1, cycles),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun PhaseLabel(kind: BreathPhaseKind) {
    val label = stringResource(phaseLabel(kind))
    // The live region sits on a stable wrapper; the crossfading children are hidden from TalkBack,
    // otherwise both the old and new label could be read during the transition.
    Box(Modifier.semantics {
        liveRegion = LiveRegionMode.Polite
        contentDescription = label
    }) {
        AnimatedContent(
            targetState = kind,
            transitionSpec = { fadeIn(tween(600)) togetherWith fadeOut(tween(400)) },
            label = "phase",
            modifier = Modifier.clearAndSetSemantics {},
        ) { phase ->
            Text(
                stringResource(phaseLabel(phase)),
                style = MaterialTheme.typography.headlineMedium,
                textAlign = TextAlign.Center,
            )
        }
    }
}

private fun phaseLabel(kind: BreathPhaseKind): Int = when (kind) {
    BreathPhaseKind.Inhale -> R.string.phase_inhale
    BreathPhaseKind.HoldIn -> R.string.phase_hold_in
    BreathPhaseKind.Exhale -> R.string.phase_exhale
    BreathPhaseKind.HoldOut -> R.string.phase_hold_out
}

private const val PHASE_KEY_STRIDE = 16

// ---------------------------------------------------------------------------------------------
// Clock

private const val MAX_FRAME_GAP_NANOS = 250_000_000L
private const val NOMINAL_FRAME_NANOS = 16_666_667L
private const val IDLE_FRAME_INTERVAL_MS = 33L

/**
 * Elapsed milliseconds on the monotonic frame clock (`withFrameNanos`), so the animation is in
 * lockstep with vsync. The value is saveable, so a rotation resumes the session instead of restarting
 * it. A gap longer than 250 ms (app in background: the Recomposer pauses frames) counts as a single
 * nominal frame, which pauses the session instead of skipping ahead, while ordinary jank still counts.
 *
 * @param idle decorative use (idle flower): capped at about 30 fps to halve its frame cost.
 */
@Composable
fun rememberFrameClock(key: Any?, running: Boolean, idle: Boolean = false): LongState {
    val elapsed = rememberSaveable(key) { mutableLongStateOf(0L) }
    LaunchedEffect(key, running) {
        if (!running) return@LaunchedEffect
        var last = -1L
        var accumulated = elapsed.longValue * 1_000_000L
        while (true) {
            if (idle) delay(IDLE_FRAME_INTERVAL_MS)
            withFrameNanos { now ->
                if (last >= 0) {
                    val delta = now - last
                    accumulated += if (delta in 0..MAX_FRAME_GAP_NANOS) delta else NOMINAL_FRAME_NANOS
                }
                last = now
                elapsed.longValue = accumulated / 1_000_000L
            }
        }
    }
    return elapsed
}
