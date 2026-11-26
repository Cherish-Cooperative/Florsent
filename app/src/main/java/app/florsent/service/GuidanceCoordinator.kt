package app.florsent.service

import android.util.Log
import app.florsent.data.GuidanceStatePersistence
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
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Process-wide owner of the guidance state. The accessibility service feeds it events and renders
 * [effect]; the UI reads [state] and sends user actions. All mutation goes through [dispatch], which
 * runs the pure [GuidanceEngine].
 *
 * Power: there is no polling. Ticks are scheduled from [GuidanceEngine.nextTickDelayMs]: every
 * second only while the fade or preview is moving, otherwise at the next deadline (at most every
 * 15 s), and not at all with the screen off or an unwatched app in front.
 */
class GuidanceCoordinator(
    private val scope: CoroutineScope,
    val engine: GuidanceEngine,
    private val configFlow: Flow<GuidanceConfig>,
    private val store: GuidanceStatePersistence,
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
            try {
                // Wait for the real watch list too: events evaluated against the empty default
                // config would skip the "started counting" bookkeeping.
                configFlow.first()
                val persisted = store.load()
                lastPersisted = persisted.durable()
                _state.value = persisted.copy(
                    foregroundPackage = _state.value.foregroundPackage,
                    screenOn = _state.value.screenOn,
                )
            } catch (e: Exception) {
                Log.w(TAG, "Could not restore guidance state; starting fresh", e)
            } finally {
                loaded.complete(Unit)
            }
        }
        // Routine persistence: only when a durable field changed, at most every 30 s.
        scope.launch {
            loaded.await()
            _state.map { it.durable() }
                .distinctUntilChanged()
                .drop(1) // the value we just loaded
                .conflate()
                .collect { s ->
                    persist(s)
                    delay(PERSIST_INTERVAL_MS)
                }
        }
        // Tick scheduling: every state change (including the tick itself) re-arms the timer, so no
        // distinctUntilChanged here; two equal consecutive delays must still schedule two ticks.
        scope.launch {
            combine(_state, config) { s, c -> engine.nextTickDelayMs(s, c, clock()) }
                .collectLatest { delayMs ->
                    if (delayMs != null) {
                        delay(delayMs)
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
        val before = _state.value
        _state.value = engine.reduce(before, event, config)
        val after = _state.value
        if (isMilestone(before, after, config)) {
            scope.launch { persist(after.durable()) }
        }
    }

    /** Last state written by either path; identical states are never written twice. */
    @Volatile private var lastPersisted: GuidanceState? = null

    private suspend fun persist(durable: GuidanceState) {
        if (durable == lastPersisted) return
        store.save(durable)
        lastPersisted = durable
    }

    /**
     * Changes worth saving immediately rather than at the next 30 s window: mode switches, a new day,
     * and the moment counting stops (screen off / leaving the app), which is when the process is most
     * likely to be reclaimed.
     */
    private fun isMilestone(before: GuidanceState, after: GuidanceState, config: GuidanceConfig): Boolean =
        before.mode != after.mode ||
            before.epochDay != after.epochDay ||
            before.cooldownUntilMs != after.cooldownUntilMs ||
            (before.isCounting(config) && !after.isCounting(config))

    /** The persisted projection; transient fields are blanked so equality reflects what is stored. */
    private fun GuidanceState.durable() = copy(
        foregroundPackage = null,
        screenOn = true,
        lastTickAtMs = null,
        countingSinceMs = null,
        previewStartedAtMs = null,
        breathingFromPreview = false,
    )

    private companion object {
        const val TAG = "GuidanceCoordinator"
        const val PERSIST_INTERVAL_MS = 30_000L
    }
}
