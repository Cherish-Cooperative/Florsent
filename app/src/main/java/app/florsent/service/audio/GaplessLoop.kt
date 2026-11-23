package app.florsent.service.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer

/** One pass through the loop file; an interface so the chaining can be tested without audio. */
interface LoopSegment {
    val isPlaying: Boolean
    fun start()
    fun pause()
    fun setVolume(volume: Float)

    /** Starts [next] seamlessly when this segment reaches its end. */
    fun setNext(next: LoopSegment?)
    fun setOnCompletion(listener: () -> Unit)
    fun release()
}

/**
 * Seamless looping of an ambience file. `MediaPlayer.isLooping` seeks back to the start at the end
 * of every pass, and the decoder has to re-prime (Opus pre-roll is 80 ms), which can be heard as a
 * short dropout every 42–48 s. Instead, two players are chained with setNextMediaPlayer: the next
 * pass is already prepared and the framework switches to it without a gap. When a pass completes,
 * it is released and a fresh one is queued behind the one now playing.
 *
 * Main thread only (completion callbacks arrive on the thread that created the players).
 */
class GaplessLoop(private val newSegment: () -> LoopSegment?) {

    private var current: LoopSegment? = newSegment()
    private var queued: LoopSegment? = null
    private var volume = 0f

    /** False if the file could not be opened at all. */
    val isValid: Boolean get() = current != null

    val isPlaying: Boolean get() = current?.isPlaying == true

    init {
        current?.let { prime(it) }
    }

    fun start() {
        current?.start()
    }

    fun pause() {
        current?.pause()
    }

    fun setVolume(v: Float) {
        volume = v
        current?.setVolume(v)
        queued?.setVolume(v)
    }

    fun release() {
        current?.setNext(null)
        current?.release()
        queued?.release()
        current = null
        queued = null
    }

    /** Queues the following pass behind [segment] and advances when [segment] ends. */
    private fun prime(segment: LoopSegment) {
        segment.setVolume(volume)
        val next = newSegment()?.also { it.setVolume(volume) }
        queued = next
        segment.setNext(next)
        segment.setOnCompletion {
            if (current !== segment) return@setOnCompletion
            segment.release()
            current = next
            queued = null
            next?.let { prime(it) }
        }
    }

    companion object {
        /** Loops `res/raw/[resId]` with [attributes] in one audio session. */
        fun forRaw(context: Context, resId: Int, attributes: AudioAttributes, sessionId: Int) = GaplessLoop {
            MediaPlayer.create(context, resId, attributes, sessionId)?.let(::MediaPlayerSegment)
        }
    }
}

private class MediaPlayerSegment(private val player: MediaPlayer) : LoopSegment {
    override val isPlaying: Boolean get() = player.isPlaying
    override fun start() = player.start()
    override fun pause() = player.pause()
    override fun setVolume(volume: Float) = player.setVolume(volume, volume)
    override fun setNext(next: LoopSegment?) = player.setNextMediaPlayer((next as? MediaPlayerSegment)?.player)
    override fun setOnCompletion(listener: () -> Unit) = player.setOnCompletionListener { listener() }
    override fun release() = player.release()
}
