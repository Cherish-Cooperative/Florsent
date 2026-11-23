package app.florsent

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.florsent.domain.GuidanceEvent
import app.florsent.ui.FlorsentAppUi
import app.florsent.ui.Route
import app.florsent.ui.theme.FlorsentTheme

class MainActivity : ComponentActivity() {

    private var requestedRoute by mutableStateOf<Route?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) requestedRoute = routeFor(intent)

        val container = appContainer
        setContent {
            FlorsentTheme {
                FlorsentAppUi(
                    container = container,
                    requestedRoute = requestedRoute,
                    onRouteConsumed = { requestedRoute = null },
                    onStartPreview = { moveTaskToBack(true) },
                    onOpenAccessibilitySettings = {
                        startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
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
