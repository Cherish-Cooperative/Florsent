package app.florsent.domain

import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.sin

enum class BreathPhaseKind {
    Inhale, HoldIn, Exhale, HoldOut;

    /** Holding the breath: the whole picture stands still so the user stays still with it. */
    val isHold: Boolean get() = this == HoldIn || this == HoldOut
}

data class BreathPhase(val kind: BreathPhaseKind, val durationMs: Long)

data class BreathingPattern(
    val id: String,
    val phases: List<BreathPhase>,
    val cycles: Int,
) {
    init {
        require(phases.isNotEmpty() && phases.all { it.durationMs > 0 }) { "phases must be positive" }
        require(cycles > 0) { "cycles must be positive" }
    }

    val cycleMs: Long = phases.sumOf { it.durationMs }
    val totalMs: Long = cycleMs * cycles

    /** Breathing (non-hold) time per cycle; always > 0 because every pattern breathes in. */
    val movingCycleMs: Long = phases.filterNot { it.kind.isHold }.sumOf { it.durationMs }

    /** "4 · 7 · 8", "5.5 · 5.5" */
    val rhythmLabel: String = phases.joinToString(" · ") { phase ->
        val seconds = phase.durationMs / 1000.0
        if (seconds % 1.0 == 0.0) seconds.toInt().toString() else seconds.toString()
    }
}

object BreathingPatterns {
    private fun pattern(id: String, cycles: Int, vararg phases: Pair<BreathPhaseKind, Long>) =
        BreathingPattern(id, phases.map { (kind, ms) -> BreathPhase(kind, ms) }, cycles)

    /** 4-4-4: inhale, hold, exhale. */
    val Equal = pattern(
        "equal_444", cycles = 6,
        BreathPhaseKind.Inhale to 4_000, BreathPhaseKind.HoldIn to 4_000, BreathPhaseKind.Exhale to 4_000,
    )

    /** 4-4-4-4 box breathing. */
    val Box = pattern(
        "box_4444", cycles = 5,
        BreathPhaseKind.Inhale to 4_000, BreathPhaseKind.HoldIn to 4_000,
        BreathPhaseKind.Exhale to 4_000, BreathPhaseKind.HoldOut to 4_000,
    )

    /** 4-7-8 relaxing breath; commonly limited to four cycles. */
    val Relax478 = pattern(
        "relax_478", cycles = 4,
        BreathPhaseKind.Inhale to 4_000, BreathPhaseKind.HoldIn to 7_000, BreathPhaseKind.Exhale to 8_000,
    )

    /** 5.5-5.5 resonance (coherent) breathing, about 5.5 breaths per minute. */
    val Resonance = pattern(
        "resonance_55", cycles = 7,
        BreathPhaseKind.Inhale to 5_500, BreathPhaseKind.Exhale to 5_500,
    )

    val all: List<BreathingPattern> = listOf(Equal, Box, Relax478, Resonance)
    val default: BreathingPattern = Equal

    fun byId(id: String?): BreathingPattern = all.firstOrNull { it.id == id } ?: default
}

/** What the text layer shows; changes at most once per second. */
data class BreathDisplay(
    val cycleIndex: Int,
    val phaseIndex: Int,
    val kind: BreathPhaseKind,
    val secondsLeft: Int,
    val finished: Boolean,
)

object BreathingTimeline {

    /**
     * Flower expansion for the draw phase: 0 = fully closed, 1 = fully open. Allocation-free so it
     * can run every frame.
     */
    fun expansionAt(pattern: BreathingPattern, elapsedMs: Long): Float {
        if (elapsedMs >= pattern.totalMs) return 0f
        var t = elapsedMs.coerceAtLeast(0) % pattern.cycleMs
        val phases = pattern.phases
        for (i in phases.indices) { // index loop: no Iterator allocation per frame
            val phase = phases[i]
            if (t < phase.durationMs) {
                val p = t.toFloat() / phase.durationMs
                return when (phase.kind) {
                    BreathPhaseKind.Inhale -> breathEase(p)
                    BreathPhaseKind.HoldIn -> 1f
                    BreathPhaseKind.Exhale -> 1f - breathEase(p)
                    BreathPhaseKind.HoldOut -> 0f
                }
            }
            t -= phase.durationMs
        }
        return 0f
    }

    /**
     * Elapsed time with every hold taken out. Drives everything that moves (petal drift, the
     * progress ring), so the screen freezes while the user holds their breath. Allocation-free.
     */
    fun movingMsAt(pattern: BreathingPattern, elapsedMs: Long): Long {
        val elapsed = elapsedMs.coerceIn(0, pattern.totalMs)
        var moving = (elapsed / pattern.cycleMs) * pattern.movingCycleMs
        var t = elapsed % pattern.cycleMs
        val phases = pattern.phases
        for (i in phases.indices) {
            if (t <= 0) break
            val phase = phases[i]
            val inPhase = minOf(t, phase.durationMs)
            if (!phase.kind.isHold) moving += inPhase
            t -= inPhase
        }
        return moving
    }

    /** 0..1 progress through the whole session; stands still during holds. */
    fun sessionProgress(pattern: BreathingPattern, elapsedMs: Long): Float =
        (movingMsAt(pattern, elapsedMs).toFloat() / (pattern.movingCycleMs * pattern.cycles)).coerceIn(0f, 1f)

    fun displayAt(pattern: BreathingPattern, elapsedMs: Long): BreathDisplay {
        if (elapsedMs >= pattern.totalMs) {
            val last = pattern.phases.lastIndex
            return BreathDisplay(pattern.cycles - 1, last, pattern.phases[last].kind, 0, finished = true)
        }
        val elapsed = elapsedMs.coerceAtLeast(0)
        val cycle = (elapsed / pattern.cycleMs).toInt()
        var t = elapsed % pattern.cycleMs
        pattern.phases.forEachIndexed { index, phase ->
            if (t < phase.durationMs) {
                val secondsLeft = ceil((phase.durationMs - t) / 1000.0).toInt()
                return BreathDisplay(cycle, index, phase.kind, secondsLeft, finished = false)
            }
            t -= phase.durationMs
        }
        error("unreachable: elapsed within cycle")
    }

    /** Idle "resting breath" for previews: a slow 8-second sine between 0.25 and 0.55. */
    fun idleExpansion(elapsedMs: Long): Float {
        val phase = (elapsedMs % IDLE_PERIOD_MS).toFloat() / IDLE_PERIOD_MS
        return 0.4f - 0.15f * cos(2f * PI.toFloat() * phase)
    }

    /**
     * The flower's curve within one inhale or exhale: it moves from the first frame and settles
     * softly at the end, like the air itself. A pure ease-in-out started at zero speed, so the
     * flower seemed to answer half a beat after the word changed.
     */
    fun breathEase(t: Float): Float {
        val x = t.coerceIn(0f, 1f)
        return 0.65f * sin(PI.toFloat() / 2f * x) + 0.35f * easeInOutSine(x)
    }

    fun easeInOutSine(t: Float): Float {
        val x = t.coerceIn(0f, 1f)
        return (1f - cos(PI.toFloat() * x)) / 2f
    }

    private const val IDLE_PERIOD_MS = 8_000L
}
