package app.florsent.service

import android.accessibilityservice.AccessibilityService
import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.os.Build
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.WindowManager
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import app.florsent.R
import kotlin.math.abs

/**
 * The soft "take a breath?" pill that slides up from the bottom edge. Tapping it is a user gesture
 * inside our own (trusted) accessibility window, so opening the breathing screen from here is not a
 * background activity launch.
 *
 * Its dot glows and dims slowly like a breathing light. The pill can be dragged up or down (not
 * sideways) when it covers something the user wants to tap; the height is remembered.
 */
class NudgeOverlay(
    private val service: AccessibilityService,
    private val onAccept: () -> Unit,
    private val onDismiss: () -> Unit,
) {
    private val windowManager = service.getSystemService(WindowManager::class.java)
    private val prefs = service.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private var root: FrameLayout? = null
    private var params: WindowManager.LayoutParams? = null
    private var pill: View? = null
    private var message: TextView? = null
    private var breathing: ValueAnimator? = null
    private var visible = false
    private var bedtime = false

    /** Shows or hides the pill; [bedtime] switches the wording to the bedtime question. */
    fun setVisible(show: Boolean, bedtime: Boolean = false) {
        if (bedtime != this.bedtime) {
            this.bedtime = bedtime
            message?.text = service.getString(messageRes())
        }
        if (show == visible) return
        visible = show
        if (show) show() else hide()
    }

    private fun messageRes() = if (bedtime) R.string.nudge_message_bedtime else R.string.nudge_message

    fun release() {
        visible = false
        breathing?.cancel()
        breathing = null
        pill?.animate()?.cancel()
        root?.let { runCatching { windowManager.removeViewImmediate(it) } }
        root = null
        params = null
        pill = null
        message = null
    }

    private fun show() {
        if (root == null) {
            val (container, content) = buildViews()
            val lp = layoutParams()
            windowManager.addView(container, lp)
            root = container
            params = lp
            pill = content
            // Screen size may have changed since the height was saved (rotation, folding): clamp once
            // the pill's own height is known.
            container.addOnLayoutChangeListener(object : View.OnLayoutChangeListener {
                override fun onLayoutChange(v: View, l: Int, t: Int, r: Int, b: Int, ol: Int, ot: Int, or: Int, ob: Int) {
                    v.removeOnLayoutChangeListener(this)
                    moveTo(lp.y)
                }
            })
        }
        breathing?.start()
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

    /** Lowest window offset: just above the navigation bar. */
    private fun minOffset(): Int = navigationBarInset() + dp(12f).toInt()

    /** Highest window offset: the pill stays below the status bar. */
    private fun maxOffset(): Int {
        val height = root?.height?.takeIf { it > 0 } ?: return Int.MAX_VALUE // not measured yet
        val screen: Int
        val top: Int
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val metrics = windowManager.currentWindowMetrics
            screen = metrics.bounds.height()
            top = metrics.windowInsets.getInsets(WindowInsets.Type.statusBars()).top
        } else {
            screen = service.resources.displayMetrics.heightPixels
            top = dp(24f).toInt()
        }
        return (screen - top - height).coerceAtLeast(minOffset())
    }

    private fun moveTo(offset: Int) {
        val container = root ?: return
        val lp = params ?: return
        val clamped = offset.coerceIn(minOffset(), maxOffset())
        if (clamped == lp.y) return
        lp.y = clamped
        runCatching { windowManager.updateViewLayout(container, lp) }
    }

    private fun saveOffset() {
        val lp = params ?: return
        // Saved relative to the bottom edge's resting place, so a different navigation bar still fits.
        prefs.edit().putInt(KEY_LIFT, lp.y - minOffset()).apply()
    }

    /**
     * Vertical drag anywhere on the pill (the close button included). A touch that moves less than the
     * touch slop stays a tap and reaches the pill or the close button as usual.
     */
    @SuppressLint("ViewConstructor")
    private inner class DragFrame(context: Context) : FrameLayout(context) {
        private val slop = ViewConfiguration.get(context).scaledTouchSlop
        private var downRawY = 0f
        private var downOffset = 0
        private var dragging = false

        override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
            when (ev.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downRawY = ev.rawY
                    downOffset = params?.y ?: 0
                    dragging = false
                }
                MotionEvent.ACTION_MOVE -> if (!dragging && abs(ev.rawY - downRawY) > slop) {
                    dragging = true
                    parent?.requestDisallowInterceptTouchEvent(true)
                }
            }
            return dragging
        }

        @SuppressLint("ClickableViewAccessibility") // dragging only moves the pill; tap and TalkBack stay on the pill
        override fun onTouchEvent(ev: MotionEvent): Boolean {
            when (ev.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downRawY = ev.rawY
                    downOffset = params?.y ?: 0
                }
                // The window is anchored to the bottom, so moving the finger up raises the offset.
                MotionEvent.ACTION_MOVE -> {
                    if (!dragging && abs(ev.rawY - downRawY) > slop) dragging = true
                    if (dragging) moveTo(downOffset - (ev.rawY - downRawY).toInt())
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (dragging) saveOffset()
                    dragging = false
                }
            }
            return true
        }
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
        // A breathing light: brightens and swells a little, then dims, about 4 s each way. With system
        // animations turned off the animator jumps to its end, leaving a steady dot.
        breathing = ObjectAnimator.ofPropertyValuesHolder(
            dot,
            PropertyValuesHolder.ofFloat(View.ALPHA, DOT_DIM, 1f),
            PropertyValuesHolder.ofFloat(View.SCALE_X, 0.8f, 1.15f),
            PropertyValuesHolder.ofFloat(View.SCALE_Y, 0.8f, 1.15f),
        ).apply {
            duration = DOT_BREATH_MS
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
            interpolator = AccelerateDecelerateInterpolator()
        }
        val message = TextView(ctx).apply {
            text = ctx.getString(messageRes())
            setTextColor(onSurface)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            maxWidth = dp(240f).toInt() // stays on screen at the largest font scale
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        val close = ImageView(ctx).apply {
            setImageResource(R.drawable.ic_close)
            imageTintList = ColorStateList.valueOf(muted)
            scaleType = ImageView.ScaleType.CENTER
            val pad = dp(14f).toInt()
            setPadding(pad, pad, pad, pad)
            contentDescription = ctx.getString(R.string.nudge_dismiss_description)
            background = RippleDrawable(ColorStateList.valueOf(ripple), null, GradientDrawable().apply {
                setShape(GradientDrawable.OVAL)
                setColor(0xFFFFFFFF.toInt())
            })
            isClickable = true
            setOnClickListener { onDismiss() }
        }

        val dotSize = dp(9f).toInt()
        pill.addView(dot, LinearLayout.LayoutParams(dotSize, dotSize).apply { marginEnd = dp(12f).toInt() })
        pill.addView(message, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
        ))
        val closeSize = dp(48f).toInt() // minimum touch target
        pill.addView(close, LinearLayout.LayoutParams(closeSize, closeSize).apply { marginStart = dp(6f).toInt() })

        // Padding leaves room for the elevation shadow inside the wrap-content window.
        val container = DragFrame(ctx).apply {
            // TalkBack announces the pill as it slides in, like a system pane.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                accessibilityPaneTitle = ctx.getString(messageRes())
            }
            val pad = dp(16f).toInt()
            setPadding(pad, pad, pad, pad)
            clipChildren = false
            clipToPadding = false
        }
        container.addView(pill, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
        ))
        this.message = message
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
        y = minOffset() + prefs.getInt(KEY_LIFT, 0).coerceAtLeast(0)
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
        const val DOT_BREATH_MS = 4_000L
        const val DOT_DIM = 0.3f
        const val PREFS = "nudge_overlay"
        const val KEY_LIFT = "lift_px"
    }
}
