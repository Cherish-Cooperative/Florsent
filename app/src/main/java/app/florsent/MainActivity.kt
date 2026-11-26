package app.florsent

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.florsent.data.AppLocales
import app.florsent.domain.GuidanceEvent
import app.florsent.ui.FlorsentAppUi
import app.florsent.ui.Route
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.florsent.ui.theme.FlorsentTheme
import app.florsent.util.SystemSettings

class MainActivity : ComponentActivity() {

    private var requestedRoute by mutableStateOf<Route?>(null)

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppLocales.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) requestedRoute = routeFor(intent)

        val container = appContainer
        if (savedInstanceState == null && requestedRoute == null) {
            // A fresh screen that was not opened from the nudge cannot be showing a breathing
            // session. If the engine still thinks one is running (the process died mid-session), end
            // it now instead of showing "breathing" on the home card until the 15-minute timeout.
            container.guidance.dispatch(GuidanceEvent.BreathingCancelled(container.guidance.now()))
        }
        setContent {
            val settings by container.currentSettings.collectAsStateWithLifecycle()
            FlorsentTheme(dynamicColor = settings.dynamicColor) {
                FlorsentAppUi(
                    container = container,
                    requestedRoute = requestedRoute,
                    onRouteConsumed = { requestedRoute = null },
                    onStartPreview = { moveTaskToBack(true) },
                    onOpenAccessibilitySettings = { SystemSettings.openAccessibility(this) },
                    onGoToLauncher = {
                        startActivity(
                            Intent(Intent.ACTION_MAIN)
                                .addCategory(Intent.CATEGORY_HOME)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                        )
                    },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        routeFor(intent)?.let { requestedRoute = it }
    }

    override fun onResume() {
        super.onResume()
        // Coming back to Florsent ends a running preview (the preview itself sends us to the
        // background, hence the grace period).
        val guidance = appContainer.guidance
        val started = guidance.state.value.previewStartedAtMs
        if (started != null && guidance.now() - started > PREVIEW_RESUME_GRACE_MS) {
            guidance.dispatch(GuidanceEvent.PreviewStopped(guidance.now()))
        }
    }

    private fun routeFor(intent: Intent?): Route? =
        if (intent?.action == ACTION_BREATHE) Route.Breathe else null

    companion object {
        const val ACTION_BREATHE = "app.florsent.action.BREATHE"
        private const val PREVIEW_RESUME_GRACE_MS = 1_500L

        fun breatheIntent(context: Context): Intent =
            Intent(context, MainActivity::class.java)
                .setAction(ACTION_BREATHE)
                .addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP,
                )
    }
}
