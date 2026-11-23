package app.florsent.ui.apps

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.florsent.AppContainer
import app.florsent.data.InstalledApp
import app.florsent.domain.KnownSocialApps
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class AppRow(val packageName: String, val label: String, val watched: Boolean)

data class WatchedAppsUiState(
    val loading: Boolean = true,
    val suggested: List<AppRow> = emptyList(),
    val others: List<AppRow> = emptyList(),
)

class WatchedAppsViewModel(private val container: AppContainer) : ViewModel() {

    private val apps = MutableStateFlow<List<InstalledApp>?>(null)

    init {
        viewModelScope.launch { apps.value = container.installedApps.launchableApps() }
    }

    val uiState: StateFlow<WatchedAppsUiState> =
        combine(apps, container.settings.settings) { list, settings ->
            if (list == null) return@combine WatchedAppsUiState(loading = true)
            val rows = list.map { AppRow(it.packageName, it.label, it.packageName in settings.watchedPackages) }
            val (suggested, others) = rows.partition { it.packageName in KnownSocialApps.packages }
            WatchedAppsUiState(loading = false, suggested = suggested, others = others)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), WatchedAppsUiState())

    fun setWatched(packageName: String, watched: Boolean) {
        viewModelScope.launch { container.settings.setWatched(packageName, watched) }
    }

    suspend fun icon(packageName: String, sizePx: Int): Bitmap? =
        container.installedApps.icon(packageName, sizePx)
}
