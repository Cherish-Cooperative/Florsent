import Foundation

/// shared/test-fixtures/breathing_golden.json, generated from the Android sources.
struct Golden: Decodable {
    struct Phase: Decodable { let kind: String; let durationMs: Int64 }
    struct Sample: Decodable {
        let t: Int64, expansion: Float, movingMs: Int64, progress: Float
        let cycle: Int, phase: Int, kind: String, secondsLeft: Int, finished: Bool
    }
    struct Pattern: Decodable {
        let id: String, cycles: Int, cycleMs: Int64, totalMs: Int64, movingCycleMs: Int64
        let rhythmLabel: String, isDefault: Bool, phases: [Phase], samples: [Sample]
    }
    struct XY: Decodable { let x: Float; let y: Float }
    struct IdleSample: Decodable { let t: Int64; let e: Float }
    struct Sound: Decodable {
        let ambientVolume: Float, cueVolume: Float, ambientFadeInMs: Int, ambientFadeOutMs: Int
        let cueRate: [String: Float], fadeRainTargetVolume: Float, ambientLoopFrames: [String: Int]
    }
    struct Copy: Decodable {
        let strings: [String: String]
        let arrays: [String: [String]]
        let plurals: [String: [String: String]]
    }

    let schemaVersion: Int
    let patterns: [Pattern]
    let easeInOutSine: [XY]
    let idleExpansion: [IdleSample]
    let sound: Sound
    let copy: [String: Copy]

    static let url = URL(fileURLWithPath: #filePath)
        .deletingLastPathComponent() // FlorsentCoreTests
        .deletingLastPathComponent() // Tests
        .deletingLastPathComponent() // FlorsentKit
        .deletingLastPathComponent() // ios
        .deletingLastPathComponent() // repo root
        .appendingPathComponent("shared/test-fixtures/breathing_golden.json")

    static func load() throws -> Golden {
        try JSONDecoder().decode(Golden.self, from: Data(contentsOf: url))
    }
}
