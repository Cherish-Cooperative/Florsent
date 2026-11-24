/// Lets the phase cue (chime and haptic) fire exactly once per (session, cycle, phase), and only moving
/// forward: a view that reports the same phase twice, or a clock that steps back, can never make the
/// chime ring twice. Port of Android's `domain/PhaseCueGate.kt`.
public struct PhaseCueGate: Sendable {
    private var sessionId = Int.min
    private var lastKey: Int64 = -1

    public init() {}

    /// True if this phase has not been cued yet in [sessionId] and comes after the last one.
    public mutating func tryCue(sessionId: Int, cycleIndex: Int, phaseIndex: Int) -> Bool {
        if cycleIndex < 0 || phaseIndex < 0 { return false }
        if sessionId != self.sessionId {
            self.sessionId = sessionId
            lastKey = -1
        }
        let key = (Int64(cycleIndex) << 16) | Int64(phaseIndex)
        if key <= lastKey { return false }
        lastKey = key
        return true
    }

    public mutating func reset() {
        sessionId = .min
        lastKey = -1
    }
}
