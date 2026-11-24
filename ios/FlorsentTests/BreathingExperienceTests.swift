import CoreHaptics
import XCTest
import FlorsentKit
@testable import Florsent

@MainActor
final class BreathingSessionModelTests: XCTestCase {
    /// Walking a whole 4-4-4 session cues each of the 18 phases once, in order, then finishes once.
    func testCuesEveryPhaseOnceThenFinishes() {
        var clock = Date(timeIntervalSince1970: 0)
        let session = BreathingSessionModel(pattern: BreathingPatterns.equal, sessionId: 1, now: { clock })
        var cued: [BreathPhaseKind] = []
        var finished = 0
        session.onPhase = { cued.append($0.kind) }
        session.onFinish = { finished += 1 }
        session.start()
        for _ in 0..<800 { // 100 ms steps, with repeats to prove de-duplication
            clock += 0.1
            session.update()
            session.update()
        }
        XCTAssertEqual(cued.count, 18)
        XCTAssertEqual(Array(cued.prefix(3)), [.inhale, .holdIn, .exhale])
        XCTAssertEqual(finished, 1)
        XCTAssertTrue(session.display.finished)
    }

    func testPauseHoldsTheClock() {
        var clock = Date(timeIntervalSince1970: 0)
        let session = BreathingSessionModel(pattern: BreathingPatterns.box, sessionId: 1, now: { clock })
        session.start()
        clock += 3
        session.pause()
        clock += 600 // ten minutes in the background
        XCTAssertEqual(session.elapsedMs(at: clock), 3_000)
        session.resume()
        clock += 2
        XCTAssertEqual(session.elapsedMs(at: clock), 5_000)
        XCTAssertEqual(session.display.kind, .inhale)
        session.update()
        XCTAssertEqual(session.display.kind, .holdIn)
        session.stop()
    }

    func testFrozenSessionStaysStill() {
        let session = BreathingSessionModel(pattern: BreathingPatterns.relax478, sessionId: 1, frozenMs: 2_600)
        session.start()
        XCTAssertEqual(session.elapsedMs(at: .distantFuture), 2_600)
        XCTAssertEqual(session.display.secondsLeft, 2)
    }
}

final class BreathHapticsTests: XCTestCase {
    func testPatternsAreGentleAndValid() throws {
        for kind in BreathPhaseKind.allCases {
            XCTAssertNoThrow(try BreathHaptics.pattern(for: kind), "\(kind)")
            let spec = BreathHaptics.spec(for: kind)
            XCTAssertLessThanOrEqual(spec.tap, 0.45)
            XCTAssertLessThanOrEqual(spec.sharpness, 0.25)
            XCTAssertTrue(spec.curve.allSatisfy { $0.1 <= 0.45 })
            XCTAssertLessThanOrEqual(spec.swell, 1.5, "never longer than a breath change")
        }
        XCTAssertGreaterThan(BreathHaptics.spec(for: .inhale).curve.last!.1, BreathHaptics.spec(for: .inhale).curve.first!.1, "inhale rises")
        XCTAssertLessThan(BreathHaptics.spec(for: .exhale).curve.last!.1, BreathHaptics.spec(for: .exhale).curve.first!.1, "exhale ebbs")
        XCTAssertEqual(BreathHaptics.spec(for: .holdIn).swell, 0, "holds stay still")
    }
}

final class L10nTests: XCTestCase {
    func testPatternDurationAndGreeting() {
        XCTAssertFalse(L10n.patternDuration(BreathingPatterns.equal).contains("pattern."))
        XCTAssertFalse(L10n.greeting(hour: 8).contains("greeting."))
        XCTAssertFalse(L10n.format("breathe.secondsLeft", 3).contains("breathe."))
        XCTAssertTrue(L10n.rhythmForVoiceOver(BreathingPatterns.relax478).contains("4, 7, 8"))
    }
}
