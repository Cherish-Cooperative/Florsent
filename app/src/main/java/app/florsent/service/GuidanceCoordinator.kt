package app.florsent.service

import app.florsent.data.GuidanceStateStore
import app.florsent.domain.GuidanceConfig
import app.florsent.domain.GuidanceEffect
import app.florsent.domain.GuidanceEngine
import app.florsent.domain.GuidanceEvent
import app.florsent.domain.GuidanceState
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Process-wide owner of the guidance state. The accessibility service feeds it events and renders
 * [effect]; the UI reads [state] and sends user actions. All mutation goes through [dispatch], which
 * runs the pure [GuidanceEngine].
 */
class GuidanceCoordinator(
    private val scope: CoroutineScope,
    val engine: GuidanceEngine,
    configFlow: Flow<GuidanceConfig>,
    private val store: GuidanceStateStore,
    private val clock: () -> Long,
) {
    val config: StateFlow<GuidanceConfig> =
        configFlow.stateIn(scope, SharingStarted.Eagerly, GuidanceConfig())

    private val _state = MutableStateFlow(GuidanceState())
    val state: StateFlow<GuidanceState> = _state.asStateFlow()

    private val _serviceRunning = MutableStateFlow(false)
    val serviceRunning: StateFlow<Boolean> = _serviceRunning.asStateFlow()

    val effect: StateFlow<GuidanceEffect> = combine(_state, config) { s, c -> engine.effect(s, c, clock()) }
        .distinctUntilChanged()
        .stateIn(scope, SharingStarted.Eagerly, GuidanceEffect.None)

    private val loaded = CompletableDeferred<Unit>()

    init {
        scope.launch {
            val persisted = store.load()
            _state.update { live ->
                persisted.copy(foregroundPackage = live.foregroundPackage, screenOn = live.screenOn)
            }
            loaded.complete(Unit)
        }
        // Persist at most every few seconds; StateFlow conflation hands us the latest value.
        scope.launch {
            loaded.await()
            _state.collect { s ->
                store.save(s)
                delay(PERSIST_INTERVAL_MS)
            }
        }
        // Tick only while something is time-dependent (watched app in front, or preview running).
        scope.launch {
            combine(_state, config) { s, c -> engine.needsTick(s, c) }
                .distinctUntilChanged()
                .collectLatest { needsTick ->
                    while (needsTick) {
                        delay(TICK_INTERVAL_MS)
                        dispatch(GuidanceEvent.Tick(clock()))
                    }
                }
        }
    }

    fun now(): Long = clock()

    fun dispatch(event: GuidanceEvent) {
        if (loaded.isCompleted) {
            apply(event)
        } else {
            scope.launch {
                loaded.await()
                apply(event)
            }
        }
    }

    fun setServiceRunning(running: Boolean) {
        _serviceRunning.value = running
    }

    private fun apply(event: GuidanceEvent) {
        val config = config.value
        _state.update { engine.reduce(it, event, config) }
    }

    private companion object {
        const val TICK_INTERVAL_MS = 1_000L
        const val PERSIST_INTERVAL_MS = 5_000L
    }
}
