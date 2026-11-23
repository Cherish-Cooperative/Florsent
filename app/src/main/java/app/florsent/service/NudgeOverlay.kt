package app.florsent.service

import android.accessibilityservice.AccessibilityService
import android.content.res.ColorStateList
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.os.Build
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.WindowManager
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import app.florsent.R

/**
 * The soft "take a breath?" pill that slides up from the bottom edge. Tapping it is a user gesture
 * inside our own (trusted) accessibility window, so opening the breathing screen from here is not a
 * background activity launch.
 */
class NudgeOverlay(
    private val service: AccessibilityService,
    private val onAccept: () -> Unit,
    private val onDismiss: () -> Unit,
) {
    private val windowManager = service.getSystemService(WindowManager::class.java)
    private var root: FrameLayout? = null
    private var pill: View? = null
    private var visible = false

    fun setVisible(show: Boolean) {
        if (show == visible) return
        visible = show
        if (show) show() else hide()
    }

    fun release() {
        visible = false
        pill?.animate()?.cancel()
        root?.let { runCatching { windowManager.removeViewImmediate(it) } }
        root = null
        pill = null
    }

    private fun show() {
        if (root == null) {
            val (container, content) = buildViews()
            windowManager.addView(container, layoutParams())
            root = container
            pill = content
        }
        val p = pill ?: return
        p.animate().cancel()
        if (p.alpha == 0f) p.translationY = dp(56f)
        p.animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(ENTER_MS)
            .setInterpolator(DecelerateInterpolator(2f))
            .start()
    }

    private fun hide() {
        val p = pill ?: return
        p.animate().cancel()
        p.animate()
            .alpha(0f)
            .translationY(dp(40f))
            .setDuration(EXIT_MS)
            .setInterpolator(AccelerateInterpolator())
            .withEndAction { if (!visible) release() }
            .start()
    }

    private fun buildViews(): Pair<FrameLayout, View> {
        val ctx = service
        val surface = ContextCompat.getColor(ctx, R.color.nudge_surface)
        val onSurface = ContextCompat.getColor(ctx, R.color.nudge_on_surface)
        val muted = ContextCompat.getColor(ctx, R.color.nudge_on_surface_muted)
        val accent = ContextCompat.getColor(ctx, R.color.nudge_accent)
        val outline = ContextCompat.getColor(ctx, R.color.nudge_outline)
        val ripple = ContextCompat.getColor(ctx, R.color.nudge_ripple)

        val pillShape = GradientDrawable().apply {
            cornerRadius = dp(28f)
            setColor(surface)
            setStroke(dp(1f).toInt(), outline)
        }
        val pill = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = RippleDrawable(ColorStateList.valueOf(ripple), pillShape, null)
            elevation = dp(6f)
            setPadding(dp(20f).toInt(), dp(6f).toInt(), dp(6f).toInt(), dp(6f).toInt())
            isClickable = true
            isFocusable = true
            contentDescription = ctx.getString(R.string.nudge_accept_description)
            setOnClickListener { onAccept() }
            alpha = 0f
        }
        val dot = View(ctx).apply {
            background = GradientDrawable().apply {
                setShape(GradientDrawable.OVAL)
                setColor(accent)
            }
        }
        val message = TextView(ctx).apply {
            text = ctx.getString(R.string.nudge_message)
            setTextColor(onSurface)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        val close = TextView(ctx).apply {
            text = "✕"
            gravity = Gravity.CENTER
            setTextColor(muted)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            contentDescription = ctx.getString(R.string.nudge_dismiss_description)
            background = RippleDrawable(ColorStateList.valueOf(ripple), null, GradientDrawable().apply {
                setShape(GradientDrawable.OVAL)
                setColor(0xFFFFFFFF.toInt())
            })
            isClickable = true
            setOnClickListener { onDismiss() }
        }

        val dotSize = dp(8f).toInt()
        pill.addView(dot, LinearLayout.LayoutParams(dotSize, dotSize).apply { marginEnd = dp(12f).toInt() })
        pill.addView(message, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
        ))
        val closeSize = dp(44f).toInt()
        pill.addView(close, LinearLayout.LayoutParams(closeSize, closeSize).apply { marginStart = dp(6f).toInt() })

        // Padding leaves room for the elevation shadow inside the wrap-content window.
        val container = FrameLayout(ctx).apply {
            val pad = dp(16f).toInt()
            setPadding(pad, pad, pad, pad)
            clipChildren = false
            clipToPadding = false
        }
        container.addView(pill, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
        ))
        return container to pill
    }

    private fun layoutParams() = WindowManager.LayoutParams(
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
        PixelFormat.TRANSLUCENT,
    ).apply {
        gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
        y = navigationBarInset() + dp(12f).toInt()
        title = "FlorsentNudge"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) fitInsetsTypes = 0
    }

    private fun navigationBarInset(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            windowManager.currentWindowMetrics.windowInsets
                .getInsets(WindowInsets.Type.navigationBars()).bottom
        } else {
            @Suppress("DiscouragedApi", "InternalInsetResource")
            val id = service.resources.getIdentifier("navigation_bar_height", "dimen", "android")
            if (id > 0) service.resources.getDimensionPixelSize(id) else 0
        }

    private fun dp(value: Float): Float = value * service.resources.displayMetrics.density

    private companion object {
        const val ENTER_MS = 520L
        const val EXIT_MS = 260L
    }
}
