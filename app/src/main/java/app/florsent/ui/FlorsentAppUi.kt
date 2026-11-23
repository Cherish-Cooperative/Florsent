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
import app.florsent.service.effect.ZenGrayscale
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
import app.florsent.ui.settings.SettingsScreen
import app.florsent.ui.settings.SettingsViewModel

enum class Route { Home, Breathe, Grounding, WatchedApps, Settings }

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
    onGoToLauncher: () -> Unit,
) {
    var route by rememberSaveable { mutableStateOf(Route.Home) }
    val guidance = container.guidance
    val context = LocalContext.current
    val breatheVm: BreatheViewModel = viewModel(
        factory = viewModelFactory {
            initializer {
                val appContext = context.applicationContext
                BreatheViewModel(container) { BreathingSoundscape(appContext) }
            }
        },
    )
    val loadedSettings by container.settings.settings.collectAsStateWithLifecycle(initialValue = null)
    val settings = loadedSettings ?: UserSettings()

    LaunchedEffect(requestedRoute) {
        if (requestedRoute != null) {
            route = requestedRoute
            if (requestedRoute == Route.Breathe && breatheVm.uiState.value.stage == BreatheStage.Ready) {
                breatheVm.start()
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
    val returnToLife = { goToLauncher: Boolean ->
        guidance.dispatch(GuidanceEvent.BreathingCompleted(guidance.now()))
        route = Route.Home
        if (goToLauncher) onGoToLauncher()
    }

    BackHandler(enabled = route != Route.Home) {
        when (route) {
            Route.Breathe ->
                if (breatheVm.uiState.value.stage == BreatheStage.Running) leaveBreathing() else route = Route.Home
            Route.Grounding -> returnToLife(false)
            else -> route = Route.Home
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
                        onBreathe = { route = Route.Breathe },
                        onPreview = {
                            vm.startPreview()
                            onStartPreview()
                        },
                        onOpenWatchedApps = { route = Route.WatchedApps },
                        onOpenSettings = { route = Route.Settings },
                        onAcceptDisclosure = vm::acceptDisclosure,
                        onOpenAccessibilitySettings = onOpenAccessibilitySettings,
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
                    GroundingScreen(
                        cooldownMinutes = if (startsCooldown) settings.cooldownMinutes else null,
                        onReturnToLife = { returnToLife(settings.returnToHomeScreen) },
                    )
                }
                Route.WatchedApps -> {
                    val vm: WatchedAppsViewModel =
                        viewModel(factory = viewModelFactory { initializer { WatchedAppsViewModel(container) } })
                    val state by vm.uiState.collectAsStateWithLifecycle()
                    WatchedAppsScreen(
                        state = state,
                        onBack = { route = Route.Home },
                        onToggle = vm::setWatched,
                        loadIcon = vm::icon,
                    )
                }
                Route.Settings -> {
                    val vm: SettingsViewModel =
                        viewModel(factory = viewModelFactory {
                            initializer { SettingsViewModel(container, context.applicationContext) }
                        })
                    val state by vm.uiState.collectAsStateWithLifecycle()
                    SettingsScreen(
                        state = state,
                        vm = vm,
                        onBack = { route = Route.Home },
                        onOpenAccessibilitySettings = onOpenAccessibilitySettings,
                        onOpenZenAccessSettings = { context.startActivity(ZenGrayscale.accessSettingsIntent()) },
                    )
                }
            }
        }
    }
    }
}
