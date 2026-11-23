package app.florsent.service.effect

import android.accessibilityservice.AccessibilityService
import android.graphics.PixelFormat
import android.os.Build
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import android.view.animation.LinearInterpolator
import kotlin.math.abs

/**
 * Base-layer effect: a full-screen neutral-gray accessibility overlay whose opacity follows the
 * guidance level. Blending toward mid-gray lowers both contrast and perceived saturation without
 * tinting the screen.
 *
 * Smoothness: the engine updates the level once per second, and each update starts a linear
 * ViewPropertyAnimator lasting one second. The overlay therefore ramps continuously at the display's
 * refresh rate, and since only view alpha changes, each frame is a compositor-only update.
 *
 * TYPE_ACCESSIBILITY_OVERLAY is a trusted window, so touches pass through at any opacity (unlike
 * TYPE_APPLICATION_OVERLAY, which is capped at 0.8 by untrusted-touch occlusion rules).
 */
class DimOverlayController(
    private val service: AccessibilityService,
    private val maxAlpha: Float = DEFAULT_MAX_ALPHA,
) : VisualEffectController {

    private val windowManager = service.getSystemService(WindowManager::class.java)
    private var view: View? = null
    private var targetAlpha = 0f

    override fun render(effect: VisualEffect) {
        val newTarget = effect.level.coerceIn(0f, 1f) * maxAlpha
        if (view == null && newTarget <= MIN_VISIBLE_ALPHA) return
        if (view != null && abs(newTarget - targetAlpha) < EPSILON) return

        val rising = newTarget >= targetAlpha
        targetAlpha = newTarget
        val v = view ?: attach()
        v.animate().cancel()

        if (newTarget <= MIN_VISIBLE_ALPHA) {
            targetAlpha = 0f
            v.animate()
                .alpha(0f)
                .setDuration(FADE_OUT_MS)
                .setInterpolator(DecelerateInterpolator())
                .withEndAction { if (targetAlpha == 0f) detach() }
                .start()
            return
        }

        v.animate()
            .alpha(newTarget)
            .setDuration(if (rising) STEP_MS else FADE_OUT_MS)
            .setInterpolator(if (rising) LinearInterpolator() else DecelerateInterpolator())
            .start()
    }

    override fun release() {
        targetAlpha = 0f
        view?.animate()?.cancel()
        detach()
    }

    private fun attach(): View {
        val v = View(service).apply {
            setBackgroundColor(OVERLAY_COLOR)
            alpha = 0f
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        }
        windowManager.addView(v, layoutParams())
        view = v
        return v
    }

    private fun detach() {
        val v = view ?: return
        view = null
        runCatching { windowManager.removeViewImmediate(v) }
    }

    private fun layoutParams() = WindowManager.LayoutParams(
        WindowManager.LayoutParams.MATCH_PARENT,
        WindowManager.LayoutParams.MATCH_PARENT,
        WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
        PixelFormat.TRANSLUCENT,
    ).apply {
        gravity = Gravity.TOP or Gravity.START
        title = "FlorsentDim"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            fitInsetsTypes = 0
            layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
    }

    companion object {
        /** Neutral mid-gray (no hue), so the screen washes out instead of being tinted. */
        private const val OVERLAY_COLOR = 0xFF858585.toInt()
        const val DEFAULT_MAX_ALPHA = 0.6f
        private const val STEP_MS = 1_000L
        private const val FADE_OUT_MS = 450L
        private const val MIN_VISIBLE_ALPHA = 0.002f
        private const val EPSILON = 0.0005f
    }
}
