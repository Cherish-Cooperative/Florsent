import AVFoundation
import FamilyControls
import XCTest
import FlorsentKit
@testable import Florsent

@MainActor
private final class FakeScreenTime: ScreenTimeControlling {
    var authorizationStatus: AuthorizationStatus = .notDetermined
    var applied: [GuidanceSettings] = []
    var started: [MonitorPlan] = []
    var lowered = 0
    var stopped = 0
    var notificationAsks = 0

    func requestAuthorization() async throws { authorizationStatus = .approved }
    func requestNotifications() async { notificationAsks += 1 }
    func apply(_ settings: GuidanceSettings, selection: FamilyActivitySelection) throws { applied.append(settings) }
    func start(_ plan: MonitorPlan, selection: FamilyActivitySelection) throws { started.append(plan) }
    func lowerShield() { lowered += 1 }
    func stopAll() { stopped += 1 }
}

@MainActor
final class GuidanceAppTests: XCTestCase {
    private var suite: String!
    private var store: SharedStore!
    private var fake: FakeScreenTime!

    override func setUp() async throws {
        suite = "florsent.apptests.\(UUID().uuidString)"
        store = SharedStore(defaults: try XCTUnwrap(UserDefaults(suiteName: suite)))
        fake = FakeScreenTime()
    }

    override func tearDown() async throws {
        UserDefaults().removePersistentDomain(forName: suite)
    }

    private func activeController() async -> GuidanceController {
        let g = GuidanceController(store: store, screenTime: fake)
        g.assumesWatchedApps = true
        await g.requestAuthorization()
        await g.setEnabled(true)
        return g
    }

    func testAuthorizationThenEnablingSchedulesAndAsksForNotifications() async {
        let g = GuidanceController(store: store, screenTime: fake)
        XCTAssertFalse(g.isAuthorized)
        XCTAssertFalse(g.isActive)
        g.assumesWatchedApps = true
        await g.requestAuthorization()
        XCTAssertTrue(g.isAuthorized)
        await g.setEnabled(true)
        XCTAssertTrue(g.isActive)
        XCTAssertEqual(fake.notificationAsks, 1)
        XCTAssertEqual(fake.applied.last?.enabled, true)
        XCTAssertTrue(store.guidanceSettings.enabled, "saved to the App Group for the extensions")
    }

    func testChangingBudgetOrBedtimeReschedules() async {
        let g = await activeController()
        g.update { $0.dailyBudgetMinutes = 30 }
        g.update { $0.bedtime.enabled = true; $0.bedtime.hour = 22; $0.bedtime.minute = 30 }
        XCTAssertEqual(fake.applied.last?.dailyBudgetMinutes, 30)
        XCTAssertEqual(fake.applied.last?.bedtime, BedtimeSettings(enabled: true, hour: 22, minute: 30))
        let count = fake.applied.count
        g.update { $0.dailyBudgetMinutes = 30 }
        XCTAssertEqual(fake.applied.count, count, "no change, no reschedule")
    }

    func testTurningOffStopsMonitoringAndLowersTheShield() async {
        let g = await activeController()
        store.updateGuidance { _ = $0.reach(GuidanceEvent(.daily, .shield), now: Date()) }
        await g.setEnabled(false)
        XCTAssertGreaterThan(fake.stopped, 0)
        XCTAssertGreaterThan(fake.lowered, 0)
        XCTAssertEqual(store.guidanceState.over, [])
        XCTAssertFalse(store.guidanceState.shieldUp)
    }

    func testBreathingWhileOverBudgetStartsQuietTimeAndAFollowUp() async throws {
        let g = await activeController()
        let now = Date()
        store.updateGuidance { _ = $0.reach(GuidanceEvent(.daily, .shield), now: now) }
        g.refresh()
        XCTAssertTrue(g.state.shieldUp)

        let quiet = g.breathingFinished(now: now)
        XCTAssertEqual(quiet, 20)
        XCTAssertEqual(fake.lowered, 1)
        XCTAssertFalse(store.guidanceState.shieldUp)
        XCTAssertEqual(store.guidanceState.quietUntil, now.addingTimeInterval(20 * 60))
        XCTAssertEqual(fake.started.first?.activity, .followUp)
        XCTAssertEqual(store.calmLog.breaths, [now])
        XCTAssertFalse(OverBudgetIntent.answer(store: store, now: now.addingTimeInterval(60)), "quiet time")
        XCTAssertTrue(OverBudgetIntent.answer(store: store, now: now.addingTimeInterval(21 * 60)))
    }

    func testBreathingWithGuidanceOffOnlyLogs() {
        let g = GuidanceController(store: store, screenTime: fake)
        XCTAssertNil(g.breathingFinished(now: Date()))
        XCTAssertEqual(store.calmLog.breaths.count, 1)
        XCTAssertNil(store.guidanceState.quietUntil)
        XCTAssertEqual(fake.lowered, 0)
    }

    func testBedtimeSuggestionFollowsTheWindow() async throws {
        let g = await activeController()
        g.update { $0.bedtime = BedtimeSettings(enabled: true, hour: 23, minute: 0) }
        let model = AppModel(guidance: g)
        let calendar = Calendar.current
        let night = try XCTUnwrap(calendar.date(bySettingHour: 23, minute: 30, second: 0, of: Date()))
        let noon = try XCTUnwrap(calendar.date(bySettingHour: 12, minute: 0, second: 0, of: Date()))
        model.openBreathing(now: night)
        XCTAssertTrue(model.bedtimeSuggestion)
        XCTAssertEqual(model.path, [.breathe])
        model.openBreathing(now: noon)
        XCTAssertFalse(model.bedtimeSuggestion)
    }

    func testFinishingBreathingShowsQuietTimeOnGrounding() async {
        let g = await activeController()
        let model = AppModel(guidance: g)
        model.finishBreathing()
        XCTAssertEqual(model.path, [.grounding])
        XCTAssertEqual(model.quietMinutes, 20)
        model.backToLife()
        XCTAssertNil(model.quietMinutes)
        XCTAssertEqual(model.path, [])
    }

    func testIntentAnswersFromTheAppGroup() {
        XCTAssertFalse(OverBudgetIntent.answer(store: store))
        store.updateGuidance { _ = $0.reach(GuidanceEvent(.bedtime, .notice), now: Date()) }
        XCTAssertTrue(OverBudgetIntent.answer(store: store))
        XCTAssertEqual(OverBudgetIntent.dialog(true).key, "intent.overBudget.yes")
        XCTAssertEqual(OverBudgetIntent.dialog(false).key, "intent.overBudget.no")
    }
}

@MainActor
final class GuidanceNotificationTests: XCTestCase {
    func testNoticeCarriesRainAndOpensBreathing() {
        let c = GuidanceNotification.content(.notice, context: .bedtime)
        XCTAssertNotNil(c.sound)
        XCTAssertEqual(c.threadIdentifier, GuidanceNotification.threadId)
        let tap = NotificationTap(userInfo: c.userInfo)
        XCTAssertEqual(tap, NotificationTap(opensBreathing: true, context: .bedtime))
        XCTAssertFalse(NotificationTap(userInfo: [:]).opensBreathing)
    }

    func testTappingABedtimeNoticeSuggestsFourSevenEight() {
        let model = AppModel(guidance: GuidanceController(store: SharedStore(defaults: UserDefaults(suiteName: "florsent.tap.\(UUID())")!),
                                                          screenTime: FakeScreenTime()))
        model.openFromNotification(NotificationTap(opensBreathing: true, context: .bedtime))
        XCTAssertEqual(model.path, [.breathe])
        XCTAssertTrue(model.bedtimeSuggestion)
    }

    func testRainSoundIsShortEnoughForANotification() throws {
        let url = try XCTUnwrap(Bundle.main.url(forResource: SoundTuning.File.rainNotification, withExtension: "caf"))
        let audio = try AVAudioFile(forReading: url)
        let file = Double(audio.length) / audio.fileFormat.sampleRate
        XCTAssertLessThan(file, 30, "iOS plays notification sounds only under 30 seconds")
        XCTAssertGreaterThan(file, 11.5)
    }

    func testScreenTimeCopyResolvesInBothLanguages() throws {
        for (folder, title) in [("en", "How about a short break?"), ("zh-Hant", "要不要休息一下？")] {
            let bundle = try XCTUnwrap(Bundle.main.path(forResource: folder, ofType: "lproj").flatMap(Bundle.init(path:)))
            XCTAssertEqual(bundle.localizedString(forKey: "notice.daily.title", value: "MISSING", table: "ScreenTime"), title)
            XCTAssertNotEqual(bundle.localizedString(forKey: "invite.body", value: "MISSING", table: "ScreenTime"), "MISSING")
        }
    }

    func testBedtimeChoicesStayInTheEvening() {
        XCTAssertEqual(BedtimeSettingsView.times.count, 13)
        XCTAssertEqual(BedtimeSettingsView.times.first, 20 * 60)
        XCTAssertEqual(BedtimeSettingsView.times.last, 2 * 60)
        XCTAssertTrue(BedtimeSettingsView.times.allSatisfy { BedtimeSettings.isReasonable(hour: $0 / 60) })
    }

    func testShortcutGuideAndDurationsAreLocalized() {
        for key in ["shortcut.step1", "shortcut.step2", "shortcut.step3", "bedtime.suggest.body", "home.guidance.setupAction"] {
            XCTAssertNotEqual(L10n.string(key), key, key)
        }
        XCTAssertFalse(L10n.duration(minutes: 90).isEmpty)
        XCTAssertNotEqual(L10n.duration(minutes: 60), L10n.duration(minutes: 90))
    }
}
