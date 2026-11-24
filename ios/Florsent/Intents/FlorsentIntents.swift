import AppIntents
import FlorsentCore

/// "Florsent: over budget right now?" Returns a Bool for Shortcuts automations, e.g. "when I open
/// Instagram, if Florsent is over budget, turn on Color Filters (grayscale)". FlorsentMonitor keeps
/// the answer current in the App Group (Phase 2); until then it answers false.
struct OverBudgetIntent: AppIntent {
    static let title: LocalizedStringResource = "intent.overBudget.title"
    static let description = IntentDescription("intent.overBudget.description")
    static let openAppWhenRun = false
    static let isDiscoverable = true

    func perform() async throws -> some IntentResult & ReturnsValue<Bool> & ProvidesDialog {
        let over = SharedStore().budget.isOverBudget
        let dialog: LocalizedStringResource = over ? "intent.overBudget.yes" : "intent.overBudget.no"
        return .result(value: over, dialog: IntentDialog(dialog))
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
