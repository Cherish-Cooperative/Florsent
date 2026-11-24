import XCTest
@testable import FlorsentCore

private let taipei: Calendar = {
    var c = Calendar(identifier: .gregorian)
    c.timeZone = TimeZone(identifier: "Asia/Taipei")!
    return c
}()

/// 2026-11-24 at hh:mm in Taipei.
private func at(_ hour: Int, _ minute: Int = 0, day: Int = 24) -> Date {
    taipei.date(from: DateComponents(year: 2026, month: 11, day: day, hour: hour, minute: minute))!
}

final class GuidancePlannerTests: XCTestCase {
    func testNothingIsMonitoredUntilGuidanceIsOn() {
        XCTAssertEqual(GuidancePlanner.plans(for: GuidanceSettings()), [])
    }

    func testDailyPlanNoticesAtBudgetThenShieldsFiveMinutesLater() throws {
        let plans = GuidancePlanner.plans(for: GuidanceSettings(enabled: true, dailyBudgetMinutes: 45))
        XCTAssertEqual(plans.count, 1)
        let daily = try XCTUnwrap(plans.first)
        XCTAssertEqual(daily.activity, .daily)
        XCTAssertTrue(daily.repeats)
        XCTAssertTrue(daily.includesPastActivity, "a daily budget counts what was already used today")
        XCTAssertEqual(daily.start, DateComponents(hour: 0, minute: 0, second: 0))
        XCTAssertEqual(daily.end, DateComponents(hour: 23, minute: 59, second: 59))
        XCTAssertEqual(daily.events.map(\.event.rawValue), ["daily.notice", "daily.shield"])
        XCTAssertEqual(daily.events.map(\.thresholdMinutes), [45, 50])
    }

    func testBedtimePlanRunsFromBedtimeUntilFiveAcrossMidnight() throws {
        let settings = GuidanceSettings(enabled: true, bedtime: BedtimeSettings(enabled: true, hour: 23, minute: 30, thresholdMinutes: 10))
        let plans = GuidancePlanner.plans(for: settings)
        XCTAssertEqual(plans.map(\.activity), [.daily, .bedtime])
        let bed = plans[1]
        XCTAssertEqual(bed.start, DateComponents(hour: 23, minute: 30, second: 0))
        XCTAssertEqual(bed.end, DateComponents(hour: 5, minute: 0, second: 0))
        XCTAssertTrue(bed.repeats)
        XCTAssertFalse(bed.includesPastActivity, "daytime scrolling does not count toward bedtime")
        XCTAssertEqual(bed.events.map(\.event), [GuidanceEvent(.bedtime, .notice), GuidanceEvent(.bedtime, .shield)])
        XCTAssertEqual(bed.events.map(\.thresholdMinutes), [10, 15])
    }

    func testSnoozeRearmsOnlyTheShieldAfterFiveMinutes() {
        let plan = GuidancePlanner.snooze(context: .daily, now: at(14, 7), calendar: taipei)
        XCTAssertEqual(plan.activity, .followUp)
        XCTAssertFalse(plan.repeats)
        XCTAssertFalse(plan.includesPastActivity)
        XCTAssertEqual(plan.events, [.init(event: GuidanceEvent(.daily, .shield), thresholdMinutes: 5)])
        XCTAssertEqual(plan.start, DateComponents(hour: 14, minute: 7, second: 0))
        XCTAssertEqual(plan.end, DateComponents(hour: 17, minute: 7, second: 0))
    }

    func testFollowUpIntervalIsLongEnoughForDeviceActivity() {
        let plan = GuidancePlanner.afterQuiet(context: .bedtime, quietEnds: at(23, 50), calendar: taipei)
        let start = plan.start.hour! * 60 + plan.start.minute!
        var end = plan.end.hour! * 60 + plan.end.minute!
        if end < start { end += 24 * 60 } // crosses midnight
        XCTAssertGreaterThanOrEqual(end - start, GuidancePlanner.minimumIntervalMinutes)
        XCTAssertEqual(plan.events.map(\.event.rawValue), ["bedtime.notice", "bedtime.shield"])
        XCTAssertEqual(plan.events.map(\.thresholdMinutes), [5, 10])
    }

    func testEventNamesRoundTrip() {
        for c in GuidanceContext.allCases {
            for s in [GuidanceStage.notice, .shield] {
                XCTAssertEqual(GuidanceEvent(rawValue: GuidanceEvent(c, s).rawValue), GuidanceEvent(c, s))
            }
        }
        XCTAssertNil(GuidanceEvent(rawValue: "daily"))
        XCTAssertNil(GuidanceEvent(rawValue: "weekly.notice"))
        XCTAssertNil(GuidanceEvent(rawValue: "daily.notice.extra"))
        XCTAssertEqual(GuidanceActivity(rawValue: "florsent.followup"), .followUp)
    }
}

final class GuidanceStateTests: XCTestCase {
    func testTwoStagesThenSnoozeThenShieldAgain() {
        var s = GuidanceState()
        XCTAssertFalse(s.isOverBudget(at: at(10)))
        XCTAssertEqual(s.reach(GuidanceEvent(.daily, .notice), now: at(10)), .notify(.daily))
        XCTAssertTrue(s.isOverBudget(at: at(10)))
        XCTAssertFalse(s.shieldUp, "the first stage is only a notification")
        XCTAssertEqual(s.reach(GuidanceEvent(.daily, .shield), now: at(10, 5)), .raiseShield(.daily))
        XCTAssertTrue(s.shieldUp)

        let plan = s.snooze(now: at(10, 6), calendar: taipei)
        XCTAssertFalse(s.shieldUp, "5 more minutes lifts the shield at once")
        XCTAssertEqual(plan.events.first?.event, GuidanceEvent(.daily, .shield))
        XCTAssertTrue(s.isOverBudget(at: at(10, 6)), "still over budget while snoozed")
        XCTAssertEqual(s.reach(GuidanceEvent(.daily, .shield), now: at(10, 11)), .raiseShield(.daily))
    }

    func testNoticeNeverCoversARaisedShield() {
        var s = GuidanceState(over: [.daily], shieldUp: true)
        XCTAssertEqual(s.reach(GuidanceEvent(.bedtime, .notice), now: at(23, 15)), .none)
        XCTAssertEqual(s.context, .bedtime, "the shield switches to bedtime words")
    }

    func testBedtimeSpeaksBeforeDaily() {
        var s = GuidanceState()
        _ = s.reach(GuidanceEvent(.daily, .notice), now: at(21))
        XCTAssertEqual(s.context, .daily)
        XCTAssertEqual(s.reach(GuidanceEvent(.bedtime, .notice), now: at(23, 20)), .notify(.bedtime))
        XCTAssertEqual(s.reach(GuidanceEvent(.daily, .shield), now: at(23, 21)), .raiseShield(.bedtime))
    }

    func testBreathingStartsQuietTimeAndLiftsTheShield() throws {
        var s = GuidanceState(over: [.daily], shieldUp: true)
        let followUp = try XCTUnwrap(s.breathed(now: at(15), quietMinutes: 20, calendar: taipei))
        XCTAssertFalse(s.shieldUp)
        XCTAssertEqual(s.quietUntil, at(15, 20))
        XCTAssertTrue(s.isQuiet(at: at(15, 19)))
        XCTAssertFalse(s.isQuiet(at: at(15, 20)))
        XCTAssertFalse(s.isOverBudget(at: at(15, 10)), "resting after breathing is not over budget")
        XCTAssertTrue(s.isOverBudget(at: at(15, 21)))
        XCTAssertEqual(s.quietMinutesLeft(at: at(15, 0)), 20)
        XCTAssertEqual(s.quietMinutesLeft(at: at(15, 25)), 0)
        // Guidance resumes gently once quiet time ends.
        XCTAssertEqual(followUp.start, DateComponents(hour: 15, minute: 20, second: 0))
        XCTAssertEqual(followUp.events.map(\.event.stage), [.notice, .shield])
    }

    func testVoluntaryBreathingNeedsNoFollowUp() {
        var s = GuidanceState()
        XCTAssertNil(s.breathed(now: at(9), quietMinutes: 20, calendar: taipei))
        XCTAssertNotNil(s.quietUntil)
    }

    func testThresholdDuringQuietTimeWaitsForQuietToEnd() {
        var s = GuidanceState()
        _ = s.breathed(now: at(12), quietMinutes: 30, calendar: taipei)
        let effect = s.reach(GuidanceEvent(.daily, .shield), now: at(12, 10))
        guard case let .watch(plan) = effect else { return XCTFail("expected a follow-up, got \(effect)") }
        XCTAssertFalse(s.shieldUp, "no shield during quiet time")
        XCTAssertEqual(s.over, [.daily], "but the budget is remembered")
        XCTAssertEqual(plan.start, DateComponents(hour: 12, minute: 30, second: 0))
    }

    func testNewDayForgetsDailyButKeepsTonight() {
        var s = GuidanceState(over: [.daily, .bedtime], shieldUp: true)
        XCTAssertEqual(s.intervalStarted(.daily, now: at(0, 0, day: 25)), .none, "bedtime still holds the shield")
        XCTAssertEqual(s.over, [.bedtime])
        XCTAssertTrue(s.shieldUp)
        XCTAssertEqual(s.intervalEnded(.bedtime, now: at(5, 0, day: 25)), .lowerShield)
        XCTAssertEqual(s.over, [])
        XCTAssertFalse(s.shieldUp)
    }

    func testDailyResetLowersADailyShield() {
        var s = GuidanceState(over: [.daily], shieldUp: true)
        XCTAssertEqual(s.intervalStarted(.daily, now: at(0, 0, day: 25)), .lowerShield)
        XCTAssertEqual(s.intervalStarted(.bedtime, now: at(23)), .none)
        XCTAssertEqual(s.intervalEnded(.followUp, now: at(23)), .none)
    }

    func testResetForgetsEverything() {
        var s = GuidanceState(over: [.daily], shieldUp: true, quietUntil: at(16))
        s.reset(now: at(15))
        XCTAssertEqual(s.over, [])
        XCTAssertFalse(s.shieldUp)
        XCTAssertNil(s.quietUntil)
    }
}

final class BedtimeTests: XCTestCase {
    func testWindowCrossesMidnightUntilFive() {
        let bed = BedtimeSettings(enabled: true, hour: 23, minute: 0)
        XCTAssertFalse(bed.isWithinWindow(at(22, 59), calendar: taipei))
        XCTAssertTrue(bed.isWithinWindow(at(23, 0), calendar: taipei))
        XCTAssertTrue(bed.isWithinWindow(at(1, 30), calendar: taipei))
        XCTAssertTrue(bed.isWithinWindow(at(4, 59), calendar: taipei))
        XCTAssertFalse(bed.isWithinWindow(at(5, 0), calendar: taipei))
        XCTAssertFalse(bed.isWithinWindow(at(12), calendar: taipei))
    }

    func testWindowAfterMidnightBedtime() {
        let bed = BedtimeSettings(enabled: true, hour: 0, minute: 30)
        XCTAssertFalse(bed.isWithinWindow(at(23, 50), calendar: taipei))
        XCTAssertTrue(bed.isWithinWindow(at(0, 30), calendar: taipei))
        XCTAssertTrue(bed.isWithinWindow(at(3), calendar: taipei))
        XCTAssertFalse(bed.isWithinWindow(at(5, 1), calendar: taipei))
    }

    func testOffMeansNeverBedtime() {
        XCTAssertFalse(BedtimeSettings(enabled: false).isWithinWindow(at(23, 30), calendar: taipei))
    }

    func testChoicesMatchAndroid() {
        XCTAssertEqual(BedtimeSettings.thresholdChoices, [10, 15, 20])
        XCTAssertEqual(BedtimeSettings().thresholdMinutes, 15)
        XCTAssertEqual(GuidanceSettings().dailyBudgetMinutes, 60)
        XCTAssertEqual(GuidanceSettings().quietMinutes, 20)
        XCTAssertTrue(BedtimeSettings.isReasonable(hour: 22))
        XCTAssertTrue(BedtimeSettings.isReasonable(hour: 1))
        XCTAssertFalse(BedtimeSettings.isReasonable(hour: 15))
    }
}

final class CalmInsightTests: XCTestCase {
    func testCalmLogCountsRecentDaysAndForgetsOldOnes() {
        var log = CalmLog()
        log.record(taipei.date(byAdding: .day, value: -31, to: at(9, 0, day: 24))!)
        log.record(at(21, 0, day: 18))
        log.record(at(8, 0, day: 24))
        log.record(at(22, 0, day: 24))
        XCTAssertEqual(log.count(lastDays: 1, now: at(23, 0, day: 24), calendar: taipei), 2)
        XCTAssertEqual(log.count(lastDays: 7, now: at(23, 0, day: 24), calendar: taipei), 3)
        XCTAssertEqual(log.breaths.count, 3, "older than 30 days is dropped")
    }

    func testWeekComparison() {
        XCTAssertEqual(WeekComparison.compare(thisWeekMinutes: 300, lastWeekMinutes: 0), .notEnough)
        XCTAssertEqual(WeekComparison.compare(thisWeekMinutes: 300, lastWeekMinutes: 420), .less(minutes: 120))
        XCTAssertEqual(WeekComparison.compare(thisWeekMinutes: 480, lastWeekMinutes: 420), .more)
        XCTAssertEqual(WeekComparison.compare(thisWeekMinutes: 400, lastWeekMinutes: 420), .similar)
        XCTAssertEqual(WeekComparison.compare(thisWeekMinutes: 25, lastWeekMinutes: 30), .similar, "10 minute floor")
    }

    func testStoreRoundTrip() throws {
        let suite = "florsent.tests.\(UUID().uuidString)"
        let defaults = try XCTUnwrap(UserDefaults(suiteName: suite))
        defer { defaults.removePersistentDomain(forName: suite) }
        let store = SharedStore(defaults: defaults)
        XCTAssertEqual(store.guidanceSettings, GuidanceSettings())
        var settings = GuidanceSettings(enabled: true, dailyBudgetMinutes: 30)
        settings.bedtime = BedtimeSettings(enabled: true, hour: 22, minute: 45, thresholdMinutes: 20)
        store.guidanceSettings = settings
        XCTAssertEqual(SharedStore(defaults: defaults).guidanceSettings, settings)

        let quiet = store.updateGuidance { $0.breathed(now: at(10), quietMinutes: 20, calendar: taipei) }
        XCTAssertNil(quiet)
        XCTAssertEqual(store.guidanceState.quietUntil, at(10, 20))

        var log = store.calmLog
        log.record(at(10))
        store.calmLog = log
        XCTAssertEqual(store.calmLog.breaths, [at(10)])
    }
}
