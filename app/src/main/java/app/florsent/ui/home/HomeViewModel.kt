package app.florsent.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.florsent.AppContainer
import app.florsent.data.UsageConsent
import app.florsent.data.UserSettings
import app.florsent.domain.BedtimeLearner
import app.florsent.domain.BedtimeLearning
import app.florsent.domain.GuidanceEvent
import app.florsent.domain.GuidanceStatus
import app.florsent.service.UsageAnalysisResult
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** The one-time bedtime invitation shown on the home screen. */
sealed interface BedtimeInvite {
    /** A habit is visible; offer to nudge before it. */
    data class Learned(val minuteOfDay: Int) : BedtimeInvite

    /** A week of data and no stable pattern; offer to pick a time. */
    data object ChooseTime : BedtimeInvite
}

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
    /** Existing users are asked once whether to keep usage history. */
    val askConsent: Boolean = false,
    val invite: BedtimeInvite? = null,
    /** Local start of tonight's window, shown only in the 3 hours before it. */
    val bedtimeHintMinute: Int? = null,
) {
    val todayProgress: Float
        get() = if (dailyBudgetMinutes <= 0) 0f else (todayUsedMinutes.toFloat() / dailyBudgetMinutes).coerceIn(0f, 1f)
}

class HomeViewModel(private val container: AppContainer) : ViewModel() {

    private val guidance = container.guidance

    /** Refreshes time-derived values (cooldown countdown, bedtime hint) while the app is in front. */
    private val refresh = flow {
        while (true) {
            emit(Unit)
            delay(15_000)
        }
    }

    private val base = combine(
        guidance.state,
        guidance.config,
        guidance.serviceRunning,
        container.settings.settings,
        refresh,
    ) { state, config, running, settings, _ ->
        val now = guidance.now()
        val status = guidance.engine.status(state, config, now)
        val hint = guidance.engine.nextBedtimeWindow(now, config)
            ?.takeIf { it.startMs - now <= HINT_LEAD_MS }
            ?.let { Instant.ofEpochMilli(it.startMs).atZone(ZoneId.systemDefault()).let { t -> t.hour * 60 + t.minute } }
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
            askConsent = running && settings.usageConsent == UsageConsent.Unknown,
            bedtimeHintMinute = hint,
        ) to settings
    }

    val uiState: StateFlow<HomeUiState> =
        combine(base, container.analysis.result) { (ui, settings), analysis ->
            ui.copy(invite = invite(settings, analysis))
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    init {
        viewModelScope.launch { container.analysis.refresh() }
    }

    fun startPreview() {
        guidance.dispatch(GuidanceEvent.PreviewStarted(guidance.now()))
    }

    fun acceptDisclosure() {
        viewModelScope.launch {
            container.settings.setDisclosureAccepted(true)
            container.settings.setUsageConsent(UsageConsent.Granted)
        }
    }

    fun answerConsent(granted: Boolean) {
        viewModelScope.launch {
            container.settings.setUsageConsent(if (granted) UsageConsent.Granted else UsageConsent.Denied)
        }
    }

    fun acceptInvite(minuteOfDay: Int) {
        viewModelScope.launch { container.settings.setBedtimeAnchor(minuteOfDay) }
    }

    fun declineInvite() {
        viewModelScope.launch { container.settings.dismissBedtimeInvite() }
    }

    private companion object {
        const val HINT_LEAD_MS = 3 * 60 * 60_000L

        /** Shown once: only with consent, no bedtime yet, not answered before, and something to say. */
        fun invite(settings: UserSettings, analysis: UsageAnalysisResult?): BedtimeInvite? {
            if (!settings.recordingAllowed || settings.bedtimeAnchorMinute != null || settings.bedtimeInviteAnswered) {
                return null
            }
            return when (val learning = analysis?.learning) {
                is BedtimeLearning.Confident -> BedtimeInvite.Learned(BedtimeLearner.roundTo5(learning.minuteOfDay))
                is BedtimeLearning.Irregular -> BedtimeInvite.ChooseTime
                else -> null
            }
        }
    }
}
