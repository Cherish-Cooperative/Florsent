package app.florsent.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.florsent.AppContainer
import app.florsent.domain.GuidanceEvent
import app.florsent.domain.GuidanceStatus
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class HomeUiState(
    val loaded: Boolean = false,
    val serviceRunning: Boolean = false,
    val disclosureAccepted: Boolean = false,
    val todayUsedMinutes: Int = 0,
    val dailyBudgetMinutes: Int = 0,
    val sessionBudgetMinutes: Int = 0,
    val watchedCount: Int = 0,
    val status: GuidanceStatus = GuidanceStatus.Idle,
    val cooldownRemainingMinutes: Int = 0,
) {
    val todayProgress: Float
        get() = if (dailyBudgetMinutes <= 0) 0f else (todayUsedMinutes.toFloat() / dailyBudgetMinutes).coerceIn(0f, 1f)
}

class HomeViewModel(private val container: AppContainer) : ViewModel() {

    private val guidance = container.guidance

    /** Refreshes time-derived values (cooldown countdown) while the app sits in front. */
    private val refresh = flow {
        while (true) {
            emit(Unit)
            delay(15_000)
        }
    }

    val uiState: StateFlow<HomeUiState> = combine(
        guidance.state,
        guidance.config,
        guidance.serviceRunning,
        container.settings.settings,
        refresh,
    ) { state, config, running, settings, _ ->
        val now = guidance.now()
        val status = guidance.engine.status(state, config, now)
        HomeUiState(
            loaded = true,
            serviceRunning = running,
            disclosureAccepted = settings.disclosureAccepted,
            todayUsedMinutes = (guidance.engine.dailyUsedMs(state, now) / 60_000).toInt(),
            dailyBudgetMinutes = settings.dailyBudgetMinutes,
            sessionBudgetMinutes = settings.sessionBudgetMinutes,
            watchedCount = settings.watchedPackages.size,
            status = status,
            cooldownRemainingMinutes = (status as? GuidanceStatus.Cooldown)
                ?.let { ((it.untilMs - now + 59_999) / 60_000).toInt() } ?: 0,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    fun startPreview() {
        guidance.dispatch(GuidanceEvent.PreviewStarted(guidance.now()))
    }

    fun acceptDisclosure() {
        viewModelScope.launch { container.settings.setDisclosureAccepted(true) }
    }
}
