package app.florsent.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.florsent.AppContainer
import app.florsent.data.UserSettings
import app.florsent.domain.GuidanceEvent
import app.florsent.service.audio.BreathingSoundscape
import app.florsent.util.SystemSettings
import app.florsent.ui.apps.LocalIconPeek
import app.florsent.ui.apps.WatchedAppsScreen
import app.florsent.ui.apps.WatchedAppsViewModel
import app.florsent.ui.breathe.BreatheScreen
import app.florsent.ui.breathe.BreatheStage
import app.florsent.ui.breathe.BreatheViewModel
import app.florsent.ui.grounding.GroundingScreen
import app.florsent.ui.home.HomeScreen
import app.florsent.ui.home.HomeViewModel
import app.florsent.ui.onboarding.OnboardingScreen
import app.florsent.ui.onboarding.OnboardingViewModel
import app.florsent.ui.settings.AdvancedSettingsScreen
import app.florsent.ui.settings.BedtimeSettingsScreen
import app.florsent.ui.settings.BedtimeViewModel
import app.florsent.ui.settings.BreathingSettingsScreen
import app.florsent.ui.settings.BudgetSettingsScreen
import app.florsent.ui.settings.SettingsHomeScreen
import app.florsent.ui.settings.SettingsPage
import app.florsent.ui.insights.InsightsScreen
import app.florsent.ui.insights.InsightsViewModel
import app.florsent.domain.BreathingPatterns
import app.florsent.domain.GuidanceContext
import app.florsent.ui.settings.SettingsViewModel

enum class Route(val parent: Route? = null) {
    Home,
    Breathe(Home),
    Grounding(Home),
    Insights(Home),
    Settings(Home),
    WatchedApps(Settings),
    SettingsBudget(Settings),
    SettingsBedtime(Settings),
    SettingsBreathing(Settings),
    SettingsAdvanced(Settings),
}

/**
 * Five screens do not justify a navigation library; a saveable enum plus BackHandler is enough.
 *
 * @param requestedRoute a route requested from outside (the nudge); [Route.Breathe] from there
 *   starts the session right away so the user is one tap from breathing.
 */
@Composable
fun FlorsentAppUi(
    container: AppContainer,
    requestedRoute: Route?,
    onRouteConsumed: () -> Unit,
    onStartPreview: () -> Unit,
    onOpenAccessibilitySettings: () -> Unit,
    onLeavePhone: () -> Unit,
) {
    var route by rememberSaveable { mutableStateOf(Route.Home) }
    val guidance = container.guidance
    val context = LocalContext.current
    val breatheVm: BreatheViewModel = viewModel(
        factory = viewModelFactory {
            initializer {
                val appContext = context.applicationContext
                BreatheViewModel(container.settings) { BreathingSoundscape(appContext) }
            }
        },
    )
    val loadedSettings by container.settings.settings.collectAsStateWithLifecycle(initialValue = null)
    val settings = loadedSettings ?: UserSettings()

    // Whether the current breathing / grounding came from a bedtime nudge (frozen for the session).
    var bedtimeSession by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(requestedRoute) {
        if (requestedRoute != null) {
            route = requestedRoute
            if (requestedRoute == Route.Breathe && breatheVm.uiState.value.stage == BreatheStage.Ready) {
                bedtimeSession = guidance.state.value.context == GuidanceContext.Bedtime
                // Bedtime breathing defaults to 4-7-8 for this session only.
                breatheVm.start(if (bedtimeSession) BreathingPatterns.Relax478 else null)
            }
            onRouteConsumed()
        }
    }

    val leaveBreathing = {
        breatheVm.stop()
        guidance.dispatch(GuidanceEvent.BreathingCancelled(guidance.now()))
        route = Route.Home
    }
    val finishBreathing = {
        if (route == Route.Breathe) {
            breatheVm.stop()
            route = Route.Grounding
        }
    }
    val returnToLife = { leavePhone: Boolean ->
        guidance.dispatch(GuidanceEvent.BreathingCompleted(guidance.now()))
        route = Route.Home
        // "去休息吧" always turns the screen off; daytime follows the user's setting.
        if (leavePhone || bedtimeSession) onLeavePhone()
        bedtimeSession = false
    }

    BackHandler(enabled = route != Route.Home) {
        when (route) {
            Route.Breathe ->
                if (breatheVm.uiState.value.stage == BreatheStage.Running) leaveBreathing() else route = Route.Home
            Route.Grounding -> returnToLife(false)
            else -> route = route.parent ?: Route.Home
        }
    }

    CompositionLocalProvider(LocalIconPeek provides container.installedApps::peekIcon) {
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        // Wait for settings so first-run users never see a flash of the home screen (or vice versa).
        val current = loadedSettings ?: return@Surface
        if (!current.onboardingCompleted) {
            val vm: OnboardingViewModel =
                viewModel(factory = viewModelFactory { initializer { OnboardingViewModel(container) } })
            val state by vm.uiState.collectAsStateWithLifecycle()
            OnboardingScreen(
                state = state,
                onToggleApp = vm::setWatched,
                loadIcon = vm::icon,
                onEnableService = {
                    vm.acceptDisclosure()
                    onOpenAccessibilitySettings()
                },
                onFinish = vm::complete,
            )
            return@Surface
        }
        AnimatedContent(
            targetState = route,
            transitionSpec = { fadeIn(tween(320)) togetherWith fadeOut(tween(200)) },
            label = "route",
        ) { current ->
            when (current) {
                Route.Home -> {
                    val vm: HomeViewModel = viewModel(factory = viewModelFactory { initializer { HomeViewModel(container) } })
                    val state by vm.uiState.collectAsStateWithLifecycle()
                    HomeScreen(
                        state = state,
                        onBreathe = {
                            bedtimeSession = false
                            route = Route.Breathe
                        },
                        onPreview = {
                            vm.startPreview()
                            onStartPreview()
                        },
                        onOpenInsights = { route = Route.Insights },
                        onOpenSettings = { route = Route.Settings },
                        onAcceptDisclosure = vm::acceptDisclosure,
                        onOpenAccessibilitySettings = onOpenAccessibilitySettings,
                        onAnswerConsent = vm::answerConsent,
                        onAcceptInvite = vm::acceptInvite,
                        onDeclineInvite = vm::declineInvite,
                    )
                }
                Route.Breathe -> {
                    val state by breatheVm.uiState.collectAsStateWithLifecycle()
                    BreatheScreen(
                        state = state,
                        onBack = { route = Route.Home },
                        onSelectPattern = breatheVm::selectPattern,
                        onAmbientChange = breatheVm::setAmbientSound,
                        onCueChange = breatheVm::setCueSound,
                        onStart = breatheVm::start,
                        onPhaseStarted = breatheVm::onPhaseStarted,
                        onFinished = finishBreathing,
                        onWarmUpSound = breatheVm::warmUpSound,
                        onBackground = breatheVm::onBackground,
                        onForeground = breatheVm::onForeground,
                    )
                }
                Route.Grounding -> {
                    val startsCooldownFlow = remember {
                        guidance.state.map { guidance.engine.completionStartsCooldown(it) }.distinctUntilChanged()
                    }
                    val startsCooldown by startsCooldownFlow.collectAsStateWithLifecycle(initialValue = false)
                    val bedtimeQuiet = (guidance.config.value.bedtime?.cooldownMs ?: 0L) / 60_000
                    val quietMinutes = if (bedtimeSession) bedtimeQuiet.toInt() else settings.cooldownMinutes
                    GroundingScreen(
                        cooldownMinutes = if (startsCooldown) quietMinutes else null,
                        bedtime = bedtimeSession,
                        onReturnToLife = { returnToLife(settings.returnToHomeScreen) },
                    )
                }
                Route.WatchedApps -> {
                    val vm: WatchedAppsViewModel =
                        viewModel(factory = viewModelFactory { initializer { WatchedAppsViewModel(container) } })
                    val state by vm.uiState.collectAsStateWithLifecycle()
                    WatchedAppsScreen(
                        state = state,
                        onBack = { route = Route.Settings },
                        onToggle = vm::setWatched,
                        loadIcon = vm::icon,
                    )
                }
                Route.Insights -> {
                    val vm: InsightsViewModel =
                        viewModel(factory = viewModelFactory { initializer { InsightsViewModel(container) } })
                    val state by vm.uiState.collectAsStateWithLifecycle()
                    InsightsScreen(state = state, onBack = { route = Route.Home }, loadIcon = vm::icon)
                }
                Route.SettingsBedtime -> {
                    val vm: BedtimeViewModel =
                        viewModel(factory = viewModelFactory { initializer { BedtimeViewModel(container) } })
                    val state by vm.uiState.collectAsStateWithLifecycle()
                    BedtimeSettingsScreen(state = state, vm = vm, onBack = { route = Route.Settings })
                }
                Route.Settings, Route.SettingsBudget, Route.SettingsBreathing, Route.SettingsAdvanced -> {
                    val vm: SettingsViewModel =
                        viewModel(factory = viewModelFactory {
                            initializer { SettingsViewModel(container, context.applicationContext) }
                        })
                    val state by vm.uiState.collectAsStateWithLifecycle()
                    val back = { route = current.parent ?: Route.Home }
                    when (current) {
                        Route.SettingsBudget -> BudgetSettingsScreen(state, vm, back)
                        Route.SettingsBreathing -> BreathingSettingsScreen(state, vm, back)
                        Route.SettingsAdvanced -> AdvancedSettingsScreen(
                            state = state,
                            vm = vm,
                            onBack = back,
                            onOpenAccessibilitySettings = onOpenAccessibilitySettings,
                            onOpenZenAccessSettings = { SystemSettings.openZenAccess(context) },
                        )
                        else -> SettingsHomeScreen(
                            state = state,
                            onBack = back,
                            onOpenWatchedApps = { route = Route.WatchedApps },
                            onOpen = { page ->
                                route = when (page) {
                                    SettingsPage.Budget -> Route.SettingsBudget
                                    SettingsPage.Bedtime -> Route.SettingsBedtime
                                    SettingsPage.Breathing -> Route.SettingsBreathing
                                    SettingsPage.Advanced -> Route.SettingsAdvanced
                                }
                            },
                        )
                    }
                }
            }
        }
    }
    }
}
