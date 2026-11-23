package app.florsent.ui.breathe

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.florsent.data.SettingsRepository
import app.florsent.domain.BreathPhaseKind
import app.florsent.domain.BreathingPatterns
import app.florsent.service.audio.BreathingSound
import app.florsent.testing.testPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class)
class BreatheViewModelTest {

    @get:Rule val tmp = TemporaryFolder()

    private class FakeSound : BreathingSound {
        val events = mutableListOf<String>()
        override fun startAmbient() { events += "start" }
        override fun stopAmbient() { events += "stop" }
        override fun pauseAmbient() { events += "pause" }
        override fun resumeAmbient() { events += "resume" }
        override fun playCue(kind: BreathPhaseKind) { events += "cue:$kind" }
        override fun release() { events += "release" }
    }

    private val dispatcher = UnconfinedTestDispatcher()
    private val sound = FakeSound()
    private var soundsCreated = 0
    private val store = ViewModelStore()
    private lateinit var settings: SettingsRepository
    private lateinit var vm: BreatheViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        settings = SettingsRepository(testPreferences(tmp.root, TestScope(dispatcher + Job())))
        val factory = viewModelFactory {
            initializer {
                BreatheViewModel(settings) {
                    soundsCreated++
                    sound
                }
            }
        }
        vm = ViewModelProvider.create(store, factory)[BreatheViewModel::class]
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `a silent session never creates the sound engine`() = runTest(dispatcher) {
        vm.start()
        vm.onPhaseStarted(0, 0, BreathPhaseKind.Inhale)
        vm.onBackground()
        vm.stop()
        store.clear()
        assertEquals(0, soundsCreated)
        assertTrue(sound.events.isEmpty())
    }

    @Test
    fun `starting with ambient on plays it, stopping fades it out`() = runTest(dispatcher) {
        settings.setAmbientSound(true)
        vm.start()
        assertEquals(BreatheStage.Running, vm.uiState.value.stage)
        vm.stop()
        assertEquals(BreatheStage.Ready, vm.uiState.value.stage)
        assertEquals(listOf("start", "stop"), sound.events)
    }

    @Test
    fun `cues play only when the cue sound is on`() = runTest(dispatcher) {
        vm.start()
        vm.onPhaseStarted(0, 0, BreathPhaseKind.Inhale)
        settings.setCueSound(true)
        vm.onPhaseStarted(0, 2, BreathPhaseKind.Exhale)
        assertEquals(listOf("cue:Exhale"), sound.events)
    }

    @Test
    fun `a phase reported twice rings once, and an old phase never rings again`() = runTest(dispatcher) {
        settings.setCueSound(true)
        vm.start()
        assertTrue(vm.onPhaseStarted(0, 0, BreathPhaseKind.Inhale))
        assertFalse("same phase again", vm.onPhaseStarted(0, 0, BreathPhaseKind.Inhale))
        assertTrue(vm.onPhaseStarted(0, 1, BreathPhaseKind.HoldIn))
        assertFalse("clock stepped back", vm.onPhaseStarted(0, 0, BreathPhaseKind.Inhale))
        assertFalse(vm.onPhaseStarted(0, 1, BreathPhaseKind.HoldIn))
        assertTrue(vm.onPhaseStarted(1, 0, BreathPhaseKind.Inhale))
        assertEquals(listOf("cue:Inhale", "cue:HoldIn", "cue:Inhale"), sound.events)
    }

    @Test
    fun `a new session starts cueing from the first phase again`() = runTest(dispatcher) {
        settings.setCueSound(true)
        vm.start()
        vm.onPhaseStarted(0, 0, BreathPhaseKind.Inhale)
        vm.onPhaseStarted(0, 1, BreathPhaseKind.HoldIn)
        vm.stop()
        vm.start()
        assertTrue(vm.onPhaseStarted(0, 0, BreathPhaseKind.Inhale))
        assertEquals(listOf("cue:Inhale", "cue:HoldIn", "stop", "cue:Inhale"), sound.events)
    }

    @Test
    fun `background pauses and foreground resumes only a running ambient session`() = runTest(dispatcher) {
        settings.setAmbientSound(true)
        vm.start()
        vm.onBackground()
        vm.onForeground()
        vm.stop()
        vm.onForeground() // not running any more
        assertEquals(listOf("start", "pause", "resume", "stop"), sound.events)
    }

    @Test
    fun `toggling ambient during a session starts and stops it`() = runTest(dispatcher) {
        vm.start()
        vm.setAmbientSound(true)
        vm.setAmbientSound(false)
        assertEquals(listOf("start", "stop"), sound.events)
    }

    @Test
    fun `each start is a new session`() = runTest(dispatcher) {
        vm.start()
        val first = vm.uiState.value.sessionId
        vm.stop()
        vm.start()
        assertEquals(first + 1, vm.uiState.value.sessionId)
    }

    @Test
    fun `the chosen pattern is remembered`() = runTest(dispatcher) {
        vm.selectPattern(BreathingPatterns.Relax478)
        assertEquals(BreathingPatterns.Relax478, vm.uiState.value.pattern)
    }

    @Test
    fun `clearing the screen releases the sound engine it created`() = runTest(dispatcher) {
        vm.warmUpSound()
        store.clear()
        assertEquals(1, soundsCreated)
        assertEquals(listOf("release"), sound.events)
    }
}
