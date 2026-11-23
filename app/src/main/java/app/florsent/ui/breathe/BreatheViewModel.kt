package app.florsent.ui.breathe

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.florsent.AppContainer
import app.florsent.domain.BreathPhaseKind
import app.florsent.domain.BreathingPattern
import app.florsent.domain.BreathingPatterns
import app.florsent.service.audio.BreathingSoundscape
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
    private val container: AppContainer,
    soundFactory: () -> BreathingSoundscape,
) : ViewModel() {

    // Built on first use, so opening the app never loads SoundPool or decodes audio.
    private val soundDelegate = lazy(LazyThreadSafetyMode.NONE, soundFactory)
    private val sound by soundDelegate

    private val stage = MutableStateFlow(BreatheStage.Ready)
    private val sessionId = MutableStateFlow(0)

    val uiState: StateFlow<BreatheUiState> =
        combine(container.settings.settings, stage, sessionId) { settings, stage, session ->
            BreatheUiState(
                pattern = BreathingPatterns.byId(settings.breathingPatternId),
                stage = stage,
                sessionId = session,
                ambientSound = settings.ambientSound,
                cueSound = settings.cueSound,
            )
        }.stateIn(viewModelScope, SharingStarted.Eagerly, BreatheUiState())

    fun selectPattern(pattern: BreathingPattern) {
        viewModelScope.launch { container.settings.setBreathingPattern(pattern.id) }
    }

    fun setAmbientSound(on: Boolean) {
        viewModelScope.launch { container.settings.setAmbientSound(on) }
        if (stage.value == BreatheStage.Running) {
            if (on) sound.startAmbient() else if (soundDelegate.isInitialized()) sound.stopAmbient()
        }
    }

    fun setCueSound(on: Boolean) {
        viewModelScope.launch { container.settings.setCueSound(on) }
    }

    fun start() {
        sessionId.value += 1
        stage.value = BreatheStage.Running
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

    fun onPhaseStarted(kind: BreathPhaseKind) {
        if (uiState.value.cueSound) sound.playCue(kind)
    }

    /** Ends a session (finished, ended early or abandoned) and returns to the picker. */
    fun stop() {
        stage.value = BreatheStage.Ready
        if (soundDelegate.isInitialized()) sound.stopAmbient()
    }

    override fun onCleared() {
        if (soundDelegate.isInitialized()) sound.release()
    }
}
