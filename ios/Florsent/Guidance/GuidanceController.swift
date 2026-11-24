import FamilyControls
import FlorsentCore
import Foundation
import Observation
import UserNotifications

/// The Screen Time side effects the app needs, behind a protocol so tests can run without
/// Family Controls authorization.
@MainActor
protocol ScreenTimeControlling {
    var authorizationStatus: AuthorizationStatus { get }
    func requestAuthorization() async throws
    func requestNotifications() async
    func apply(_ settings: GuidanceSettings, selection: FamilyActivitySelection) throws
    func start(_ plan: MonitorPlan, selection: FamilyActivitySelection) throws
    func lowerShield()
    func stopAll()
}

@MainActor
struct LiveScreenTime: ScreenTimeControlling {
    var authorizationStatus: AuthorizationStatus { AuthorizationCenter.shared.authorizationStatus }

    func requestAuthorization() async throws {
        try await AuthorizationCenter.shared.requestAuthorization(for: .individual)
    }

    func requestNotifications() async {
        _ = try? await UNUserNotificationCenter.current().requestAuthorization(options: [.alert, .sound])
    }

    func apply(_ settings: GuidanceSettings, selection: FamilyActivitySelection) throws {
        try GuidanceScheduler.apply(settings, selection: selection)
    }

    func start(_ plan: MonitorPlan, selection: FamilyActivitySelection) throws {
        try GuidanceScheduler.start(plan, selection: selection)
    }

    func lowerShield() { ShieldController.lower() }
    func stopAll() { GuidanceScheduler.stopAll() }
}

/// Gentle guidance from the app's side: Screen Time authorization, the watched apps, budgets and
/// bedtime, and quiet time after breathing. The extensions keep the live state in the App Group.
@MainActor @Observable
final class GuidanceController {
    private(set) var authorization: AuthorizationStatus
    private(set) var selection: FamilyActivitySelection
    private(set) var settings: GuidanceSettings
    private(set) var state: GuidanceState
    private(set) var calmLog: CalmLog
    private(set) var failed = false

    @ObservationIgnored let store: SharedStore
    @ObservationIgnored private let screenTime: ScreenTimeControlling

    init(store: SharedStore = SharedStore(), screenTime: ScreenTimeControlling = LiveScreenTime()) {
        self.store = store
        self.screenTime = screenTime
        authorization = screenTime.authorizationStatus
        selection = WatchedApps.load(from: store)
        settings = store.guidanceSettings
        state = store.guidanceState
        calmLog = store.calmLog
    }

    /// Tests cannot mint Screen Time tokens; this stands in for a non-empty selection there.
    @ObservationIgnored var assumesWatchedApps = false

    var isAuthorized: Bool { authorization == .approved }
    var hasWatchedApps: Bool { assumesWatchedApps || !selection.isEmpty }
    var isActive: Bool { isAuthorized && settings.enabled && hasWatchedApps }

    /// Picks up what the extensions changed while the app was away.
    func refresh() {
        authorization = screenTime.authorizationStatus
        state = store.guidanceState
        calmLog = store.calmLog
    }

    func requestAuthorization() async {
        do {
            try await screenTime.requestAuthorization()
            failed = false
        } catch {
            failed = true
        }
        authorization = screenTime.authorizationStatus
    }

    func setSelection(_ new: FamilyActivitySelection) {
        selection = new
        WatchedApps.save(new, to: store)
        if settings.enabled, !hasWatchedApps { settings.enabled = false; store.guidanceSettings = settings }
        reschedule()
    }

    /// Turning guidance on also asks once for notifications (stage one is a notification).
    func setEnabled(_ on: Bool) async {
        if on { await screenTime.requestNotifications() }
        update { $0.enabled = on }
        if !on {
            store.updateGuidance { $0.reset(now: Date()) }
            state = store.guidanceState
            screenTime.lowerShield()
        }
    }

    func update(_ change: (inout GuidanceSettings) -> Void) {
        var next = settings
        change(&next)
        guard next != settings else { return }
        settings = next
        store.guidanceSettings = next
        reschedule()
    }

    private func reschedule() {
        do {
            if isActive { try screenTime.apply(settings, selection: selection) } else { screenTime.stopAll() }
        } catch {
            failed = true
        }
    }

    /// Breathing ended: log it, lift the shield and start quiet time. Returns the quiet minutes to
    /// show on "back to the present", or nil when guidance is off.
    @discardableResult
    func breathingFinished(now: Date = Date()) -> Int? {
        var log = store.calmLog
        log.record(now)
        store.calmLog = log
        calmLog = log
        guard isActive else { return nil }
        let quiet = settings.quietMinutes
        let followUp = store.updateGuidance { $0.breathed(now: now, quietMinutes: quiet) }
        state = store.guidanceState
        screenTime.lowerShield()
        if let followUp { try? screenTime.start(followUp, selection: selection) }
        return quiet
    }

    func isBedtime(at now: Date = Date()) -> Bool { isActive && settings.bedtime.isWithinWindow(now) }
}
