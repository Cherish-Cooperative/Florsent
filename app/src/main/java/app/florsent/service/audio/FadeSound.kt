package app.florsent.service.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import app.florsent.R
import app.florsent.service.effect.VisualEffect
import app.florsent.service.effect.VisualEffectController
import app.florsent.util.DebugLog

/** The sound bed that plays while the screen fades; an interface so the controller can be tested. */
interface FadeSoundPlayer {
    /** Starts (or rescues a fade-out) and ramps up over [fadeInMs]. False if audio focus was refused. */
    fun start(fadeInMs: Long): Boolean

    /** Ramps down over [fadeOutMs], then releases the player and gives back audio focus. */
    fun stop(fadeOutMs: Long)

    /** Silences and frees everything immediately. */
    fun release()
}

/**
 * Optional soft rain while the screen fades. It joins the visual layers so it follows exactly the
 * same guidance level: on when the fade starts, off when the effect ends (app left, screen off,
 * nudge accepted, preview over).
 *
 * Other audio is ducked, never paused. If focus is refused or taken away (a call, another app
 * starting playback), the sound stays off until this fade is over, so Florsent never keeps grabbing
 * focus back from something the user chose to play.
 */
class FadeSoundController(
    private val player: FadeSoundPlayer,
    private val isEnabled: () -> Boolean,
    private val inCall: () -> Boolean,
) : VisualEffectController {

    private var playing = false
    private var suppressed = false
    private var handingOff = false

    override fun render(effect: VisualEffect) {
        if (effect.level <= 0f) suppressed = false
        val want = effect.level > 0f && !suppressed && isEnabled() && !inCall()
        when {
            want && !playing -> {
                playing = player.start(FADE_IN_MS)
                if (!playing) suppressed = true
            }
            !want && playing -> {
                player.stop(if (handingOff) HANDOFF_FADE_OUT_MS else FADE_OUT_MS)
                playing = false
            }
        }
        if (!playing) handingOff = false
    }

    /**
     * The nudge was tapped: the breathing screen takes about a second to come up and then fades its
     * own ambience in over 2.5 s. Let the rain linger long enough to overlap, so there is no silent gap.
     */
    fun handOffToBreathing() {
        if (playing) handingOff = true
    }

    /** Audio focus was lost for good (call, another app); stay quiet for the rest of this fade. */
    fun onFocusLost() {
        playing = false
        suppressed = true
    }

    override fun release() {
        playing = false
        suppressed = false
        player.release()
    }

    companion object {
        /** Rises with the fade rather than starting abruptly. */
        const val FADE_IN_MS = 15_000L

        /** Leaving the app, screen off, preview over. */
        const val FADE_OUT_MS = 1_500L

        /** Nudge tapped: covers the ~1 s launch plus the breathing ambience's 2.5 s fade-in. */
        const val HANDOFF_FADE_OUT_MS = 3_500L
    }
}

/** Volume curve for the fade-in: quadratic, so the first seconds are barely audible. */
object FadeSoundCurve {
    const val TARGET_VOLUME = 0.4f

    fun volumeAt(progress: Float): Float {
        val p = progress.coerceIn(0f, 1f)
        return TARGET_VOLUME * p * p
    }
}

/**
 * Plays `res/raw/fade_ambient.ogg` (distant rain and evening wind, seamless Opus loop) with
 * AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK, so the app in front is ducked by the system instead of paused.
 * Volume ramps run on wall-clock time, not ValueAnimator, so turning off system animations never
 * makes the sound jump in at full volume. Main thread only.
 */
class FadeSoundscape(
    context: Context,
    private val onFocusLost: () -> Unit,
) : FadeSoundPlayer {

    private val appContext = context.applicationContext
    private val audioManager = appContext.getSystemService(AudioManager::class.java)
    private val attributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_MEDIA)
        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
        .build()

    private var player: MediaPlayer? = null
    private var stopping = false
    private val handler = Handler(Looper.getMainLooper())
    private var ramp: Runnable? = null
    private var progress = 0f

    private val focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
        .setAudioAttributes(attributes)
        .setWillPauseWhenDucked(false)
        .setOnAudioFocusChangeListener { change ->
            if (change == AudioManager.AUDIOFOCUS_LOSS || change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) {
                // Already fading out (e.g. handing over to the breathing session, which just took
                // focus): let that fade finish at its own pace instead of cutting it short.
                if (!stopping) stop(FadeSoundController.FADE_OUT_MS)
                onFocusLost()
            }
        }
        .build()

    override fun start(fadeInMs: Long): Boolean {
        if (player == null) {
            if (audioManager.requestAudioFocus(focusRequest) != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) return false
            player = MediaPlayer.create(appContext, R.raw.fade_ambient, attributes, audioManager.generateAudioSessionId())
                ?.apply {
                    isLooping = true
                    setVolume(0f, 0f)
                    start()
                }
            if (player == null) {
                audioManager.abandonAudioFocusRequest(focusRequest)
                return false
            }
            progress = 0f
        }
        DebugLog.d { "fade sound start (progress=$progress)" }
        // From wherever it is (silence, or part-way through a fade-out) up to full.
        stopping = false
        rampTo(1f, ((1f - progress) * fadeInMs).toLong()) {}
        return true
    }

    override fun stop(fadeOutMs: Long) {
        val current = player ?: return
        DebugLog.d { "fade sound stop (progress=$progress)" }
        stopping = true
        rampTo(0f, (progress * fadeOutMs).toLong().coerceAtLeast(1)) {
            if (player === current) release()
        }
    }

    override fun release() {
        if (player != null) DebugLog.d { "fade sound released" }
        ramp?.let(handler::removeCallbacks)
        ramp = null
        player?.release()
        player = null
        progress = 0f
        stopping = false
        audioManager.abandonAudioFocusRequest(focusRequest)
    }

    private fun rampTo(target: Float, durationMs: Long, onEnd: () -> Unit) {
        ramp?.let(handler::removeCallbacks)
        val from = progress
        val startedAt = SystemClock.uptimeMillis()
        val duration = durationMs.coerceAtLeast(1)
        val step = object : Runnable {
            override fun run() {
                val t = ((SystemClock.uptimeMillis() - startedAt).toFloat() / duration).coerceIn(0f, 1f)
                progress = from + (target - from) * t
                val v = FadeSoundCurve.volumeAt(progress)
                player?.setVolume(v, v)
                if (t < 1f) {
                    handler.postDelayed(this, RAMP_STEP_MS)
                } else {
                    ramp = null
                    onEnd()
                }
            }
        }
        ramp = step
        step.run()
    }

    private companion object {
        const val RAMP_STEP_MS = 50L
    }
}
