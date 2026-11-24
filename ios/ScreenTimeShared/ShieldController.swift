import FamilyControls
import ManagedSettings

extension ManagedSettingsStore.Name {
    static var florsent: Self { Self("florsent") }
}

/// The soft shield (stage two). Only Florsent's own named store is touched, and only the watched
/// apps; "5 more minutes" or finishing a breath clears it.
enum ShieldController {
    static func raise(over selection: FamilyActivitySelection) {
        guard !selection.isEmpty else { return }
        let store = ManagedSettingsStore(named: .florsent)
        store.shield.applications = selection.applicationTokens.isEmpty ? nil : selection.applicationTokens
        store.shield.webDomains = selection.webDomainTokens.isEmpty ? nil : selection.webDomainTokens
        let categories: ShieldSettings.ActivityCategoryPolicy<Application>? =
            selection.categoryTokens.isEmpty ? nil : .specific(selection.categoryTokens)
        store.shield.applicationCategories = categories
        store.shield.webDomainCategories = selection.categoryTokens.isEmpty ? nil : .specific(selection.categoryTokens)
    }

    static func lower() {
        ManagedSettingsStore(named: .florsent).clearAllSettings()
    }
}
