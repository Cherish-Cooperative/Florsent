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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.florsent.AppContainer
import app.florsent.domain.GuidanceEvent
import app.florsent.ui.apps.WatchedAppsScreen
import app.florsent.ui.apps.WatchedAppsViewModel
import app.florsent.ui.breathe.BreatheScreen
import app.florsent.ui.budget.BudgetScreen
import app.florsent.ui.budget.BudgetViewModel
import app.florsent.ui.home.HomeScreen
import app.florsent.ui.home.HomeViewModel

enum class Route { Home, Breathe, WatchedApps, Budget }

/**
 * Four screens do not justify a navigation library; a saveable enum plus BackHandler is enough.
 */
@Composable
fun FlorsentAppUi(
    container: AppContainer,
    requestedRoute: Route?,
    onRouteConsumed: () -> Unit,
    onStartPreview: () -> Unit,
    onOpenAccessibilitySettings: () -> Unit,
) {
    var route by rememberSaveable { mutableStateOf(Route.Home) }
    val guidance = container.guidance

    LaunchedEffect(requestedRoute) {
        if (requestedRoute != null) {
            route = requestedRoute
            onRouteConsumed()
        }
    }

    val leaveBreathe = {
        guidance.dispatch(GuidanceEvent.BreathingCancelled(guidance.now()))
        route = Route.Home
    }
    BackHandler(enabled = route != Route.Home) {
        if (route == Route.Breathe) leaveBreathe() else route = Route.Home
    }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        AnimatedContent(
            targetState = route,
            transitionSpec = { fadeIn(tween(260)) togetherWith fadeOut(tween(180)) },
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
                        onOpenBudget = { route = Route.Budget },
                        onAcceptDisclosure = vm::acceptDisclosure,
                        onOpenAccessibilitySettings = onOpenAccessibilitySettings,
                    )
                }
                Route.Breathe -> BreatheScreen(
                    onComplete = {
                        guidance.dispatch(GuidanceEvent.BreathingCompleted(guidance.now()))
                        route = Route.Home
                    },
                    onCancel = leaveBreathe,
                )
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
                Route.Budget -> {
                    val vm: BudgetViewModel =
                        viewModel(factory = viewModelFactory { initializer { BudgetViewModel(container) } })
                    val settings by vm.settings.collectAsStateWithLifecycle()
                    BudgetScreen(
                        settings = settings,
                        onBack = { route = Route.Home },
                        onSessionChange = { vm.setSession(it) },
                        onDailyChange = { vm.setDaily(it) },
                        onCooldownChange = { vm.setCooldown(it) },
                    )
                }
            }
        }
    }
}
