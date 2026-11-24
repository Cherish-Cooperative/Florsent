import XCTest
@testable import FlorsentCore

final class PhaseCueGateTests: XCTestCase {
    func testCuesEachPhaseOnceAndOnlyForward() {
        var gate = PhaseCueGate()
        XCTAssertTrue(gate.tryCue(sessionId: 1, cycleIndex: 0, phaseIndex: 0))
        XCTAssertFalse(gate.tryCue(sessionId: 1, cycleIndex: 0, phaseIndex: 0), "same phase twice")
        XCTAssertTrue(gate.tryCue(sessionId: 1, cycleIndex: 0, phaseIndex: 1))
        XCTAssertFalse(gate.tryCue(sessionId: 1, cycleIndex: 0, phaseIndex: 0), "clock stepped back")
        XCTAssertTrue(gate.tryCue(sessionId: 1, cycleIndex: 1, phaseIndex: 0))
        XCTAssertFalse(gate.tryCue(sessionId: 1, cycleIndex: -1, phaseIndex: 0))
    }

    func testNewSessionStartsOver() {
        var gate = PhaseCueGate()
        XCTAssertTrue(gate.tryCue(sessionId: 1, cycleIndex: 3, phaseIndex: 2))
        XCTAssertTrue(gate.tryCue(sessionId: 2, cycleIndex: 0, phaseIndex: 0))
        gate.reset()
        XCTAssertTrue(gate.tryCue(sessionId: 2, cycleIndex: 0, phaseIndex: 0))
    }
}

final class GroundingTests: XCTestCase {
    func testNextIndexNeverRepeatsAndCoversAll() {
        for current in 0..<12 {
            var seen = Set<Int>()
            for random in -30..<30 {
                let next = GroundingPrompts.nextIndex(after: current, count: 12, random: random)
                XCTAssertNotEqual(next, current)
                XCTAssertTrue((0..<12).contains(next))
                seen.insert(next)
            }
            XCTAssertEqual(seen.count, 11)
        }
        XCTAssertEqual(GroundingPrompts.nextIndex(after: 0, count: 1, random: 5), 0)
    }

    func testKeysAndNight() {
        XCTAssertEqual(GroundingPrompts.key(night: false, index: 0), "grounding.day.1")
        XCTAssertEqual(GroundingPrompts.key(night: true, index: 7), "grounding.night.8")
        XCTAssertTrue(GroundingPrompts.isNight(hour: 23))
        XCTAssertTrue(GroundingPrompts.isNight(hour: 2))
        XCTAssertFalse(GroundingPrompts.isNight(hour: 9))
    }
}

final class SharedStoreTests: XCTestCase {
    func testBudgetAndBreathingRequest() throws {
        let suite = "florsent.tests.\(UUID().uuidString)"
        let defaults = try XCTUnwrap(UserDefaults(suiteName: suite))
        defer { defaults.removePersistentDomain(forName: suite) }
        let store = SharedStore(defaults: defaults)
        XCTAssertFalse(store.isOverBudget())
        let now = Date(timeIntervalSince1970: 1_000_000)
        store.updateGuidance { _ = $0.reach(GuidanceEvent(.daily, .notice), now: now) }
        XCTAssertTrue(store.isOverBudget(now: now))
        XCTAssertEqual(store.guidanceState.over, [.daily])

        store.requestBreathing(now: now)
        XCTAssertTrue(store.takeBreathingRequest(now: now.addingTimeInterval(60)))
        XCTAssertFalse(store.takeBreathingRequest(now: now.addingTimeInterval(61)), "consumed")
        store.requestBreathing(now: now)
        XCTAssertFalse(store.takeBreathingRequest(now: now.addingTimeInterval(3_600)), "stale")
    }

    func testFadeRainCurve() {
        XCTAssertEqual(SoundTuning.fadeRainVolume(progress: 0), 0)
        XCTAssertEqual(SoundTuning.fadeRainVolume(progress: 0.5), 0.15, accuracy: 1e-6)
        XCTAssertEqual(SoundTuning.fadeRainVolume(progress: 2), 0.6, accuracy: 1e-6)
    }
}
