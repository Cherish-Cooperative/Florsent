// Dumps the Android breathing timeline (app/src/main/java/app/florsent/domain/Breathing.kt, compiled
// read-only next to this file) as JSON, so other platforms can verify they match it sample by sample.
// Run through shared/tools/export_golden.sh; do not edit the output by hand.
import app.florsent.domain.BreathPhaseKind
import app.florsent.domain.BreathingPattern
import app.florsent.domain.BreathingPatterns
import app.florsent.domain.BreathingTimeline

private fun kindId(k: BreathPhaseKind) = when (k) {
    BreathPhaseKind.Inhale -> "inhale"
    BreathPhaseKind.HoldIn -> "holdIn"
    BreathPhaseKind.Exhale -> "exhale"
    BreathPhaseKind.HoldOut -> "holdOut"
}

private fun f(x: Float): String = x.toString().let { if (it.contains('E')) "%.9f".format(x.toDouble()) else it }

/** Every 250 ms through the session, plus 1 ms either side of every phase boundary. */
private fun sampleTimes(p: BreathingPattern): List<Long> {
    val times = sortedSetOf<Long>(-500L, -1L)
    var t = 0L
    while (t <= p.totalMs + 500) { times += t; t += 250 }
    var boundary = 0L
    repeat(p.cycles) {
        for (phase in p.phases) {
            times += listOf(boundary - 1, boundary, boundary + 1).filter { it >= 0 }
            boundary += phase.durationMs
        }
    }
    times += listOf(p.totalMs - 1, p.totalMs, p.totalMs + 1)
    return times.toList()
}

fun main() {
    val out = StringBuilder()
    out.append("{\"patterns\":[")
    BreathingPatterns.all.forEachIndexed { pi, p ->
        if (pi > 0) out.append(',')
        out.append("{\"id\":\"${p.id}\",\"cycles\":${p.cycles},\"cycleMs\":${p.cycleMs},\"totalMs\":${p.totalMs},")
        out.append("\"movingCycleMs\":${p.movingCycleMs},\"rhythmLabel\":\"${p.rhythmLabel}\",")
        out.append("\"isDefault\":${p.id == BreathingPatterns.default.id},\"phases\":[")
        out.append(p.phases.joinToString(",") { "{\"kind\":\"${kindId(it.kind)}\",\"durationMs\":${it.durationMs}}" })
        out.append("],\"samples\":[")
        out.append(sampleTimes(p).joinToString(",") { t ->
            val d = BreathingTimeline.displayAt(p, t)
            "{\"t\":$t,\"expansion\":${f(BreathingTimeline.expansionAt(p, t))},\"movingMs\":${BreathingTimeline.movingMsAt(p, t)}," +
                "\"progress\":${f(BreathingTimeline.sessionProgress(p, t))},\"cycle\":${d.cycleIndex},\"phase\":${d.phaseIndex}," +
                "\"kind\":\"${kindId(d.kind)}\",\"secondsLeft\":${d.secondsLeft},\"finished\":${d.finished}}"
        })
        out.append("]}")
    }
    out.append("],\"easeInOutSine\":[")
    out.append((0..40).joinToString(",") { i -> val x = -0.25f + i * 0.0375f; "{\"x\":${f(x)},\"y\":${f(BreathingTimeline.easeInOutSine(x))}}" })
    out.append("],\"idleExpansion\":[")
    out.append((0..64).joinToString(",") { i -> val t = i * 250L; "{\"t\":$t,\"e\":${f(BreathingTimeline.idleExpansion(t))}}" })
    out.append("]}")
    println(out)
}
