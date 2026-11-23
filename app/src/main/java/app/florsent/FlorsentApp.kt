package app.florsent

import android.app.Application
import android.content.Context
import app.florsent.data.GuidanceStateStore
import app.florsent.data.InstalledAppsRepository
import app.florsent.data.SettingsRepository
import app.florsent.data.guidanceDataStore
import app.florsent.data.settingsDataStore
import app.florsent.domain.GuidanceEngine
import app.florsent.domain.KnownSocialApps
import app.florsent.service.GuidanceCoordinator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.map
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
    val installedApps = InstalledAppsRepository(context)
    val guidance = GuidanceCoordinator(
        scope = scope,
        engine = GuidanceEngine(),
        configFlow = settings.settings.map { it.toGuidanceConfig() },
        store = GuidanceStateStore(context.guidanceDataStore),
        clock = System::currentTimeMillis,
    )

    init {
        scope.launch {
            settings.initializeWatchedIfNeeded(installedApps.installedPackages(KnownSocialApps.packages))
        }
    }
}

val Context.appContainer: AppContainer
    get() = (applicationContext as FlorsentApp).container
