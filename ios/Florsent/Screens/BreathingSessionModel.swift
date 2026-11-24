import FlorsentKit
import Foundation
import Observation

/// Clock and cues for one breathing session.
///
/// Elapsed time is accumulated only while running, so leaving the app pauses the session instead of
/// skipping ahead (as on Android). The text layer (`display`) changes at most once a second; instead
/// of polling every frame, a task sleeps until `BreathingTimeline.nextChangeMs`. The flower reads
/// `elapsedMs(at:)` from its own `TimelineView`. Each phase is cued (chime, haptic, VoiceOver
/// announcement) once, through `PhaseCueGate`.
@MainActor @Observable
final class BreathingSessionModel {
    let pattern: BreathingPattern
    let sessionId: Int
    private(set) var display: BreathDisplay
    private(set) var isRunning = false

    @ObservationIgnored var onPhase: (BreathDisplay) -> Void = { _ in }
    @ObservationIgnored var onFinish: () -> Void = {}
    @ObservationIgnored private var accumulated: TimeInterval = 0
    @ObservationIgnored private var resumedAt: Date?
    @ObservationIgnored private var frozenMs: Int64?
    @ObservationIgnored private var gate = PhaseCueGate()
    @ObservationIgnored private var ticker: Task<Void, Never>?
    @ObservationIgnored private var finished = false
    @ObservationIgnored private let now: () -> Date

    init(pattern: BreathingPattern, sessionId: Int, frozenMs: Int64? = nil, now: @escaping () -> Date = Date.init) {
        self.pattern = pattern
        self.sessionId = sessionId
        self.frozenMs = frozenMs
        self.now = now
        display = BreathingTimeline.display(pattern, at: frozenMs ?? 0)
    }

    func elapsedMs(at date: Date) -> Int64 {
        if let frozenMs { return frozenMs }
        var seconds = accumulated
        if let resumedAt { seconds += max(date.timeIntervalSince(resumedAt), 0) }
        return Int64(seconds * 1000)
    }

    func start() {
        guard !finished else { return }
        if frozenMs != nil { isRunning = true; return } // screenshot mode: still, no cues
        resume()
    }

    func resume() {
        guard !finished, resumedAt == nil, frozenMs == nil else { return }
        resumedAt = now()
        isRunning = true
        update()
        scheduleTicks()
    }

    func pause() {
        guard let resumedAt else { return }
        accumulated += max(now().timeIntervalSince(resumedAt), 0)
        self.resumedAt = nil
        isRunning = false
        ticker?.cancel()
        ticker = nil
    }

    /// Ends the session (finished or "End early"); safe to call more than once.
    func stop() {
        pause()
        finished = true
    }

    /// Recomputes the display for the current time and fires the phase cue once per phase.
    func update() {
        let elapsed = elapsedMs(at: now())
        let next = BreathingTimeline.display(pattern, at: elapsed)
        if next != display { display = next }
        if next.finished {
            guard !finished else { return }
            stop()
            onFinish()
            return
        }
        if gate.tryCue(sessionId: sessionId, cycleIndex: next.cycleIndex, phaseIndex: next.phaseIndex) {
            onPhase(next)
        }
    }

    private func scheduleTicks() {
        ticker?.cancel()
        ticker = Task { [weak self] in
            while !Task.isCancelled {
                guard let self else { return }
                let elapsed = self.elapsedMs(at: self.now())
                guard let next = BreathingTimeline.nextChangeMs(self.pattern, after: elapsed) else {
                    self.update()
                    return
                }
                try? await Task.sleep(for: .milliseconds(max(next - elapsed, 1)))
                if Task.isCancelled { return }
                self.update()
            }
        }
    }
}
