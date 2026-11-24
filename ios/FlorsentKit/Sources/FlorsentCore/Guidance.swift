import Foundation

// Gentle guidance on iOS, Foundation only so the ~6 MB DeviceActivityMonitor can run it and the
// tests can run on macOS. The Screen Time types (DeviceActivitySchedule, ManagedSettingsStore) are
// built from these plans at the edges: ios/ScreenTimeShared.

/// Which reminder is speaking: the daily budget or the bedtime window. Bedtime wins when both apply.
public enum GuidanceContext: String, Codable, Sendable, CaseIterable {
    case daily
    case bedtime
}

/// Two stages, as on Android: first a notification with the distant rain, then the soft shield.
public enum GuidanceStage: String, Codable, Sendable {
    case notice
    case shield
}

/// A threshold event's name carries its meaning, so the monitor needs no lookup table:
/// "daily.notice", "bedtime.shield", ...
public struct GuidanceEvent: Hashable, Sendable {
    public let context: GuidanceContext
    public let stage: GuidanceStage

    public init(_ context: GuidanceContext, _ stage: GuidanceStage) {
        self.context = context
        self.stage = stage
    }

    public init?(rawValue: String) {
        let parts = rawValue.split(separator: ".")
        guard parts.count == 2, let c = GuidanceContext(rawValue: String(parts[0])),
              let s = GuidanceStage(rawValue: String(parts[1])) else { return nil }
        self.init(c, s)
    }

    public var rawValue: String { "\(context.rawValue).\(stage.rawValue)" }
}

/// The monitored activities. `followUp` is the one-off interval after "5 more minutes" or after quiet
/// time ends while still over budget; starting a new one replaces the old.
public enum GuidanceActivity: String, Sendable, CaseIterable {
    case daily = "florsent.daily"
    case bedtime = "florsent.bedtime"
    case followUp = "florsent.followup"
}

// MARK: - Settings

public struct BedtimeSettings: Codable, Equatable, Sendable {
    public var enabled: Bool
    public var hour: Int
    public var minute: Int
    /// Minutes of scrolling inside the window before the first reminder (Android: 10, 15, 20).
    public var thresholdMinutes: Int

    public init(enabled: Bool = false, hour: Int = 23, minute: Int = 0, thresholdMinutes: Int = 15) {
        self.enabled = enabled
        self.hour = hour
        self.minute = minute
        self.thresholdMinutes = thresholdMinutes
    }

    public static let thresholdChoices = [10, 15, 20]
    /// The bedtime window runs from the chosen time until 05:00.
    public static let wakeHour = 5
    /// Bedtimes offered by the picker: 20:00 to 02:00.
    public static func isReasonable(hour: Int) -> Bool { hour >= 20 || hour <= 2 }

    /// Whether [date] falls inside tonight's window (chosen time → 05:00, across midnight).
    public func isWithinWindow(_ date: Date, calendar: Calendar = .current) -> Bool {
        guard enabled else { return false }
        let c = calendar.dateComponents([.hour, .minute], from: date)
        let now = (c.hour ?? 0) * 60 + (c.minute ?? 0)
        let start = hour * 60 + minute
        let end = Self.wakeHour * 60
        return start <= end ? (now >= start && now < end) : (now >= start || now < end)
    }
}

public struct GuidanceSettings: Codable, Equatable, Sendable {
    /// On once the person picked apps to watch and turned guidance on.
    public var enabled: Bool
    public var dailyBudgetMinutes: Int
    /// No reminders for this long after breathing (Android "Quiet time", default 20).
    public var quietMinutes: Int
    public var bedtime: BedtimeSettings

    public init(enabled: Bool = false, dailyBudgetMinutes: Int = 60, quietMinutes: Int = 20,
                bedtime: BedtimeSettings = BedtimeSettings()) {
        self.enabled = enabled
        self.dailyBudgetMinutes = dailyBudgetMinutes
        self.quietMinutes = quietMinutes
        self.bedtime = bedtime
    }

    public static let dailyChoices = [15, 30, 45, 60, 90, 120]
    public static let quietChoices = [10, 20, 30, 60]
    /// The shield comes this long after the notification if scrolling goes on.
    public static let shieldGraceMinutes = 5
    /// "5 more minutes" on the shield.
    public static let snoozeMinutes = 5
}

// MARK: - Plans

/// One DeviceActivitySchedule with its events, as plain values. Times are wall-clock components;
/// an end earlier than the start means the next day (the bedtime window crosses midnight).
public struct MonitorPlan: Equatable, Sendable {
    public struct Event: Equatable, Sendable {
        public let event: GuidanceEvent
        public let thresholdMinutes: Int
    }

    public let activity: GuidanceActivity
    public let start: DateComponents
    public let end: DateComponents
    public let repeats: Bool
    public let events: [Event]
    /// A daily budget counts what was already used today; follow-ups count only from now.
    public let includesPastActivity: Bool
}

public enum GuidancePlanner {
    /// DeviceActivity rejects intervals shorter than this.
    public static let minimumIntervalMinutes = 15
    /// How long a follow-up keeps watching.
    public static let followUpHours = 3

    /// The repeating plans for the current settings: nothing until guidance is on.
    public static func plans(for settings: GuidanceSettings) -> [MonitorPlan] {
        guard settings.enabled else { return [] }
        let grace = GuidanceSettings.shieldGraceMinutes
        var plans = [
            MonitorPlan(
                activity: .daily,
                start: DateComponents(hour: 0, minute: 0, second: 0),
                end: DateComponents(hour: 23, minute: 59, second: 59),
                repeats: true,
                events: [
                    .init(event: GuidanceEvent(.daily, .notice), thresholdMinutes: settings.dailyBudgetMinutes),
                    .init(event: GuidanceEvent(.daily, .shield), thresholdMinutes: settings.dailyBudgetMinutes + grace),
                ],
                includesPastActivity: true
            ),
        ]
        let bed = settings.bedtime
        if bed.enabled {
            plans.append(MonitorPlan(
                activity: .bedtime,
                start: DateComponents(hour: bed.hour, minute: bed.minute, second: 0),
                end: DateComponents(hour: BedtimeSettings.wakeHour, minute: 0, second: 0),
                repeats: true,
                events: [
                    .init(event: GuidanceEvent(.bedtime, .notice), thresholdMinutes: bed.thresholdMinutes),
                    .init(event: GuidanceEvent(.bedtime, .shield), thresholdMinutes: bed.thresholdMinutes + grace),
                ],
                includesPastActivity: false
            ))
        }
        return plans
    }

    /// "5 more minutes": the shield comes back after five more minutes of scrolling, no notice first.
    public static func snooze(context: GuidanceContext, now: Date, calendar: Calendar = .current) -> MonitorPlan {
        followUp(context: context, from: now, events: [
            .init(event: GuidanceEvent(context, .shield), thresholdMinutes: GuidanceSettings.snoozeMinutes),
        ], calendar: calendar)
    }

    /// After quiet time, still over budget: start again gently, notice then shield.
    public static func afterQuiet(context: GuidanceContext, quietEnds: Date, calendar: Calendar = .current) -> MonitorPlan {
        let grace = GuidanceSettings.shieldGraceMinutes
        return followUp(context: context, from: quietEnds, events: [
            .init(event: GuidanceEvent(context, .notice), thresholdMinutes: grace),
            .init(event: GuidanceEvent(context, .shield), thresholdMinutes: grace * 2),
        ], calendar: calendar)
    }

    static func followUp(context: GuidanceContext, from start: Date, events: [MonitorPlan.Event],
                         calendar: Calendar) -> MonitorPlan {
        let end = start.addingTimeInterval(TimeInterval(max(followUpHours * 60, minimumIntervalMinutes) * 60))
        let parts: Set<Calendar.Component> = [.hour, .minute, .second]
        return MonitorPlan(
            activity: .followUp,
            start: calendar.dateComponents(parts, from: start),
            end: calendar.dateComponents(parts, from: end),
            repeats: false,
            events: events,
            includesPastActivity: false
        )
    }
}

// MARK: - State machine

/// What the monitor (or shield button) should do after a transition.
public enum GuidanceEffect: Equatable, Sendable {
    case none
    case notify(GuidanceContext)
    case raiseShield(GuidanceContext)
    case lowerShield
    /// Reached during quiet time: say nothing now, watch again gently once quiet time ends.
    case watch(MonitorPlan)
}

/// Shared guidance state in the App Group. Every process changes it only through these transitions.
public struct GuidanceState: Codable, Equatable, Sendable {
    /// Which budgets are over right now (cleared when their interval starts over).
    public var over: Set<GuidanceContext>
    public var shieldUp: Bool
    public var quietUntil: Date?
    public var updatedAt: Date?

    public init(over: Set<GuidanceContext> = [], shieldUp: Bool = false, quietUntil: Date? = nil, updatedAt: Date? = nil) {
        self.over = over
        self.shieldUp = shieldUp
        self.quietUntil = quietUntil
        self.updatedAt = updatedAt
    }

    /// The voice of the notice and the shield: bedtime first.
    public var context: GuidanceContext { over.contains(.bedtime) ? .bedtime : .daily }

    public func isQuiet(at now: Date) -> Bool { quietUntil.map { now < $0 } ?? false }

    /// What the "Over budget right now?" intent answers: over, and not resting after breathing.
    public func isOverBudget(at now: Date) -> Bool { !over.isEmpty && !isQuiet(at: now) }

    public func quietMinutesLeft(at now: Date) -> Int {
        guard let until = quietUntil, now < until else { return 0 }
        return Int((until.timeIntervalSince(now) / 60).rounded(.up))
    }

    /// A threshold was reached. Quiet time holds it back until it ends; a notice never covers a
    /// shield that is already up.
    public mutating func reach(_ event: GuidanceEvent, now: Date, calendar: Calendar = .current) -> GuidanceEffect {
        updatedAt = now
        over.insert(event.context)
        if let until = quietUntil, now < until {
            return .watch(GuidancePlanner.afterQuiet(context: context, quietEnds: until, calendar: calendar))
        }
        switch event.stage {
        case .notice:
            return shieldUp ? .none : .notify(context)
        case .shield:
            shieldUp = true
            return .raiseShield(context)
        }
    }

    /// A monitored interval began: a new day forgets yesterday's budget.
    public mutating func intervalStarted(_ activity: GuidanceActivity, now: Date) -> GuidanceEffect {
        updatedAt = now
        guard activity == .daily else { return .none }
        return forget(.daily)
    }

    /// A monitored interval ended: the bedtime window closing at 05:00 forgets the night.
    public mutating func intervalEnded(_ activity: GuidanceActivity, now: Date) -> GuidanceEffect {
        updatedAt = now
        switch activity {
        case .daily: return forget(.daily)
        case .bedtime: return forget(.bedtime)
        case .followUp: return .none
        }
    }

    private mutating func forget(_ context: GuidanceContext) -> GuidanceEffect {
        over.remove(context)
        guard over.isEmpty, shieldUp else { return .none }
        shieldUp = false
        return .lowerShield
    }

    /// "5 more minutes": lift the shield now, it returns after five more minutes of scrolling.
    public mutating func snooze(now: Date, calendar: Calendar = .current) -> MonitorPlan {
        updatedAt = now
        shieldUp = false
        return GuidancePlanner.snooze(context: context, now: now, calendar: calendar)
    }

    /// Breathing finished: quiet time starts and the shield lifts. Returns the follow-up to watch
    /// after quiet time when a budget is still over, so guidance resumes gently.
    public mutating func breathed(now: Date, quietMinutes: Int, calendar: Calendar = .current) -> MonitorPlan? {
        updatedAt = now
        shieldUp = false
        let until = now.addingTimeInterval(TimeInterval(max(quietMinutes, 0) * 60))
        quietUntil = until
        guard !over.isEmpty else { return nil }
        return GuidancePlanner.afterQuiet(context: context, quietEnds: until, calendar: calendar)
    }

    /// Guidance turned off: nothing over, nothing shown.
    public mutating func reset(now: Date) {
        self = GuidanceState(updatedAt: now)
    }
}

// MARK: - Calm log

/// When breathing practice was completed, for the calm insights (last 30 days, nothing else).
public struct CalmLog: Codable, Equatable, Sendable {
    public var breaths: [Date]

    public init(breaths: [Date] = []) { self.breaths = breaths }

    public static let keepDays = 30

    public mutating func record(_ date: Date) {
        breaths.append(date)
        let cutoff = date.addingTimeInterval(-TimeInterval(Self.keepDays) * 86_400)
        breaths.removeAll { $0 < cutoff }
    }

    /// Sessions in the last [days] calendar days including today.
    public func count(lastDays days: Int, now: Date, calendar: Calendar = .current) -> Int {
        guard let start = calendar.date(byAdding: .day, value: -(days - 1), to: calendar.startOfDay(for: now)) else { return 0 }
        return breaths.filter { $0 >= start && $0 <= now }.count
    }
}

// MARK: - Insights

/// This week against the week before, worded like Android's insights ("12 min less scrolling").
public enum WeekComparison: Equatable, Sendable {
    case notEnough
    case less(minutes: Int)
    case more
    case similar

    /// Differences under 10% (or 10 minutes) read as "about the same".
    public static func compare(thisWeekMinutes: Int, lastWeekMinutes: Int) -> WeekComparison {
        guard lastWeekMinutes > 0 else { return .notEnough }
        let diff = thisWeekMinutes - lastWeekMinutes
        let margin = max(10, lastWeekMinutes / 10)
        if diff <= -margin { return .less(minutes: -diff) }
        if diff >= margin { return .more }
        return .similar
    }
}
