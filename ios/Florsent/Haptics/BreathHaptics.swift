import CoreHaptics
import FlorsentKit
import UIKit

/// Gentle Core Haptics cues for each breath phase, felt rather than heard:
/// - inhale: a soft tap, then a swell that rises over 0.9 s, like air filling the chest;
/// - exhale: a softer tap and a long ebb over 1.2 s;
/// - hold: one quiet, round tap, then stillness.
/// Intensities stay at or below 0.45 and sharpness low, so it never buzzes. Devices without Core
/// Haptics fall back to a soft impact.
@MainActor
final class BreathHaptics {
    private var engine: CHHapticEngine?
    private let supported = CHHapticEngine.capabilitiesForHardware().supportsHaptics
    private lazy var fallback = UIImpactFeedbackGenerator(style: .soft)

    func prepare() {
        guard supported else { fallback.prepare(); return }
        guard engine == nil, let engine = try? CHHapticEngine() else { return }
        engine.playsHapticsOnly = true
        engine.isAutoShutdownEnabled = true
        engine.resetHandler = { [weak engine] in try? engine?.start() }
        try? engine.start()
        self.engine = engine
    }

    func play(_ kind: BreathPhaseKind) {
        guard supported else {
            fallback.impactOccurred(intensity: kind == .inhale ? 0.6 : 0.4)
            return
        }
        prepare()
        guard let engine, let pattern = try? Self.pattern(for: kind) else { return }
        try? engine.start()
        try? engine.makePlayer(with: pattern).start(atTime: CHHapticTimeImmediate)
    }

    func stop() {
        engine?.stop()
        engine = nil
    }

    nonisolated static func pattern(for kind: BreathPhaseKind) throws -> CHHapticPattern {
        let spec = spec(for: kind)
        var events = [
            CHHapticEvent(eventType: .hapticTransient, parameters: [
                .init(parameterID: .hapticIntensity, value: spec.tap),
                .init(parameterID: .hapticSharpness, value: spec.sharpness),
            ], relativeTime: 0),
        ]
        var curves: [CHHapticParameterCurve] = []
        if spec.swell > 0 {
            events.append(CHHapticEvent(eventType: .hapticContinuous, parameters: [
                .init(parameterID: .hapticIntensity, value: 1),
                .init(parameterID: .hapticSharpness, value: spec.sharpness * 0.6),
            ], relativeTime: 0.05, duration: spec.swell))
            curves.append(CHHapticParameterCurve(
                parameterID: .hapticIntensityControl,
                controlPoints: spec.curve.map { .init(relativeTime: 0.05 + $0.0 * spec.swell, value: $0.1) },
                relativeTime: 0
            ))
        }
        return try CHHapticPattern(events: events, parameterCurves: curves)
    }

    nonisolated struct Spec {
        let tap: Float, sharpness: Float, swell: TimeInterval
        /// (fraction of swell, intensity) control points.
        let curve: [(Double, Float)]
    }

    nonisolated static func spec(for kind: BreathPhaseKind) -> Spec {
        switch kind {
        case .inhale: Spec(tap: 0.35, sharpness: 0.2, swell: 0.9, curve: [(0, 0.08), (0.7, 0.42), (1, 0.2)])
        case .exhale: Spec(tap: 0.28, sharpness: 0.1, swell: 1.2, curve: [(0, 0.38), (0.4, 0.22), (1, 0.0)])
        case .holdIn, .holdOut: Spec(tap: 0.3, sharpness: 0.12, swell: 0, curve: [])
        }
    }
}
