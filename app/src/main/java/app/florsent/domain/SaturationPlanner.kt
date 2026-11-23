package app.florsent.domain

import kotlin.math.roundToInt

/** Set [packageName]'s layer saturation to [saturation] (0 = gray, 100 = untouched). */
data class SaturationCommand(val packageName: String, val saturation: Int)

/**
 * Pure planning for the per-app desaturation layer. Given what is currently applied and what the
 * guidance wants, it produces the commands to get there, restores always first, so a package is
 * never left desaturated once it stops being the target.
 */
object SaturationPlanner {
    const val FULL = 100

    /** Quantization step; keeps shell invocations to a handful per fade instead of one per frame. */
    const val STEP = 5

    /** Guidance level (0..1, already eased) to saturation (100..0), quantized to [STEP]. */
    fun saturationFor(level: Float): Int {
        val raw = FULL * (1f - level.coerceIn(0f, 1f))
        return ((raw / STEP).roundToInt() * STEP).coerceIn(0, FULL)
    }

    /** What should be desaturated right now; empty means "everything at 100". */
    fun desired(targetPackage: String?, level: Float, enabled: Boolean): Map<String, Int> {
        if (!enabled || targetPackage.isNullOrEmpty()) return emptyMap()
        val saturation = saturationFor(level)
        return if (saturation >= FULL) emptyMap() else mapOf(targetPackage to saturation)
    }

    /**
     * Commands to move from [applied] to [desired]. Every applied package that is no longer desired
     * gets an explicit restore to [FULL], and those restores come before any new desaturation.
     */
    fun commands(applied: Map<String, Int>, desired: Map<String, Int>): List<SaturationCommand> {
        val restores = applied.keys
            .filter { it !in desired }
            .sorted()
            .map { SaturationCommand(it, FULL) }
        val changes = desired.entries
            .filter { (pkg, level) -> applied[pkg] != level }
            .sortedBy { it.key }
            .map { (pkg, level) -> SaturationCommand(pkg, level) }
        return restores + changes
    }
}
