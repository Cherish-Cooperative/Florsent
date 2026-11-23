package app.florsent.service.audio

import android.animation.ValueAnimator
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.SoundPool
import android.view.animation.LinearInterpolator
import app.florsent.R
import app.florsent.domain.BreathPhaseKind

/**
 * Optional sound for the breathing session: a looping ambient bed that fades in and out, plus a
 * soft bell at phase changes (pitched per phase via the SoundPool playback rate, so one 10 KB asset
 * covers all phases). Must be used from the main thread.
 */
class BreathingSoundscape(context: Context) {

    private val appContext = context.applicationContext
    private val audioManager = appContext.getSystemService(AudioManager::class.java)
    private val attributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_MEDIA)
        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
        .build()

    private val soundPool = SoundPool.Builder().setMaxStreams(2).setAudioAttributes(attributes).build()
    private var cueId = 0
    private var cueLoaded = false

    private var player: MediaPlayer? = null
    private var fade: ValueAnimator? = null
    private var volume = 0f

    private val focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
        .setAudioAttributes(attributes)
        .setOnAudioFocusChangeListener { change ->
            if (change == AudioManager.AUDIOFOCUS_LOSS || change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) {
                stopAmbient()
            }
        }
        .build()

    init {
        soundPool.setOnLoadCompleteListener { _, id, status -> if (id == cueId && status == 0) cueLoaded = true }
        cueId = soundPool.load(appContext, R.raw.cue_soft, 1)
    }

    fun startAmbient() {
        if (player != null) return
        if (audioManager.requestAudioFocus(focusRequest) != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) return
        player = MediaPlayer.create(appContext, R.raw.ambient_handpan, attributes, audioManager.generateAudioSessionId())
            ?.apply {
                isLooping = true
                setVolume(0f, 0f)
                start()
            }
        fadeTo(AMBIENT_VOLUME, FADE_IN_MS) {}
    }

    fun stopAmbient() {
        val current = player ?: return
        fadeTo(0f, FADE_OUT_MS) {
            if (player === current) {
                current.release()
                player = null
                audioManager.abandonAudioFocusRequest(focusRequest)
            }
        }
    }

    fun playCue(kind: BreathPhaseKind) {
        if (!cueLoaded) return
        val rate = when (kind) {
            BreathPhaseKind.Inhale -> 1.0f
            BreathPhaseKind.HoldIn -> 0.89f
            BreathPhaseKind.Exhale -> 0.75f
            BreathPhaseKind.HoldOut -> 0.84f
        }
        soundPool.play(cueId, CUE_VOLUME, CUE_VOLUME, 1, 0, rate)
    }

    fun release() {
        fade?.cancel()
        player?.release()
        player = null
        audioManager.abandonAudioFocusRequest(focusRequest)
        soundPool.release()
    }

    private fun fadeTo(target: Float, durationMs: Long, onEnd: () -> Unit) {
        fade?.cancel()
        fade = ValueAnimator.ofFloat(volume, target).apply {
            duration = durationMs
            interpolator = LinearInterpolator()
            addUpdateListener {
                volume = it.animatedValue as Float
                player?.setVolume(volume, volume)
            }
            addListener(object : android.animation.AnimatorListenerAdapter() {
                private var cancelled = false
                override fun onAnimationCancel(animation: android.animation.Animator) { cancelled = true }
                override fun onAnimationEnd(animation: android.animation.Animator) { if (!cancelled) onEnd() }
            })
            start()
        }
    }

    private companion object {
        const val AMBIENT_VOLUME = 0.45f
        const val CUE_VOLUME = 0.35f
        const val FADE_IN_MS = 2_500L
        const val FADE_OUT_MS = 1_200L
    }
}
