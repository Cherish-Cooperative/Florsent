package app.florsent.service.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import app.florsent.R
import app.florsent.service.effect.VisualEffect
import app.florsent.service.effect.VisualEffectController
import app.florsent.util.DebugLog

/** The sound bed that plays while the screen fades; an interface so the controller can be tested. */
interface FadeSoundPlayer {
    /** Starts (or rescues a fade-out) and ramps up over [fadeInMs]. False if audio focus was refused. */
    fun start(fadeInMs: Long): Boolean

    /** Asks for audio focus again while still playing, ducking whatever took it. False if refused. */
    fun reclaimFocus(): Boolean

    /** Ramps down over [fadeOutMs], then releases the player and gives back audio focus. */
    fun stop(fadeOutMs: Long)

    /** Silences and frees everything immediately. */
    fun release()
}

/** Runs an action later on the main thread; an interface so retry timing can be tested. */
fun interface DelayScheduler {
    /** Schedules [action] after [delayMs]; the returned function cancels it. */
    fun schedule(delayMs: Long, action: () -> Unit): () -> Unit
}

/**
 * When to ask for audio focus again after a feed video took it.
 *
 * Swiping through Reels or Shorts takes focus on every new video, so giving up on the first loss
 * silences the rain within seconds. Instead the rain asks again after a short cooldown, which ducks
 * the video under it. Two limits keep this from turning into a fight with an app that grabs focus
 * right back: at most one request per cooldown, and a loss that comes almost immediately after a
 * grant counts as pushback, doubling the cooldown each time. Enough pushback in a row and the rain
 * gives up for the rest of this fade.
 */
class FocusReclaimPolicy(
    private val baseDelayMs: Long = BASE_DELAY_MS,
    private val maxDelayMs: Long = MAX_DELAY_MS,
    private val pushbackWindowMs: Long = PUSHBACK_WINDOW_MS,
    private val maxPushbacks: Int = MAX_PUSHBACKS,
) {
    private var lastGrantAt: Long? = null
    private var streak = 0

    fun onGranted(nowMs: Long) {
        lastGrantAt = nowMs
    }

    /** Focus was taken away. Returns how long to wait before asking again, or null to give up. */
    fun onLost(nowMs: Long): Long? {
        val grantedAt = lastGrantAt
        val pushback = grantedAt != null && nowMs - grantedAt < pushbackWindowMs
        return next(pushback)
    }

    /** A request to get focus back was refused; always counts as pushback. */
    fun onRefused(): Long? = next(pushback = true)

    fun reset() {
        lastGrantAt = null
        streak = 0
    }

    private fun next(pushback: Boolean): Long? {
        streak = if (pushback) streak + 1 else 1
        if (streak > maxPushbacks) return null
        return (baseDelayMs shl (streak - 1).coerceAtMost(MAX_SHIFT)).coerceAtMost(maxDelayMs)
    }

    companion object {
        /** Long enough for the new video to start, short enough that the rain barely drops out. */
        const val BASE_DELAY_MS = 1_200L
        const val MAX_DELAY_MS = 9_600L

        /** Losing focus again this soon after getting it means the other app is grabbing it back. */
        const val PUSHBACK_WINDOW_MS = 1_000L
        const val MAX_PUSHBACKS = 5
        private const val MAX_SHIFT = 16
    }
}

/**
 * Optional soft rain while the screen fades. It joins the visual layers so it follows exactly the
 * same guidance level: on when the fade starts, off when the effect ends (app left, screen off,
 * nudge accepted, preview over). Sound plays only while a watched app is fading, so focus is only
 * ever reclaimed from the feed the user is scrolling.
 *
 * Other audio is ducked, never paused. A call (or ringing) always wins: the rain stops for the rest
 * of this fade. Any other app taking focus (a new Reel or Short starting) is answered by
 * [FocusReclaimPolicy]: wait, ask again, and give up only if the other app keeps fighting back.
 */
class FadeSoundController(
    private val player: FadeSoundPlayer,
    private val isEnabled: () -> Boolean,
    private val inCall: () -> Boolean,
    private val scheduler: DelayScheduler,
    private val nowMs: () -> Long,
    private val policy: FocusReclaimPolicy = FocusReclaimPolicy(),
) : VisualEffectController {

    private var playing = false
    private var suppressed = false
    private var handingOff = false
    private var cancelRetry: (() -> Unit)? = null

    /** True while a request to get focus back is waiting for its cooldown. */
    val isReclaimPending: Boolean get() = cancelRetry != null

    override fun render(effect: VisualEffect) {
        if (effect.level <= 0f) suppressed = false
        val want = effect.level > 0f && !suppressed && isEnabled() && !inCall()
        when {
            want && !playing -> {
                playing = player.start(FADE_IN_MS)
                if (playing) {
                    policy.reset()
                    policy.onGranted(nowMs())
                } else {
                    suppressed = true
                }
            }
            !want && playing -> {
                cancelPendingRetry()
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
        if (playing) {
            handingOff = true
            cancelPendingRetry()
        }
    }

    /** Another app took audio focus. */
    fun onFocusLost() {
        // Already fading out (hand-off to breathing, which takes focus itself): let it finish.
        if (!playing || handingOff) return
        if (inCall()) return giveUp("call")
        if (isReclaimPending) return // one request per cooldown, however many losses arrive
        val delay = policy.onLost(nowMs()) ?: return giveUp("pushback")
        scheduleRetry(delay)
    }

    /** Focus came back on its own (the other app let go). */
    fun onFocusGained() {
        if (!playing) return
        cancelPendingRetry()
        policy.onGranted(nowMs())
    }

    override fun release() {
        cancelPendingRetry()
        playing = false
        suppressed = false
        player.release()
    }

    private fun scheduleRetry(delayMs: Long) {
        DebugLog.d { "fade sound: focus lost, asking again in $delayMs ms" }
        cancelRetry = scheduler.schedule(delayMs) {
            cancelRetry = null
            retry()
        }
    }

    private fun retry() {
        if (!playing || handingOff) return
        if (inCall()) return giveUp("call")
        if (!isEnabled()) return
        if (player.reclaimFocus()) {
            DebugLog.d { "fade sound: focus back, feed ducked" }
            policy.onGranted(nowMs())
        } else {
            val delay = policy.onRefused() ?: return giveUp("refused")
            scheduleRetry(delay)
        }
    }

    private fun giveUp(reason: String) {
        DebugLog.d { "fade sound: quiet for the rest of this fade ($reason)" }
        cancelPendingRetry()
        player.stop(FADE_OUT_MS)
        playing = false
        suppressed = true
    }

    private fun cancelPendingRetry() {
        cancelRetry?.invoke()
        cancelRetry = null
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
    /**
     * The master is -13.6 LUFS (true peak -1.3 dBFS); at 0.6 the rain sits around -18 LUFS, clearly
     * above a feed video ducked by the system and still well below one playing at full volume.
     */
    const val TARGET_VOLUME = 0.6f

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
    private val onFocusGained: () -> Unit,
) : FadeSoundPlayer {

    private val appContext = context.applicationContext
    private val audioManager = appContext.getSystemService(AudioManager::class.java)
    private val attributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_MEDIA)
        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
        .build()

    private var loop: GaplessLoop? = null
    private var stopping = false
    private val ramp = VolumeRamp { p -> loop?.setVolume(FadeSoundCurve.volumeAt(p)) }

    private val focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
        .setAudioAttributes(attributes)
        .setWillPauseWhenDucked(false)
        .setOnAudioFocusChangeListener { change ->
            // The controller decides: ask again later (a new Reel or Short) or stop (a call). A fade
            // already under way (e.g. handing over to breathing) always finishes at its own pace.
            when (change) {
                AudioManager.AUDIOFOCUS_LOSS, AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> if (!stopping) onFocusLost()
                AudioManager.AUDIOFOCUS_GAIN -> if (!stopping) onFocusGained()
            }
        }
        .build()

    override fun start(fadeInMs: Long): Boolean {
        if (loop == null) {
            if (audioManager.requestAudioFocus(focusRequest) != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) return false
            val created = GaplessLoop.forRaw(appContext, R.raw.fade_ambient, attributes, audioManager.generateAudioSessionId())
            if (!created.isValid) {
                audioManager.abandonAudioFocusRequest(focusRequest)
                return false
            }
            loop = created
            ramp.set(0f)
            created.start()
        }
        DebugLog.d { "fade sound start (progress=${ramp.progress})" }
        // From wherever it is (silence, or part-way through a fade-out) up to full.
        stopping = false
        ramp.to(1f, ((1f - ramp.progress) * fadeInMs).toLong())
        return true
    }

    override fun reclaimFocus(): Boolean {
        if (loop == null || stopping) return false
        return audioManager.requestAudioFocus(focusRequest) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
    }

    override fun stop(fadeOutMs: Long) {
        val current = loop ?: return
        DebugLog.d { "fade sound stop (progress=${ramp.progress})" }
        stopping = true
        ramp.to(0f, (ramp.progress * fadeOutMs).toLong()) {
            if (loop === current) release()
        }
    }

    override fun release() {
        if (loop != null) DebugLog.d { "fade sound released" }
        ramp.cancel()
        loop?.release()
        loop = null
        stopping = false
        audioManager.abandonAudioFocusRequest(focusRequest)
    }
}
