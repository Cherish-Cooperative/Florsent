import FlorsentKit
import Foundation
import Observation
import SwiftUI

/// App-wide state: navigation, preferences, and the (lazily created) sound and haptics.
@MainActor @Observable
final class AppModel {
    var path: [Route] = []
    /// On iPad (regular width) Settings is a centered form sheet with its own stack, as in
    /// Apple's iPad apps; on iPhone it is pushed onto [path]. Set by RootView.
    var settingsAsSheet = false
    var settingsSheetShown = false
    var settingsPath: [Route] = []
    let settings = AppSettings()
    let guidance: GuidanceController
    /// Breathing was opened in the bedtime window: the picker suggests 4-7-8.
    private(set) var bedtimeSuggestion = false
    /// Shown on "back to the present" after breathing while guidance is on.
    private(set) var quietMinutes: Int?

    /// Created on first use, so opening the app never loads audio (as on Android).
    @ObservationIgnored private(set) lazy var sound = BreathingSoundscape()
    @ObservationIgnored private(set) lazy var haptics = BreathHaptics()

    init(guidance: GuidanceController? = nil) {
        self.guidance = guidance ?? GuidanceController()
        #if DEBUG
        DebugLaunch.apply(to: self)
        #endif
    }

    func openBreathing(bedtime: Bool? = nil, now: Date = Date()) {
        bedtimeSuggestion = bedtime ?? guidance.isBedtime(at: now)
        path = [.breathe]
    }

    /// Breathing finished or ended early: quiet time starts, then "back to the present".
    func finishBreathing(now: Date = Date()) {
        quietMinutes = guidance.breathingFinished(now: now)
        path = [.grounding]
    }

    /// Opens Settings, optionally straight on one of its sub-pages.
    func openSettings(_ page: Route? = nil) {
        let sub = page.map { [$0] } ?? []
        if settingsAsSheet {
            settingsPath = sub
            settingsSheetShown = true
        } else {
            path = [.settings] + sub
        }
    }

    func backToLife() {
        quietMinutes = nil
        path = []
    }

    /// The app came to the front: pick up the extensions' state, and a breathing request from the
    /// shield's "Go breathe" or the Start Breathing intent.
    func becameActive() {
        guidance.refresh()
        if SharedStore().takeBreathingRequest() { openBreathing() }
    }

    /// A guidance notification was tapped.
    func openFromNotification(_ tap: NotificationTap) {
        guard tap.opensBreathing else { return }
        _ = SharedStore().takeBreathingRequest() // the same request, consumed once
        openBreathing(bedtime: tap.context == .bedtime ? true : nil)
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

/// What a tapped guidance notification asks for (read from its userInfo).
struct NotificationTap: Sendable, Equatable {
    let opensBreathing: Bool
    let context: GuidanceContext?

    init(opensBreathing: Bool, context: GuidanceContext?) {
        self.opensBreathing = opensBreathing
        self.context = context
    }

    init(userInfo: [AnyHashable: Any]) {
        opensBreathing = userInfo[GuidanceNotification.routeKey] as? String == GuidanceNotification.breatheRoute
        context = (userInfo[GuidanceNotification.contextKey] as? String).flatMap(GuidanceContext.init(rawValue:))
    }
}
