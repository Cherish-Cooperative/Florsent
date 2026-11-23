package app.florsent.domain

/**
 * Lets the phase cue (chime and haptic tick) fire exactly once per (session, cycle, phase), and only
 * moving forward. It lives in the ViewModel, so it survives recomposition, a recreated breathing
 * screen and configuration changes; a UI that reports the same phase twice, or a clock that steps
 * back, can never make the chime ring twice.
 */
class PhaseCueGate {

    private var sessionId = Int.MIN_VALUE
    private var lastKey = -1L

    /** True if this phase has not been cued yet in [sessionId] and comes after the last one. */
    fun tryCue(sessionId: Int, cycleIndex: Int, phaseIndex: Int): Boolean {
        if (cycleIndex < 0 || phaseIndex < 0) return false
        if (sessionId != this.sessionId) {
            this.sessionId = sessionId
            lastKey = -1L
        }
        val key = (cycleIndex.toLong() shl 16) or phaseIndex.toLong()
        if (key <= lastKey) return false
        lastKey = key
        return true
    }

    fun reset() {
        sessionId = Int.MIN_VALUE
        lastKey = -1L
    }
}
