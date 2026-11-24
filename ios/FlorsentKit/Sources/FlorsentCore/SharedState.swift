import Foundation

/// The App Group shared by the app, its Screen Time extensions and App Intents. The identifier comes
/// from each bundle's Info.plist (`FlorsentAppGroup`, set from ios/Config.xcconfig).
public enum AppGroup {
    public static var identifier: String {
        Bundle.main.object(forInfoDictionaryKey: "FlorsentAppGroup") as? String ?? "group.app.florsent"
    }

    /// Falls back to the app's own defaults when the group is not provisioned (simulator, unsigned).
    public static var defaults: UserDefaults { UserDefaults(suiteName: identifier) ?? .standard }

    public static var containerURL: URL? {
        FileManager.default.containerURL(forSecurityApplicationGroupIdentifier: identifier)
    }
}

/// Keys in the App Group defaults. Extensions write budget state; the app writes preferences.
public enum SharedKey {
    public static let breathingPatternId = "breathingPatternId"
    public static let ambientSound = "ambientSound"
    public static let cueSound = "cueSound"
    public static let haptics = "haptics"
    public static let overBudget = "overBudget"
    public static let overBudgetUpdatedAt = "overBudgetUpdatedAt"
    public static let breathingRequestedAt = "breathingRequestedAt"
}

/// Whether the watched apps are over budget right now. Written by FlorsentMonitor (Phase 2), read by
/// the "Is Florsent over budget?" App Intent for Shortcuts automations (e.g. turning on grayscale).
public struct BudgetState: Sendable, Equatable {
    public var isOverBudget: Bool
    public var updatedAt: Date?

    public init(isOverBudget: Bool, updatedAt: Date?) {
        self.isOverBudget = isOverBudget
        self.updatedAt = updatedAt
    }
}

public struct SharedStore: @unchecked Sendable { // UserDefaults is thread-safe
    public let defaults: UserDefaults

    public init(defaults: UserDefaults = AppGroup.defaults) { self.defaults = defaults }

    public var budget: BudgetState {
        BudgetState(
            isOverBudget: defaults.bool(forKey: SharedKey.overBudget),
            updatedAt: defaults.object(forKey: SharedKey.overBudgetUpdatedAt) as? Date
        )
    }

    public func setOverBudget(_ over: Bool, now: Date = Date()) {
        defaults.set(over, forKey: SharedKey.overBudget)
        defaults.set(now, forKey: SharedKey.overBudgetUpdatedAt)
    }

    /// The shield's "Start breathing" was tapped; the app opens straight into breathing next launch.
    public func requestBreathing(now: Date = Date()) {
        defaults.set(now, forKey: SharedKey.breathingRequestedAt)
    }

    /// Consumes a breathing request made in the last [window] seconds.
    public func takeBreathingRequest(now: Date = Date(), window: TimeInterval = 600) -> Bool {
        guard let at = defaults.object(forKey: SharedKey.breathingRequestedAt) as? Date else { return false }
        defaults.removeObject(forKey: SharedKey.breathingRequestedAt)
        return now.timeIntervalSince(at) <= window && now >= at
    }
}
