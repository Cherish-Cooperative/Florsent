import FlorsentCore
import Foundation
import ManagedSettings

/// The shield's two buttons, with public API only and no passcode or lecture:
/// - "Go breathe": a shield cannot open its app, so it closes the watched app, remembers the request
///   and posts a notification (distant rain) that opens Florsent straight into breathing.
/// - "5 more minutes": lifts the shield at once and watches five more minutes of scrolling.
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
        let store = SharedStore()
        switch action {
        case .primaryButtonPressed:
            store.requestBreathing()
            GuidanceNotification.post(.invite, context: store.guidanceState.context)
            return .close
        case .secondaryButtonPressed:
            let plan = store.updateGuidance { $0.snooze(now: Date()) }
            ShieldController.lower()
            try? GuidanceScheduler.start(plan, selection: WatchedApps.load(from: store))
            return .defer // shield is gone, so the app simply continues
        @unknown default:
            return .none
        }
    }
}
