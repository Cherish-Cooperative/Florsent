import FamilyControls
import FlorsentCore
import Foundation

/// The apps, categories and sites the person chose in FamilyActivityPicker. The tokens are opaque:
/// Florsent never learns which apps they are. Stored property-list encoded in the App Group.
enum WatchedApps {
    static func load(from store: SharedStore = SharedStore()) -> FamilyActivitySelection {
        guard let data = store.activitySelectionData,
              let selection = try? PropertyListDecoder().decode(FamilyActivitySelection.self, from: data) else {
            return FamilyActivitySelection()
        }
        return selection
    }

    static func save(_ selection: FamilyActivitySelection, to store: SharedStore = SharedStore()) {
        store.activitySelectionData = try? PropertyListEncoder().encode(selection)
    }
}

extension FamilyActivitySelection {
    var isEmpty: Bool { applicationTokens.isEmpty && categoryTokens.isEmpty && webDomainTokens.isEmpty }
    var itemCount: Int { applicationTokens.count + categoryTokens.count + webDomainTokens.count }
}
