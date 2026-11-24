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

/// Keys in the App Group defaults. The app writes preferences and the selection; the extensions and
/// the app move the guidance state through `GuidanceState`'s transitions.
public enum SharedKey {
    public static let breathingPatternId = "breathingPatternId"
    public static let ambientSound = "ambientSound"
    public static let cueSound = "cueSound"
    public static let haptics = "haptics"
    public static let breathingRequestedAt = "breathingRequestedAt"
    public static let guidanceSettings = "guidanceSettings"
    public static let guidanceState = "guidanceState"
    /// The FamilyActivitySelection (opaque tokens), property-list encoded by the app.
    public static let activitySelection = "activitySelection"
    public static let calmLog = "calmLog"
}

public struct SharedStore: @unchecked Sendable { // UserDefaults is thread-safe
    public let defaults: UserDefaults

    public init(defaults: UserDefaults = AppGroup.defaults) { self.defaults = defaults }

    // MARK: Guidance

    public var guidanceSettings: GuidanceSettings {
        get { decode(SharedKey.guidanceSettings) ?? GuidanceSettings() }
        nonmutating set { encode(newValue, SharedKey.guidanceSettings) }
    }

    public var guidanceState: GuidanceState {
        get { decode(SharedKey.guidanceState) ?? GuidanceState() }
        nonmutating set { encode(newValue, SharedKey.guidanceState) }
    }

    /// Runs one transition on the stored state and saves it. Processes are short-lived and events
    /// arrive one at a time, so read-modify-write on the App Group is enough.
    @discardableResult
    public func updateGuidance<T>(_ transition: (inout GuidanceState) -> T) -> T {
        var state = guidanceState
        let result = transition(&state)
        guidanceState = state
        return result
    }

    public var calmLog: CalmLog {
        get { decode(SharedKey.calmLog) ?? CalmLog() }
        nonmutating set { encode(newValue, SharedKey.calmLog) }
    }

    public var activitySelectionData: Data? {
        get { defaults.data(forKey: SharedKey.activitySelection) }
        nonmutating set { defaults.set(newValue, forKey: SharedKey.activitySelection) }
    }

    /// "Over budget right now?" for the App Intent and Shortcuts automations.
    public func isOverBudget(now: Date = Date()) -> Bool { guidanceState.isOverBudget(at: now) }

    // MARK: Breathing requests

    /// The shield's "Go breathe" or a notification asked for breathing; the app opens straight into it.
    public func requestBreathing(now: Date = Date()) {
        defaults.set(now, forKey: SharedKey.breathingRequestedAt)
    }

    /// Consumes a breathing request made in the last [window] seconds.
    public func takeBreathingRequest(now: Date = Date(), window: TimeInterval = 600) -> Bool {
        guard let at = defaults.object(forKey: SharedKey.breathingRequestedAt) as? Date else { return false }
        defaults.removeObject(forKey: SharedKey.breathingRequestedAt)
        return now.timeIntervalSince(at) <= window && now >= at
    }

    // MARK: Coding

    private func decode<T: Decodable>(_ key: String) -> T? {
        defaults.data(forKey: key).flatMap { try? JSONDecoder().decode(T.self, from: $0) }
    }

    private func encode<T: Encodable>(_ value: T, _ key: String) {
        defaults.set(try? JSONEncoder().encode(value), forKey: key)
    }
}
