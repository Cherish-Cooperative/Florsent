package app.florsent.ui.breathe

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.florsent.data.SettingsRepository
import app.florsent.domain.BreathPhaseKind
import app.florsent.domain.PhaseCueGate
import app.florsent.domain.BreathingPattern
import app.florsent.domain.BreathingPatterns
import app.florsent.service.audio.BreathingSound
import app.florsent.util.DebugLog
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class BreatheStage { Ready, Running }

data class BreatheUiState(
    val pattern: BreathingPattern = BreathingPatterns.default,
    val stage: BreatheStage = BreatheStage.Ready,
    /** Increments per started session so the clock restarts even with the same pattern. */
    val sessionId: Int = 0,
    val ambientSound: Boolean = false,
    val cueSound: Boolean = false,
)

class BreatheViewModel(
    private val settings: SettingsRepository,
    soundFactory: () -> BreathingSound,
) : ViewModel() {

    // Built on first use, so opening the app never loads SoundPool or decodes audio.
    private val soundDelegate = lazy(LazyThreadSafetyMode.NONE, soundFactory)
    private val sound by soundDelegate

    private val stage = MutableStateFlow(BreatheStage.Ready)
    private val sessionId = MutableStateFlow(0)
    private val cueGate = PhaseCueGate()

    /** A pattern for this session only (bedtime uses 4-7-8) without changing the saved choice. */
    private val sessionPattern = MutableStateFlow<BreathingPattern?>(null)

    val uiState: StateFlow<BreatheUiState> =
        combine(settings.settings, stage, sessionId, sessionPattern) { settings, stage, session, override ->
            BreatheUiState(
                pattern = override ?: BreathingPatterns.byId(settings.breathingPatternId),
                stage = stage,
                sessionId = session,
                ambientSound = settings.ambientSound,
                cueSound = settings.cueSound,
            )
        }.stateIn(viewModelScope, SharingStarted.Eagerly, BreatheUiState())

    fun selectPattern(pattern: BreathingPattern) {
        sessionPattern.value = null
        viewModelScope.launch { settings.setBreathingPattern(pattern.id) }
    }

    fun setAmbientSound(on: Boolean) {
        viewModelScope.launch { settings.setAmbientSound(on) }
        if (stage.value == BreatheStage.Running) {
            if (on) sound.startAmbient() else if (soundDelegate.isInitialized()) sound.stopAmbient()
        }
    }

    fun setCueSound(on: Boolean) {
        viewModelScope.launch { settings.setCueSound(on) }
    }

    fun start(patternForThisSession: BreathingPattern? = null) {
        sessionPattern.value = patternForThisSession
        sessionId.value += 1
        stage.value = BreatheStage.Running
        DebugLog.d { "breathing start ambient=${uiState.value.ambientSound}" }
        if (uiState.value.ambientSound) sound.startAmbient()
    }

    /** Touch the soundscape so SoundPool has decoded the cue before the first phase. */
    fun warmUpSound() {
        sound
    }

    fun onBackground() {
        if (soundDelegate.isInitialized()) sound.pauseAmbient()
    }

    fun onForeground() {
        if (stage.value == BreatheStage.Running && uiState.value.ambientSound) sound.resumeAmbient()
    }

    /**
     * A new phase began on screen. Returns true if this phase gets its cue (haptic + chime): each
     * (session, cycle, phase) is cued at most once and only moving forward, whatever the UI reports.
     */
    fun onPhaseStarted(cycleIndex: Int, phaseIndex: Int, kind: BreathPhaseKind): Boolean {
        if (!cueGate.tryCue(sessionId.value, cycleIndex, phaseIndex)) {
            DebugLog.d { "cue skipped (duplicate) session=${sessionId.value} cycle=$cycleIndex phase=$phaseIndex" }
            return false
        }
        DebugLog.d { "cue session=${sessionId.value} cycle=$cycleIndex phase=$phaseIndex $kind sound=${uiState.value.cueSound}" }
        if (uiState.value.cueSound) sound.playCue(kind)
        return true
    }

    /** Ends a session (finished, ended early or abandoned) and returns to the picker. */
    fun stop() {
        stage.value = BreatheStage.Ready
        sessionPattern.value = null
        if (soundDelegate.isInitialized()) sound.stopAmbient()
    }

    override fun onCleared() {
        if (soundDelegate.isInitialized()) sound.release()
    }
}
