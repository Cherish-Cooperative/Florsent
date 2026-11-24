import Foundation

// A line-by-line port of the Android app's `domain/Breathing.kt`. Both platforms are checked against
// the same golden file (shared/test-fixtures/breathing_golden.json), so change them together.

public enum BreathPhaseKind: String, Sendable, CaseIterable, Codable {
    case inhale, holdIn, exhale, holdOut

    /// Holding the breath: the whole picture stands still so the user stays still with it.
    public var isHold: Bool { self == .holdIn || self == .holdOut }
}

public struct BreathPhase: Sendable, Hashable {
    public let kind: BreathPhaseKind
    public let durationMs: Int64

    public init(_ kind: BreathPhaseKind, _ durationMs: Int64) {
        self.kind = kind
        self.durationMs = durationMs
    }
}

public struct BreathingPattern: Sendable, Hashable, Identifiable {
    public let id: String
    public let phases: [BreathPhase]
    public let cycles: Int
    public let cycleMs: Int64
    public let totalMs: Int64
    /// Breathing (non-hold) time per cycle; always > 0 because every pattern breathes in.
    public let movingCycleMs: Int64
    /// "4 · 7 · 8", "5.5 · 5.5"
    public let rhythmLabel: String

    public init(id: String, cycles: Int, phases: [BreathPhase]) {
        precondition(!phases.isEmpty && phases.allSatisfy { $0.durationMs > 0 }, "phases must be positive")
        precondition(cycles > 0, "cycles must be positive")
        self.id = id
        self.phases = phases
        self.cycles = cycles
        cycleMs = phases.reduce(0) { $0 + $1.durationMs }
        totalMs = cycleMs * Int64(cycles)
        movingCycleMs = phases.filter { !$0.kind.isHold }.reduce(0) { $0 + $1.durationMs }
        rhythmLabel = phases.map { phase in
            let seconds = Double(phase.durationMs) / 1000
            return seconds.truncatingRemainder(dividingBy: 1) == 0 ? String(Int(seconds)) : String(seconds)
        }.joined(separator: " · ")
    }

    /// Whole seconds of the session (for "6 rounds · about 1 min 12 s").
    public var totalSeconds: Int { Int(totalMs / 1000) }
}

public enum BreathingPatterns {
    /// 4-4-4: inhale, hold, exhale.
    public static let equal = BreathingPattern(
        id: "equal_444", cycles: 6, phases: [.init(.inhale, 4_000), .init(.holdIn, 4_000), .init(.exhale, 4_000)]
    )

    /// 4-4-4-4 box breathing.
    public static let box = BreathingPattern(
        id: "box_4444", cycles: 5,
        phases: [.init(.inhale, 4_000), .init(.holdIn, 4_000), .init(.exhale, 4_000), .init(.holdOut, 4_000)]
    )

    /// 4-7-8 relaxing breath; commonly limited to four cycles.
    public static let relax478 = BreathingPattern(
        id: "relax_478", cycles: 4, phases: [.init(.inhale, 4_000), .init(.holdIn, 7_000), .init(.exhale, 8_000)]
    )

    /// 5.5-5.5 resonance (coherent) breathing, about 5.5 breaths per minute.
    public static let resonance = BreathingPattern(
        id: "resonance_55", cycles: 7, phases: [.init(.inhale, 5_500), .init(.exhale, 5_500)]
    )

    public static let all: [BreathingPattern] = [equal, box, relax478, resonance]
    public static let `default`: BreathingPattern = equal

    public static func byId(_ id: String?) -> BreathingPattern {
        all.first { $0.id == id } ?? Self.default
    }
}

/// What the text layer shows; changes at most once per second.
public struct BreathDisplay: Sendable, Hashable {
    public let cycleIndex: Int
    public let phaseIndex: Int
    public let kind: BreathPhaseKind
    public let secondsLeft: Int
    public let finished: Bool

    /// Identifies the phase instance (cycle, phase); changes exactly when a new phase begins.
    public var phaseKey: Int { cycleIndex * 16 + phaseIndex }
}

public enum BreathingTimeline {

    /// Flower expansion for drawing: 0 = fully closed, 1 = fully open. Allocation-free per frame.
    public static func expansion(_ pattern: BreathingPattern, at elapsedMs: Int64) -> Float {
        if elapsedMs >= pattern.totalMs { return 0 }
        var t = max(elapsedMs, 0) % pattern.cycleMs
        for phase in pattern.phases {
            if t < phase.durationMs {
                let p = Float(t) / Float(phase.durationMs)
                switch phase.kind {
                case .inhale: return easeInOutSine(p)
                case .holdIn: return 1
                case .exhale: return 1 - easeInOutSine(p)
                case .holdOut: return 0
                }
            }
            t -= phase.durationMs
        }
        return 0
    }

    /// Elapsed time with every hold taken out. Drives everything that moves (petal drift, the
    /// progress ring), so the screen freezes while the user holds their breath.
    public static func movingMs(_ pattern: BreathingPattern, at elapsedMs: Int64) -> Int64 {
        let elapsed = min(max(elapsedMs, 0), pattern.totalMs)
        var moving = (elapsed / pattern.cycleMs) * pattern.movingCycleMs
        var t = elapsed % pattern.cycleMs
        for phase in pattern.phases {
            if t <= 0 { break }
            let inPhase = min(t, phase.durationMs)
            if !phase.kind.isHold { moving += inPhase }
            t -= inPhase
        }
        return moving
    }

    /// 0...1 progress through the whole session; stands still during holds.
    public static func sessionProgress(_ pattern: BreathingPattern, at elapsedMs: Int64) -> Float {
        let total = Float(pattern.movingCycleMs * Int64(pattern.cycles))
        return min(max(Float(movingMs(pattern, at: elapsedMs)) / total, 0), 1)
    }

    public static func display(_ pattern: BreathingPattern, at elapsedMs: Int64) -> BreathDisplay {
        if elapsedMs >= pattern.totalMs {
            let last = pattern.phases.count - 1
            return BreathDisplay(
                cycleIndex: pattern.cycles - 1, phaseIndex: last, kind: pattern.phases[last].kind,
                secondsLeft: 0, finished: true
            )
        }
        let elapsed = max(elapsedMs, 0)
        let cycle = Int(elapsed / pattern.cycleMs)
        var t = elapsed % pattern.cycleMs
        for (index, phase) in pattern.phases.enumerated() {
            if t < phase.durationMs {
                let secondsLeft = Int((Double(phase.durationMs - t) / 1000).rounded(.up))
                return BreathDisplay(
                    cycleIndex: cycle, phaseIndex: index, kind: phase.kind, secondsLeft: secondsLeft, finished: false
                )
            }
            t -= phase.durationMs
        }
        preconditionFailure("unreachable: elapsed within cycle")
    }

    /// The next elapsed time (> [elapsedMs]) at which [display] changes: a new second, a new phase or
    /// the end. iOS sleeps until then instead of polling every frame. Nil once finished.
    public static func nextChangeMs(_ pattern: BreathingPattern, after elapsedMs: Int64) -> Int64? {
        let elapsed = max(elapsedMs, 0)
        if elapsed >= pattern.totalMs { return nil }
        var t = elapsed % pattern.cycleMs
        for phase in pattern.phases {
            if t < phase.durationMs {
                let remaining = phase.durationMs - t
                let next = (remaining - 1) / 1000 * 1000 // largest whole second strictly below `remaining`
                return elapsed + (remaining - next)
            }
            t -= phase.durationMs
        }
        return nil
    }

    /// Idle "resting breath" for previews: a slow 8-second sine between 0.25 and 0.55.
    public static func idleExpansion(at elapsedMs: Int64) -> Float {
        let phase = Float(elapsedMs % idlePeriodMs) / Float(idlePeriodMs)
        return 0.4 - 0.15 * jvmCos(2 * Float.pi * phase)
    }

    public static func easeInOutSine(_ t: Float) -> Float {
        let x = min(max(t, 0), 1)
        return (1 - jvmCos(Float.pi * x)) / 2
    }

    static let idlePeriodMs: Int64 = 8_000

    /// kotlin.math.cos(Float) on the JVM computes in Double; doing the same keeps us bit-close.
    @inline(__always) static func jvmCos(_ x: Float) -> Float { Float(cos(Double(x))) }
}
