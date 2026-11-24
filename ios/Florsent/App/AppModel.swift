import FlorsentKit
import Observation
import SwiftUI

/// App-wide state: navigation, preferences, and the (lazily created) sound and haptics.
@MainActor @Observable
final class AppModel {
    var path: [Route] = []
    let settings = AppSettings()

    /// Created on first use, so opening the app never loads audio (as on Android).
    @ObservationIgnored private(set) lazy var sound = BreathingSoundscape()
    @ObservationIgnored private(set) lazy var haptics = BreathHaptics()

    init() {
        #if DEBUG
        DebugLaunch.apply(to: self)
        #endif
    }

    func openBreathing() { path = [.breathe] }

    /// Breathing finished or ended early: on to "back to the present".
    func finishBreathing() { path = [.grounding] }

    func backToLife() { path = [] }

    /// The shield's "Start breathing" or the Start Breathing intent asked for a session.
    func openPendingBreathingRequest() {
        if SharedStore().takeBreathingRequest() { openBreathing() }
    }
}

/// Preferences, stored in the App Group so the extensions and intents can read them.
@MainActor @Observable
final class AppSettings {
    @ObservationIgnored private let defaults: UserDefaults

    var patternId: String { didSet { defaults.set(patternId, forKey: SharedKey.breathingPatternId) } }
    var ambientSound: Bool { didSet { defaults.set(ambientSound, forKey: SharedKey.ambientSound) } }
    var cueSound: Bool { didSet { defaults.set(cueSound, forKey: SharedKey.cueSound) } }
    var haptics: Bool { didSet { defaults.set(haptics, forKey: SharedKey.haptics) } }

    init(defaults: UserDefaults = AppGroup.defaults) {
        self.defaults = defaults
        patternId = BreathingPatterns.byId(defaults.string(forKey: SharedKey.breathingPatternId)).id
        ambientSound = defaults.bool(forKey: SharedKey.ambientSound)   // off by default, as on Android
        cueSound = defaults.bool(forKey: SharedKey.cueSound)           // off by default, as on Android
        haptics = defaults.object(forKey: SharedKey.haptics) as? Bool ?? true
    }

    var pattern: BreathingPattern { BreathingPatterns.byId(patternId) }
}
