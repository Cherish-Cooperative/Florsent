import DeviceActivity
import FlorsentCore
import Foundation

/// Screen Time monitor. Runs in its own process under a ~6 MB memory ceiling, so it holds nothing:
/// each callback runs one GuidanceState transition from the App Group and carries out its effect
/// (a rain notification, the soft shield, lifting it, or watching again after quiet time).
final class FlorsentMonitor: DeviceActivityMonitor {
    override func intervalDidStart(for activity: DeviceActivityName) {
        super.intervalDidStart(for: activity)
        guard let a = GuidanceActivity(rawValue: activity.rawValue) else { return }
        let store = SharedStore()
        GuidanceEffects.perform(store.updateGuidance { $0.intervalStarted(a, now: Date()) }, store: store)
    }

    override func intervalDidEnd(for activity: DeviceActivityName) {
        super.intervalDidEnd(for: activity)
        guard let a = GuidanceActivity(rawValue: activity.rawValue) else { return }
        let store = SharedStore()
        GuidanceEffects.perform(store.updateGuidance { $0.intervalEnded(a, now: Date()) }, store: store)
    }

    override func eventDidReachThreshold(_ event: DeviceActivityEvent.Name, activity: DeviceActivityName) {
        super.eventDidReachThreshold(event, activity: activity)
        guard let e = GuidanceEvent(rawValue: event.rawValue) else { return }
        let store = SharedStore()
        GuidanceEffects.perform(store.updateGuidance { $0.reach(e, now: Date()) }, store: store)
    }
}
