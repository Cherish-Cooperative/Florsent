package app.florsent.ui.theme

import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * WCAG 2.x contrast checks on plain ARGB ints (no Android types, so the brand palettes are verified
 * by JVM unit tests and dynamic palettes are audited at runtime in debug builds).
 */
object ContrastAudit {

    const val AA_TEXT = 4.5

    /** A text/background pair as used in the UI; [overlayAlpha] tints [background] over [base]. */
    data class Pair(
        val name: String,
        val text: Int,
        val background: Int,
        val base: Int? = null,
        val overlayAlpha: Float = 1f,
    )

    data class Result(val name: String, val ratio: Double) {
        val passes: Boolean get() = ratio >= AA_TEXT
    }

    fun ratio(foreground: Int, background: Int): Double {
        val a = relativeLuminance(foreground)
        val b = relativeLuminance(background)
        return (max(a, b) + 0.05) / (min(a, b) + 0.05)
    }

    fun audit(pairs: List<Pair>): List<Result> = pairs.map { p ->
        val bg = if (p.base != null) blend(p.background, p.base, p.overlayAlpha) else p.background
        Result(p.name, ratio(p.text, bg))
    }

    /** Composites [top] at [alpha] over an opaque [bottom]. */
    fun blend(top: Int, bottom: Int, alpha: Float): Int {
        fun ch(c: Int, shift: Int) = (c shr shift) and 0xFF
        fun mix(shift: Int) = (ch(top, shift) * alpha + ch(bottom, shift) * (1 - alpha)).toInt().coerceIn(0, 255)
        return (0xFF shl 24) or (mix(16) shl 16) or (mix(8) shl 8) or mix(0)
    }

    private fun relativeLuminance(argb: Int): Double {
        fun channel(shift: Int): Double {
            val c = ((argb shr shift) and 0xFF) / 255.0
            return if (c <= 0.03928) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
        }
        return 0.2126 * channel(16) + 0.7152 * channel(8) + 0.0722 * channel(0)
    }
}
