#if DEBUG
import FlorsentKit
import Foundation

/// Debug-only launch arguments for deterministic screenshots and manual QA, e.g.
///   xcrun simctl launch <udid> app.florsent -FlorsentScreen session -FlorsentFreezeMs 2600
///   -FlorsentScreen home|breathe|session|grounding|settings
///   -FlorsentPattern equal_444|box_4444|relax_478|resonance_55
///   -FlorsentFreezeMs <ms>        session clock stands still at this elapsed time
///   -FlorsentGroundingIndex <n>   -FlorsentNight YES|NO
enum DebugLaunch {
    private static var args: UserDefaults { .standard } // launch arguments land in the argument domain

    static var screen: String? { args.string(forKey: "FlorsentScreen") }
    static var freezeMs: Int64? { args.object(forKey: "FlorsentFreezeMs").map { _ in Int64(args.integer(forKey: "FlorsentFreezeMs")) } }
    static var groundingIndex: Int? { args.object(forKey: "FlorsentGroundingIndex").map { _ in args.integer(forKey: "FlorsentGroundingIndex") } }
    static var night: Bool? { args.object(forKey: "FlorsentNight").map { _ in args.bool(forKey: "FlorsentNight") } }

    @MainActor static func apply(to model: AppModel) {
        if let id = args.string(forKey: "FlorsentPattern") { model.settings.patternId = BreathingPatterns.byId(id).id }
        switch screen {
        case "breathe", "session": model.path = [.breathe]
        case "grounding": model.path = [.grounding]
        case "settings": model.path = [.settings]
        default: break
        }
    }
}
#endif
