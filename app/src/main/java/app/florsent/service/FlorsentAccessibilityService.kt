package app.florsent.service

import android.accessibilityservice.AccessibilityService
import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.PowerManager
import android.view.accessibility.AccessibilityEvent
import android.view.inputmethod.InputMethodManager
import androidx.core.content.ContextCompat
import app.florsent.MainActivity
import app.florsent.appContainer
import app.florsent.domain.ForegroundPolicy
import app.florsent.domain.GuidanceEvent
import app.florsent.service.effect.CompositeVisualEffectController
import app.florsent.service.effect.DimOverlayController
import app.florsent.service.effect.PlatformZenRuleGateway
import app.florsent.service.effect.ShizukuSaturationController
import app.florsent.service.effect.VisualEffect
import app.florsent.service.effect.VisualEffectController
import app.florsent.service.effect.ZenGrayscale
import app.florsent.service.effect.ZenGrayscaleController
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Only listens to TYPE_WINDOW_STATE_CHANGED (see res/xml/accessibility_service_config.xml) to learn
 * which app is in front. It never reads window content. It also hosts the overlay windows, because
 * TYPE_ACCESSIBILITY_OVERLAY requires an accessibility service context.
 */
class FlorsentAccessibilityService : AccessibilityService() {

    private val scope = MainScope()
    private val coordinator get() = appContainer.guidance

    private var effects: VisualEffectController? = null
    private var nudge: NudgeOverlay? = null
    private var renderJob: Job? = null
    private var ignoredPackages: Set<String> = emptySet()
    private var lastForeground: String? = null
    private var receiverRegistered = false
    private var isShutDown = true

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val now = coordinator.now()
            when (intent.action) {
                Intent.ACTION_SCREEN_OFF -> coordinator.dispatch(GuidanceEvent.ScreenChanged(false, now))
                Intent.ACTION_USER_PRESENT -> coordinator.dispatch(GuidanceEvent.ScreenChanged(true, now))
                Intent.ACTION_SCREEN_ON -> {
                    val keyguard = getSystemService(KeyguardManager::class.java)
                    if (keyguard?.isKeyguardLocked != true) {
                        coordinator.dispatch(GuidanceEvent.ScreenChanged(true, now))
                    }
                }
            }
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        ignoredPackages = getSystemService(InputMethodManager::class.java)
            ?.enabledInputMethodList
            ?.mapTo(mutableSetOf()) { it.packageName }
            .orEmpty()

        val container = appContainer
        effects = CompositeVisualEffectController(
            listOfNotNull(
                DimOverlayController(this),
                if (ZenGrayscale.isSupported) {
                    ZenGrayscaleController(PlatformZenRuleGateway(this)) {
                        container.currentSettings.value.zenGrayscaleFinish
                    }
                } else {
                    null
                },
                ShizukuSaturationController(container.shizuku) { container.currentSettings.value.shizukuSaturation },
            ),
        )
        nudge = NudgeOverlay(
            service = this,
            onAccept = ::onNudgeAccepted,
            onDismiss = { coordinator.dispatch(GuidanceEvent.NudgeDismissed(coordinator.now())) },
        )

        ContextCompat.registerReceiver(
            this,
            screenReceiver,
            IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_OFF)
                addAction(Intent.ACTION_SCREEN_ON)
                addAction(Intent.ACTION_USER_PRESENT)
            },
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        receiverRegistered = true

        // Seed the screen state: after a process restart no broadcast tells us it is already on.
        val power = getSystemService(PowerManager::class.java)
        val keyguard = getSystemService(KeyguardManager::class.java)
        val usable = power?.isInteractive != false && keyguard?.isKeyguardLocked != true
        coordinator.dispatch(GuidanceEvent.ScreenChanged(usable, coordinator.now()))

        isShutDown = false
        coordinator.setServiceRunning(true)
        renderJob = scope.launch {
            // Layer toggles are part of the key so switching one applies even at a steady level.
            val toggles = container.currentSettings
                .map { it.zenGrayscaleFinish to it.shizukuSaturation }
                .distinctUntilChanged()
            combine(coordinator.effect, toggles) { effect, _ -> effect }.collect { effect ->
                effects?.render(VisualEffect(effect.level, effect.targetPackage))
                nudge?.setVisible(effect.showNudge)
            }
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = ForegroundPolicy.resolve(event.packageName, event.className, packageName, ignoredPackages)
            ?: return
        if (pkg == lastForeground) return
        lastForeground = pkg
        coordinator.dispatch(GuidanceEvent.ForegroundChanged(pkg, coordinator.now()))
    }

    override fun onInterrupt() = Unit

    override fun onUnbind(intent: Intent?): Boolean {
        shutdown()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        shutdown()
        scope.cancel()
        super.onDestroy()
    }

    private fun onNudgeAccepted() {
        coordinator.dispatch(GuidanceEvent.NudgeAccepted(coordinator.now()))
        startActivity(MainActivity.breatheIntent(this))
    }

    /** Called from both onUnbind and onDestroy; only the first call does anything. */
    private fun shutdown() {
        if (isShutDown) return
        isShutDown = true
        renderJob?.cancel()
        renderJob = null
        effects?.release()
        effects = null
        nudge?.release()
        nudge = null
        if (receiverRegistered) {
            runCatching { unregisterReceiver(screenReceiver) }
            receiverRegistered = false
        }
        lastForeground = null
        coordinator.dispatch(GuidanceEvent.ForegroundChanged(null, coordinator.now()))
        coordinator.setServiceRunning(false)
    }
}
