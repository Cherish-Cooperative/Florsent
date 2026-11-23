package app.florsent.service.audio

import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class WavReaderTest {

    private fun wav(rate: Int = 22_050, channels: Int = 1, bits: Int = 16, format: Int = 1, extra: Boolean = false, samples: Int = 10): ByteArray {
        val out = ByteArrayOutputStream()
        fun le(n: Int, size: Int) = ByteBuffer.allocate(size).order(ByteOrder.LITTLE_ENDIAN).apply {
            if (size == 2) putShort(n.toShort()) else putInt(n)
        }.array()
        val data = ByteArray(samples * channels * bits / 8) { it.toByte() }
        out.write("RIFF".toByteArray()); out.write(le(0, 4)); out.write("WAVE".toByteArray())
        if (extra) { out.write("LIST".toByteArray()); out.write(le(3, 4)); out.write(byteArrayOf(1, 2, 3, 0)) } // odd size + pad
        out.write("fmt ".toByteArray()); out.write(le(16, 4))
        out.write(le(format, 2)); out.write(le(channels, 2)); out.write(le(rate, 4))
        out.write(le(rate * channels * bits / 8, 4)); out.write(le(channels * bits / 8, 2)); out.write(le(bits, 2))
        out.write("data".toByteArray()); out.write(le(data.size, 4)); out.write(data)
        return out.toByteArray()
    }

    @Test
    fun `reads 16-bit pcm, skipping other chunks and padding`() {
        val clip = WavReader.read(wav(extra = true))
        assertNotNull(clip)
        assertEquals(22_050, clip!!.sampleRate)
        assertEquals(1, clip.channels)
        assertEquals(20, clip.data.size)
    }

    @Test
    fun `rejects what a static 16-bit track cannot play`() {
        assertNull(WavReader.read(wav(bits = 8)))
        assertNull(WavReader.read(wav(format = 3)))
        assertNull(WavReader.read(wav(channels = 6)))
        assertNull(WavReader.read("not a wave file at all".toByteArray()))
        assertNull(WavReader.read(wav().copyOf(30)))
    }

    @Test
    fun `the bundled cue is a short mono 16-bit clip`() {
        val res = listOf(File("src/main/res"), File("app/src/main/res")).first { it.isDirectory }
        val clip = WavReader.read(File(res, "raw/cue_soft.wav").readBytes())
        assertNotNull(clip)
        assertEquals(1, clip!!.channels)
        val seconds = clip.data.size / 2.0 / clip.sampleRate
        assertEquals(1.6, seconds, 0.05)
    }

    @Test
    fun `cue pitch rises for inhale and settles for exhale`() {
        val rates = app.florsent.domain.BreathPhaseKind.entries.map(BreathingSoundscape::cueRate)
        rates.forEach { assert(it in 0.5f..1f) }
        assertEquals(1f, BreathingSoundscape.cueRate(app.florsent.domain.BreathPhaseKind.Inhale), 0f)
    }
}
