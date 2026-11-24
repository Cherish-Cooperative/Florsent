import XCTest
@testable import FlorsentCore

/// Every sample in the Android-generated golden file must match the Swift timeline.
final class GoldenTimelineTests: XCTestCase {
    private var golden: Golden!

    override func setUpWithError() throws {
        golden = try Golden.load()
        XCTAssertEqual(golden.schemaVersion, 1)
    }

    func testPatternsMatchAndroid() throws {
        XCTAssertEqual(golden.patterns.map(\.id), BreathingPatterns.all.map(\.id))
        for g in golden.patterns {
            let p = BreathingPatterns.byId(g.id)
            XCTAssertEqual(p.id, g.id)
            XCTAssertEqual(p.cycles, g.cycles, g.id)
            XCTAssertEqual(p.cycleMs, g.cycleMs, g.id)
            XCTAssertEqual(p.totalMs, g.totalMs, g.id)
            XCTAssertEqual(p.movingCycleMs, g.movingCycleMs, g.id)
            XCTAssertEqual(p.rhythmLabel, g.rhythmLabel, g.id)
            XCTAssertEqual(p.phases.map(\.kind.rawValue), g.phases.map(\.kind), g.id)
            XCTAssertEqual(p.phases.map(\.durationMs), g.phases.map(\.durationMs), g.id)
            XCTAssertEqual(g.isDefault, p == BreathingPatterns.default, g.id)
        }
    }

    func testEverySampleMatchesAndroid() {
        var checked = 0
        for g in golden.patterns {
            let p = BreathingPatterns.byId(g.id)
            for s in g.samples {
                let where_ = "\(g.id) t=\(s.t)"
                XCTAssertEqual(BreathingTimeline.expansion(p, at: s.t), s.expansion, accuracy: 1e-5, where_)
                XCTAssertEqual(BreathingTimeline.movingMs(p, at: s.t), s.movingMs, where_)
                XCTAssertEqual(BreathingTimeline.sessionProgress(p, at: s.t), s.progress, accuracy: 1e-5, where_)
                let d = BreathingTimeline.display(p, at: s.t)
                XCTAssertEqual(d.cycleIndex, s.cycle, where_)
                XCTAssertEqual(d.phaseIndex, s.phase, where_)
                XCTAssertEqual(d.kind.rawValue, s.kind, where_)
                XCTAssertEqual(d.secondsLeft, s.secondsLeft, where_)
                XCTAssertEqual(d.finished, s.finished, where_)
                checked += 1
            }
        }
        XCTAssertGreaterThan(checked, 1_000)
    }

    func testEasingAndIdleMatchAndroid() {
        for s in golden.easeInOutSine {
            XCTAssertEqual(BreathingTimeline.easeInOutSine(s.x), s.y, accuracy: 1e-6, "x=\(s.x)")
        }
        for s in golden.idleExpansion {
            XCTAssertEqual(BreathingTimeline.idleExpansion(at: s.t), s.e, accuracy: 1e-6, "t=\(s.t)")
        }
    }

    func testSoundTuningMatchesAndroid() {
        let s = golden.sound
        XCTAssertEqual(SoundTuning.ambientVolume, s.ambientVolume)
        XCTAssertEqual(SoundTuning.cueVolume, s.cueVolume)
        XCTAssertEqual(SoundTuning.ambientFadeInSeconds * 1000, Double(s.ambientFadeInMs))
        XCTAssertEqual(SoundTuning.ambientFadeOutSeconds * 1000, Double(s.ambientFadeOutMs))
        XCTAssertEqual(SoundTuning.fadeRainTargetVolume, s.fadeRainTargetVolume)
        for kind in BreathPhaseKind.allCases {
            XCTAssertEqual(SoundTuning.cueRate(kind), s.cueRate[kind.rawValue], kind.rawValue)
        }
    }

    func testGroundingCountsMatchAndroid() throws {
        for (lang, copy) in golden.copy {
            XCTAssertEqual(copy.arrays["grounding_prompts"]?.count, GroundingPrompts.dayCount, lang)
            XCTAssertEqual(copy.arrays["grounding_prompts_bedtime"]?.count, GroundingPrompts.nightCount, lang)
        }
        XCTAssertEqual(GroundingPrompts.dayCount + GroundingPrompts.nightCount, 20)
    }

    /// iOS-only helper: the display changes exactly at nextChangeMs and never in between.
    func testNextChangeIsExact() {
        for p in BreathingPatterns.all {
            var t: Int64 = 0
            var steps = 0
            while let next = BreathingTimeline.nextChangeMs(p, after: t) {
                XCTAssertGreaterThan(next, t)
                XCTAssertEqual(BreathingTimeline.display(p, at: t), BreathingTimeline.display(p, at: next - 1), "\(p.id) \(t)")
                XCTAssertNotEqual(BreathingTimeline.display(p, at: next - 1), BreathingTimeline.display(p, at: next), "\(p.id) \(next)")
                t = next
                steps += 1
            }
            XCTAssertEqual(t, p.totalMs, p.id)
            XCTAssertTrue(BreathingTimeline.display(p, at: t).finished)
            XCTAssertGreaterThanOrEqual(steps, p.totalSeconds)
        }
    }
}
