import DeviceActivity
import FlorsentCore
import Foundation

/// Screen Time monitor. Runs in a separate process with a ~6 MB memory ceiling, so it links only
/// FlorsentCore (Foundation) and keeps no state beyond a few App Group keys.
///
/// Phase 2 will start a daily `DeviceActivitySchedule` with one threshold event per budget from the
/// app; this skeleton already keeps the "over budget" flag that the App Intent reads.
final class FlorsentMonitor: DeviceActivityMonitor {
    override func intervalDidStart(for activity: DeviceActivityName) {
        super.intervalDidStart(for: activity)
        SharedStore().setOverBudget(false) // a new day starts within budget
    }

    override func intervalDidEnd(for activity: DeviceActivityName) {
        super.intervalDidEnd(for: activity)
        SharedStore().setOverBudget(false)
    }

    override func eventDidReachThreshold(_ event: DeviceActivityEvent.Name, activity: DeviceActivityName) {
        super.eventDidReachThreshold(event, activity: activity)
        SharedStore().setOverBudget(true)
        // Phase 2: apply the soft shield through ManagedSettingsStore here.
    }
}
