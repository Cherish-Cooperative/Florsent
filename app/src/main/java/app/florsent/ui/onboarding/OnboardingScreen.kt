package app.florsent.ui.onboarding

import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.florsent.AppContainer
import app.florsent.R
import app.florsent.domain.BreathingTimeline
import app.florsent.domain.AppLabel
import app.florsent.domain.SocialAppMatcher
import app.florsent.data.InstalledApp
import app.florsent.data.UsageConsent
import app.florsent.ui.apps.AppRow
import app.florsent.ui.apps.AppToggleRow
import app.florsent.ui.breathe.BreathingFlower
import app.florsent.ui.breathe.rememberFrameClock
import app.florsent.ui.breathe.rememberReducedMotion
import app.florsent.ui.components.SoftCard
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class OnboardingUiState(
    /** Installed feed-style social apps, or null while loading. */
    val socialApps: List<AppRow>? = null,
    val serviceRunning: Boolean = false,
)

class OnboardingViewModel(private val container: AppContainer) : ViewModel() {
    private val installed = MutableStateFlow<List<InstalledApp>?>(null)

    init {
        viewModelScope.launch {
            installed.value = container.installedApps.launchableApps()
                .filter { SocialAppMatcher.brandOf(AppLabel(it.packageName, it.label)) != null }
        }
    }

    val uiState: StateFlow<OnboardingUiState> = combine(
        installed, container.settings.settings, container.guidance.serviceRunning,
    ) { apps, settings, running ->
        OnboardingUiState(
            socialApps = apps?.map { AppRow(it.packageName, it.label, it.packageName in settings.watchedPackages) },
            serviceRunning = running,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), OnboardingUiState())

    fun setWatched(packageName: String, watched: Boolean) {
        viewModelScope.launch { container.settings.setWatched(packageName, watched) }
    }

    suspend fun icon(packageName: String, sizePx: Int): Bitmap? = container.installedApps.icon(packageName, sizePx)

    /** The disclosure covers both the service and the 30-day history, so it grants both. */
    fun acceptDisclosure() {
        viewModelScope.launch {
            container.settings.setDisclosureAccepted(true)
            container.settings.setUsageConsent(UsageConsent.Granted)
        }
    }

    fun complete() {
        viewModelScope.launch { container.settings.setOnboardingCompleted() }
    }
}

private const val PAGES = 3

/**
 * First run: purpose → pick apps → accessibility disclosure. The disclosure page is the in-app
 * "prominent disclosure" required by Google Play before sending the user to accessibility settings.
 */
@Composable
fun OnboardingScreen(
    state: OnboardingUiState,
    onToggleApp: (String, Boolean) -> Unit,
    loadIcon: suspend (String, Int) -> Bitmap?,
    onEnableService: () -> Unit,
    onFinish: () -> Unit,
) {
    var page by rememberSaveable { mutableIntStateOf(0) }
    BackHandler(enabled = page > 0) { page-- }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(horizontal = 28.dp),
    ) {
        AnimatedContent(
            targetState = page,
            transitionSpec = {
                val forward = targetState > initialState
                (slideInHorizontally(tween(420)) { if (forward) it / 6 else -it / 6 } + fadeIn(tween(420))) togetherWith
                    (slideOutHorizontally(tween(260)) { if (forward) -it / 6 else it / 6 } + fadeOut(tween(260)))
            },
            label = "onboarding",
            modifier = Modifier.weight(1f),
        ) { p ->
            when (p) {
                0 -> PurposePage()
                1 -> AppsPage(state.socialApps, onToggleApp, loadIcon)
                else -> DisclosurePage(state.serviceRunning)
            }
        }

        PageDots(current = page, modifier = Modifier.align(Alignment.CenterHorizontally).padding(vertical = 16.dp))

        val primaryLabel = when {
            page == 0 -> R.string.onboarding_begin
            page == 1 -> R.string.onboarding_next
            state.serviceRunning -> R.string.onboarding_start_using
            else -> R.string.onboarding_enable
        }
        Button(
            onClick = {
                when {
                    page < PAGES - 1 -> page++
                    state.serviceRunning -> onFinish()
                    else -> onEnableService()
                }
            },
            modifier = Modifier.fillMaxWidth().height(56.dp),
        ) { Text(stringResource(primaryLabel), style = MaterialTheme.typography.titleMedium) }

        Box(Modifier.fillMaxWidth().height(56.dp), contentAlignment = Alignment.Center) {
            if (page == PAGES - 1 && !state.serviceRunning) {
                TextButton(onClick = onFinish) {
                    Text(stringResource(R.string.onboarding_later), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun PurposePage() {
    val reducedMotion = rememberReducedMotion()
    val clock = rememberFrameClock(key = Unit, running = !reducedMotion, idle = true)
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(40.dp))
        BreathingFlower(
            expansion = { if (reducedMotion) 0.45f else BreathingTimeline.idleExpansion(clock.longValue) },
            timeSeconds = { clock.longValue / 1000f },
            modifier = Modifier.size(200.dp),
        )
        Spacer(Modifier.height(24.dp))
        Text(
            stringResource(R.string.app_name),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(10.dp))
        Text(
            stringResource(R.string.onboarding_purpose_title),
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
            modifier = Modifier.semantics { heading() },
        )
        Spacer(Modifier.height(32.dp))
        Column(verticalArrangement = Arrangement.spacedBy(18.dp), modifier = Modifier.fillMaxWidth()) {
            Step(1, stringResource(R.string.onboarding_step_fade))
            Step(2, stringResource(R.string.onboarding_step_nudge))
            Step(3, stringResource(R.string.onboarding_step_breathe))
        }
    }
}

@Composable
private fun Step(number: Int, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(32.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "$number",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
        Spacer(Modifier.width(16.dp))
        Text(text, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun AppsPage(
    apps: List<AppRow>?,
    onToggle: (String, Boolean) -> Unit,
    loadIcon: suspend (String, Int) -> Bitmap?,
) {
    Column(Modifier.fillMaxSize()) {
        Spacer(Modifier.height(40.dp))
        Text(
            stringResource(R.string.onboarding_apps_title),
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.semantics { heading() },
        )
        Spacer(Modifier.height(10.dp))
        Text(
            stringResource(R.string.onboarding_apps_body),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(20.dp))
        when {
            apps == null -> Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            apps.isEmpty() -> SoftCard {
                Text(
                    stringResource(R.string.onboarding_apps_none),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(20.dp),
                )
            }
            else -> SoftCard {
                LazyColumn(Modifier.padding(vertical = 6.dp)) {
                    items(apps, key = { it.packageName }) { AppToggleRow(it, onToggle, loadIcon) }
                }
            }
        }
    }
}

@Composable
private fun DisclosurePage(serviceRunning: Boolean) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Spacer(Modifier.height(40.dp))
        Text(
            stringResource(R.string.onboarding_disclosure_title),
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.semantics { heading() },
        )
        Spacer(Modifier.height(10.dp))
        Text(
            stringResource(R.string.onboarding_disclosure_body),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(20.dp))
        PromiseCard(
            title = stringResource(R.string.onboarding_does_title),
            items = listOf(
                stringResource(R.string.onboarding_does_1),
                stringResource(R.string.onboarding_does_2),
                stringResource(R.string.onboarding_does_3),
                stringResource(R.string.onboarding_does_4),
            ),
        )
        Spacer(Modifier.height(12.dp))
        PromiseCard(
            title = stringResource(R.string.onboarding_doesnt_title),
            items = listOf(
                stringResource(R.string.onboarding_doesnt_1),
                stringResource(R.string.onboarding_doesnt_2),
                stringResource(R.string.onboarding_doesnt_3),
            ),
        )
        Spacer(Modifier.height(20.dp))
        Text(
            stringResource(if (serviceRunning) R.string.onboarding_service_ready else R.string.onboarding_enable_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = if (serviceRunning) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun PromiseCard(title: String, items: List<String>) {
    SoftCard {
        Column(Modifier.padding(horizontal = 20.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
            items.forEach { item ->
                Row {
                    Text(
                        "·",
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.width(16.dp).clearAndSetSemantics {},
                    )
                    Text(item, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun PageDots(current: Int, modifier: Modifier = Modifier) {
    val description = stringResource(R.string.onboarding_page_description, current + 1, PAGES)
    Row(
        modifier.clearAndSetSemantics { contentDescription = description },
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        repeat(PAGES) { i ->
            Box(
                Modifier
                    .size(width = if (i == current) 20.dp else 6.dp, height = 6.dp)
                    .clip(CircleShape)
                    .background(
                        if (i == current) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.outline,
                    ),
            )
        }
    }
}
