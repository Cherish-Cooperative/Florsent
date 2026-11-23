package app.florsent

import android.app.Application
import android.content.Context
import app.florsent.data.GuidanceStateStore
import app.florsent.data.InstalledAppsRepository
import app.florsent.data.SettingsRepository
import app.florsent.data.UserSettings
import app.florsent.data.guidanceDataStore
import app.florsent.data.settingsDataStore
import app.florsent.domain.GuidanceEngine
import app.florsent.domain.SocialAppMatcher
import app.florsent.data.UsageLogStore
import app.florsent.service.GuidanceCoordinator
import app.florsent.service.UsageAnalysis
import app.florsent.service.UsageRecording
import java.io.File
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import app.florsent.service.shizuku.PlatformShizukuClient
import app.florsent.service.shizuku.ShizukuSaturation
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class FlorsentApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}

/** Manual dependency container; small enough that a DI framework would be overhead. */
class AppContainer(context: Context) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    val settings = SettingsRepository(context.settingsDataStore)

    /** Latest settings for synchronous reads (e.g. effect controllers); defaults until loaded. */
    val currentSettings: StateFlow<UserSettings> =
        settings.settings.stateIn(scope, SharingStarted.Eagerly, UserSettings())
    val installedApps = InstalledAppsRepository(context)

    /** Optional third visual layer; inert unless the user installs Shizuku and turns it on. */
    val shizuku: ShizukuSaturation = PlatformShizukuClient.saturation(context)
    val guidance = GuidanceCoordinator(
        scope = scope,
        engine = GuidanceEngine(),
        configFlow = settings.settings.map { it.toGuidanceConfig() },
        store = GuidanceStateStore(context.guidanceDataStore),
        clock = System::currentTimeMillis,
    )

    /** 30-day on-device usage log (only written with the user's consent). */
    val usageLog = UsageLogStore(File(context.filesDir, "usage"))
    val recording = UsageRecording(usageLog, scope)
    val analysis = UsageAnalysis(usageLog, clock = guidance::now)

    init {
        scope.launch {
            // Only launchable apps (a disabled official app must not be watched instead of the
            // client actually in use), matched by package or brand label.
            val launchable = installedApps.launchableLabels()
            settings.initializeWatchedIfNeeded(SocialAppMatcher.suggest(launchable))
            settings.repairWatchedOnce { SocialAppMatcher.repair(it, launchable) }
        }
        // Recording follows the real (loaded) settings, the service and the guidance state; event
        // driven, so it adds no wakeups of its own.
        scope.launch {
            combine(guidance.state, guidance.config, guidance.serviceRunning, settings.settings) { s, c, running, user ->
                UsageRecording.Input(
                    recording = running && user.recordingAllowed,
                    screenInUse = s.screenOn,
                    watchedApp = if (s.isCounting(c)) s.foregroundPackage else null,
                )
            }.distinctUntilChanged().collect { recording.submit(it, guidance.now()) }
        }
        scope.launch { usageLog.prune(guidance.now() / 1000) }
    }

    /** "Clear usage history": deletes the log and the learned results. */
    fun clearUsageHistory() {
        val s = guidance.state.value
        val c = guidance.config.value
        val user = currentSettings.value
        recording.clearHistory(
            guidance.now(),
            UsageRecording.Input(
                recording = guidance.serviceRunning.value && user.recordingAllowed,
                screenInUse = s.screenOn,
                watchedApp = if (s.isCounting(c)) s.foregroundPackage else null,
            ),
        )
        analysis.invalidate()
    }
}

val Context.appContainer: AppContainer
    get() = (applicationContext as FlorsentApp).container
