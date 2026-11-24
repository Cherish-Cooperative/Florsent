import DeviceActivity
import FamilyControls
import FlorsentCore
import Foundation

/// Turns FlorsentCore's MonitorPlans into DeviceActivity schedules and threshold events.
enum GuidanceScheduler {
    /// Replaces the repeating daily and bedtime monitoring with what the settings ask for.
    static func apply(_ settings: GuidanceSettings, selection: FamilyActivitySelection) throws {
        let center = DeviceActivityCenter()
        center.stopMonitoring(GuidanceActivity.allCases.map { DeviceActivityName($0.rawValue) })
        guard settings.enabled, !selection.isEmpty else { return }
        for plan in GuidancePlanner.plans(for: settings) {
            try start(plan, selection: selection, center: center)
        }
    }

    /// Starts one plan; a follow-up replaces the previous follow-up.
    static func start(_ plan: MonitorPlan, selection: FamilyActivitySelection,
                      center: DeviceActivityCenter = DeviceActivityCenter()) throws {
        guard !selection.isEmpty else { return }
        let name = DeviceActivityName(plan.activity.rawValue)
        center.stopMonitoring([name])
        let schedule = DeviceActivitySchedule(intervalStart: plan.start, intervalEnd: plan.end, repeats: plan.repeats)
        var events: [DeviceActivityEvent.Name: DeviceActivityEvent] = [:]
        for e in plan.events {
            events[DeviceActivityEvent.Name(e.event.rawValue)] = event(for: selection, minutes: e.thresholdMinutes,
                                                                        includesPast: plan.includesPastActivity)
        }
        try center.startMonitoring(name, during: schedule, events: events)
    }

    static func stopAll() {
        DeviceActivityCenter().stopMonitoring()
    }

    private static func event(for s: FamilyActivitySelection, minutes: Int, includesPast: Bool) -> DeviceActivityEvent {
        let threshold = DateComponents(minute: minutes)
        if #available(iOS 17.4, *) {
            return DeviceActivityEvent(applications: s.applicationTokens, categories: s.categoryTokens,
                                       webDomains: s.webDomainTokens, threshold: threshold,
                                       includesPastActivity: includesPast)
        }
        return DeviceActivityEvent(applications: s.applicationTokens, categories: s.categoryTokens,
                                   webDomains: s.webDomainTokens, threshold: threshold)
    }
}
