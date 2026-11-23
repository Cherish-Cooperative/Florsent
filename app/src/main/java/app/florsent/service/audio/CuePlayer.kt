package app.florsent.service.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Handler
import android.os.HandlerThread
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** 16-bit PCM read from a WAV file. */
class PcmClip(val sampleRate: Int, val channels: Int, val data: ByteArray)

/** Minimal RIFF/WAVE reader for the bundled 16-bit PCM cue; null for anything else. */
object WavReader {
    fun read(bytes: ByteArray): PcmClip? {
        if (bytes.size < 12) return null
        val b = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        if (tag(bytes, 0) != "RIFF" || tag(bytes, 8) != "WAVE") return null
        var pos = 12
        var rate = 0
        var channels = 0
        var bits = 0
        var format = 0
        while (pos + 8 <= bytes.size) {
            val id = tag(bytes, pos)
            val size = b.getInt(pos + 4)
            val body = pos + 8
            if (size < 0 || body + size > bytes.size) return null
            when (id) {
                "fmt " -> {
                    format = b.getShort(body).toInt() and 0xFFFF
                    channels = b.getShort(body + 2).toInt()
                    rate = b.getInt(body + 4)
                    bits = b.getShort(body + 14).toInt()
                }
                "data" -> {
                    if (format != 1 || bits != 16 || channels !in 1..2 || rate <= 0) return null
                    return PcmClip(rate, channels, bytes.copyOfRange(body, body + size))
                }
            }
            pos = body + size + (size and 1) // chunks are word-aligned
        }
        return null
    }

    private fun tag(bytes: ByteArray, at: Int) = String(bytes, at, 4, Charsets.US_ASCII)
}

/**
 * The phase chime. A static AudioTrack holds the decoded clip, so playing it is just a restart: no
 * decoding, no new track per chime. It asks for PERFORMANCE_MODE_POWER_SAVING, which routes it to the
 * same deep-buffer output as the ambience MediaPlayer instead of waking the low-latency output
 * (SoundPool's default) on every chime, a reconfiguration that can make the ambience stutter. All
 * calls run on a dedicated audio thread, never the UI thread.
 */
class CuePlayer(context: Context, attributes: AudioAttributes, private val volume: Float) {

    private val thread = HandlerThread("florsent-cue").apply { start() }
    private val handler = Handler(thread.looper)
    private var track: AudioTrack? = null
    private var sampleRate = 0

    init {
        val appContext = context.applicationContext
        handler.post {
            val clip = appContext.resources.openRawResource(app.florsent.R.raw.cue_soft).use { WavReader.read(it.readBytes()) }
                ?: return@post
            sampleRate = clip.sampleRate
            track = runCatching {
                AudioTrack.Builder()
                    .setAudioAttributes(attributes)
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(clip.sampleRate)
                            .setChannelMask(if (clip.channels == 1) AudioFormat.CHANNEL_OUT_MONO else AudioFormat.CHANNEL_OUT_STEREO)
                            .build(),
                    )
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .setBufferSizeInBytes(clip.data.size)
                    .setPerformanceMode(AudioTrack.PERFORMANCE_MODE_POWER_SAVING)
                    .build()
                    .apply {
                        write(clip.data, 0, clip.data.size)
                        setVolume(volume)
                    }
            }.getOrNull()
        }
    }

    /** Plays the chime at [rate] × its natural pitch (0.5–1.0). Restarts it if still ringing. */
    fun play(rate: Float) {
        handler.post {
            val t = track ?: return@post
            if (t.state != AudioTrack.STATE_INITIALIZED) return@post
            runCatching {
                if (t.playState == AudioTrack.PLAYSTATE_PLAYING) t.stop()
                t.reloadStaticData()
                t.playbackRate = (sampleRate * rate.coerceIn(0.5f, 1f)).toInt()
                t.play()
            }
        }
    }

    fun release() {
        handler.post {
            track?.release()
            track = null
        }
        thread.quitSafely()
    }
}
