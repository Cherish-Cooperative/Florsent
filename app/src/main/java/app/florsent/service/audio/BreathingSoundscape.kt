package app.florsent.service.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import app.florsent.R
import app.florsent.domain.BreathPhaseKind

/** Sound for a breathing session; an interface so the ViewModel can be tested without audio. */
interface BreathingSound {
    fun startAmbient()
    fun stopAmbient()
    fun pauseAmbient()
    fun resumeAmbient()
    fun playCue(kind: BreathPhaseKind)
    fun release()
}

/**
 * Optional sound for the breathing session: the singing-bowl bed (gapless loop, faded in and out)
 * and a soft chime at phase changes, pitched per phase so one short clip covers all phases. The
 * chime shares the ambience's output (see [CuePlayer]) so it never disturbs the bed. Main thread only.
 */
class BreathingSoundscape(context: Context) : BreathingSound {

    private val appContext = context.applicationContext
    private val audioManager = appContext.getSystemService(AudioManager::class.java)
    private val attributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_MEDIA)
        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
        .build()

    private val cue = CuePlayer(appContext, attributes, CUE_VOLUME)
    private var loop: GaplessLoop? = null
    private val ramp = VolumeRamp { p -> loop?.setVolume(AMBIENT_VOLUME * p) }

    private val focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
        .setAudioAttributes(attributes)
        .setOnAudioFocusChangeListener { change ->
            if (change == AudioManager.AUDIOFOCUS_LOSS || change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) {
                stopAmbient()
            }
        }
        .build()

    override fun startAmbient() {
        val existing = loop
        if (existing != null) {
            // Possibly mid fade-out: fade back in instead of going silent.
            if (!existing.isPlaying) existing.start()
            ramp.to(1f, FADE_IN_MS)
            return
        }
        if (audioManager.requestAudioFocus(focusRequest) != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) return
        val created = GaplessLoop.forRaw(appContext, R.raw.breathing_ambient, attributes, audioManager.generateAudioSessionId())
        if (!created.isValid) {
            audioManager.abandonAudioFocusRequest(focusRequest)
            return
        }
        loop = created
        ramp.set(0f)
        created.start()
        ramp.to(1f, FADE_IN_MS)
    }

    /** App went to the background mid-session: hold the loop (and give back audio focus). */
    override fun pauseAmbient() {
        val current = loop ?: return
        ramp.set(0f)
        if (current.isPlaying) current.pause()
        audioManager.abandonAudioFocusRequest(focusRequest)
    }

    /** Back in front: resume a paused loop with a short fade. */
    override fun resumeAmbient() {
        val current = loop ?: return
        if (current.isPlaying) return
        if (audioManager.requestAudioFocus(focusRequest) != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) return
        current.start()
        ramp.to(1f, FADE_IN_MS)
    }

    override fun stopAmbient() {
        val current = loop ?: return
        ramp.to(0f, (ramp.progress * FADE_OUT_MS).toLong()) {
            if (loop === current) {
                current.release()
                loop = null
                audioManager.abandonAudioFocusRequest(focusRequest)
            }
        }
    }

    override fun playCue(kind: BreathPhaseKind) {
        cue.play(cueRate(kind))
    }

    override fun release() {
        ramp.cancel()
        loop?.release()
        loop = null
        audioManager.abandonAudioFocusRequest(focusRequest)
        cue.release()
    }

    companion object {
        const val AMBIENT_VOLUME = 0.45f
        const val CUE_VOLUME = 0.35f
        const val FADE_IN_MS = 2_500L
        const val FADE_OUT_MS = 1_200L

        /** One clip, pitched per phase: rising for inhale, settling for exhale. */
        fun cueRate(kind: BreathPhaseKind): Float = when (kind) {
            BreathPhaseKind.Inhale -> 1.0f
            BreathPhaseKind.HoldIn -> 0.89f
            BreathPhaseKind.Exhale -> 0.75f
            BreathPhaseKind.HoldOut -> 0.84f
        }
    }
}
