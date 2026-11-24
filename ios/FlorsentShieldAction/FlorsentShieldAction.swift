import FlorsentCore
import ManagedSettings
import UserNotifications

/// Handles the shield buttons. A shield cannot open its app directly, so "Start breathing" closes the
/// watched app, remembers the request (the app opens straight into breathing) and posts a
/// notification with the 12-second distant-rain sound to tap. "Not now" lets the moment pass.
final class FlorsentShieldAction: ShieldActionDelegate {
    override func handle(action: ShieldAction, for application: ApplicationToken,
                         completionHandler: @escaping (ShieldActionResponse) -> Void) {
        completionHandler(respond(to: action))
    }

    override func handle(action: ShieldAction, for webDomain: WebDomainToken,
                         completionHandler: @escaping (ShieldActionResponse) -> Void) {
        completionHandler(respond(to: action))
    }

    override func handle(action: ShieldAction, for category: ActivityCategoryToken,
                         completionHandler: @escaping (ShieldActionResponse) -> Void) {
        completionHandler(respond(to: action))
    }

    private func respond(to action: ShieldAction) -> ShieldActionResponse {
        switch action {
        case .primaryButtonPressed:
            SharedStore().requestBreathing()
            postBreathingInvite()
            return .close
        case .secondaryButtonPressed:
            // Phase 2: lift this app's shield for a short grace period before deferring.
            return .defer
        @unknown default:
            return .none
        }
    }

    private func postBreathingInvite() {
        let content = UNMutableNotificationContent()
        content.title = String(localized: "notification.title")
        content.body = String(localized: "notification.body")
        // Installed by the app into the App Group's Library/Sounds (see NotificationSound).
        content.sound = UNNotificationSound(named: UNNotificationSoundName(SoundTuning.File.rainNotification + ".caf"))
        content.interruptionLevel = .active
        let request = UNNotificationRequest(identifier: "florsent.breathe", content: content, trigger: nil)
        UNUserNotificationCenter.current().add(request)
    }
}
