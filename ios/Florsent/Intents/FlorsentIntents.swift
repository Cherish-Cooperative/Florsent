import AppIntents
import Foundation
import FlorsentCore

/// "Florsent: over budget right now?" Returns a Bool for Shortcuts automations, e.g. "when I open
/// a watched app, if Florsent is over budget, turn on Color Filters (grayscale)". FlorsentMonitor
/// keeps the answer current in the App Group: yes once a daily or bedtime budget is reached, no
/// during quiet time after breathing and once the day (or night) starts over.
struct OverBudgetIntent: AppIntent {
    static let title: LocalizedStringResource = "intent.overBudget.title"
    static let description = IntentDescription("intent.overBudget.description")
    static let openAppWhenRun = false
    static let isDiscoverable = true

    func perform() async throws -> some IntentResult & ReturnsValue<Bool> & ProvidesDialog {
        let over = Self.answer()
        return .result(value: over, dialog: IntentDialog(Self.dialog(over)))
    }

    static func answer(store: SharedStore = SharedStore(), now: Date = Date()) -> Bool {
        store.isOverBudget(now: now)
    }

    static func dialog(_ over: Bool) -> LocalizedStringResource {
        over ? "intent.overBudget.yes" : "intent.overBudget.no"
    }
}

/// Opens Florsent straight into breathing practice.
struct StartBreathingIntent: AppIntent {
    static let title: LocalizedStringResource = "intent.breathe.title"
    static let description = IntentDescription("intent.breathe.description")
    static let openAppWhenRun = true

    @MainActor
    func perform() async throws -> some IntentResult {
        SharedStore().requestBreathing()
        return .result()
    }
}

struct FlorsentShortcuts: AppShortcutsProvider {
    static var appShortcuts: [AppShortcut] {
        AppShortcut(
            intent: OverBudgetIntent(),
            phrases: [
                "Is \(.applicationName) over budget?",
                "Am I over budget in \(.applicationName)?",
            ],
            shortTitle: "shortcut.overBudget",
            systemImageName: "hourglass"
        )
        AppShortcut(
            intent: StartBreathingIntent(),
            phrases: [
                "Breathe with \(.applicationName)",
                "Start breathing in \(.applicationName)",
            ],
            shortTitle: "shortcut.breathe",
            systemImageName: "wind"
        )
    }

    static let shortcutTileColor: ShortcutTileColor = .grayGreen
}
