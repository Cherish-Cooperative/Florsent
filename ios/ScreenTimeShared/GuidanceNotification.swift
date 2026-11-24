import FlorsentCore
import Foundation
import UserNotifications

/// Local notifications with the 12-second distant rain (fades in and out). Tapping one opens
/// Florsent straight into breathing.
enum GuidanceNotification {
    enum Kind: String {
        /// Stage one: the budget is reached ("How about a short break?").
        case notice
        /// After "Go breathe" on the shield: one tap into breathing.
        case invite
    }

    static let routeKey = "florsent.route"
    static let contextKey = "florsent.context"
    static let breatheRoute = "breathe"
    static let threadId = "florsent.guidance"

    static func content(_ kind: Kind, context: GuidanceContext) -> UNMutableNotificationContent {
        let content = UNMutableNotificationContent()
        switch kind {
        case .notice:
            content.title = context == .bedtime
                ? String(localized: "notice.bedtime.title", table: "ScreenTime")
                : String(localized: "notice.daily.title", table: "ScreenTime")
            content.body = String(localized: "notice.body", table: "ScreenTime")
        case .invite:
            content.title = String(localized: "invite.title", table: "ScreenTime")
            content.body = String(localized: "invite.body", table: "ScreenTime")
        }
        // The app installs it into the App Group's Library/Sounds, where extension notifications look.
        content.sound = UNNotificationSound(named: UNNotificationSoundName(SoundTuning.File.rainNotification + ".caf"))
        content.interruptionLevel = .active
        content.threadIdentifier = threadId
        content.userInfo = [routeKey: breatheRoute, contextKey: context.rawValue]
        return content
    }

    static func post(_ kind: Kind, context: GuidanceContext) {
        let request = UNNotificationRequest(identifier: "florsent.\(kind.rawValue)",
                                            content: content(kind, context: context), trigger: nil)
        UNUserNotificationCenter.current().add(request)
    }
}

/// Carries out what a GuidanceState transition asked for (monitor, shield buttons and the app).
enum GuidanceEffects {
    static func perform(_ effect: GuidanceEffect, store: SharedStore = SharedStore()) {
        switch effect {
        case .none:
            break
        case let .notify(context):
            GuidanceNotification.post(.notice, context: context)
        case .raiseShield:
            ShieldController.raise(over: WatchedApps.load(from: store))
        case .lowerShield:
            ShieldController.lower()
        case let .watch(plan):
            try? GuidanceScheduler.start(plan, selection: WatchedApps.load(from: store))
        }
    }
}
