package app.florsent.service.audio

import android.os.Handler
import android.os.Looper
import android.os.SystemClock

/**
 * Linear 0..1 ramp on wall-clock time (not ValueAnimator, which jumps straight to the end when the
 * user turns off system animations, making a sound pop in at full volume). Main thread only.
 */
class VolumeRamp(private val apply: (Float) -> Unit) {

    private val handler = Handler(Looper.getMainLooper())
    private var step: Runnable? = null

    /** Where the ramp currently is, 0..1. */
    var progress = 0f
        private set

    fun to(target: Float, durationMs: Long, onEnd: () -> Unit = {}) {
        cancel()
        val from = progress
        val startedAt = SystemClock.uptimeMillis()
        val duration = durationMs.coerceAtLeast(1)
        val runnable = object : Runnable {
            override fun run() {
                val t = ((SystemClock.uptimeMillis() - startedAt).toFloat() / duration).coerceIn(0f, 1f)
                progress = from + (target - from) * t
                apply(progress)
                if (t < 1f) {
                    handler.postDelayed(this, STEP_MS)
                } else {
                    step = null
                    onEnd()
                }
            }
        }
        step = runnable
        runnable.run()
    }

    /** Stops where it is. */
    fun cancel() {
        step?.let(handler::removeCallbacks)
        step = null
    }

    /** Jumps to [value] without ramping. */
    fun set(value: Float) {
        cancel()
        progress = value
        apply(value)
    }

    private companion object {
        const val STEP_MS = 50L
    }
}
