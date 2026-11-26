import FlorsentKit
import SwiftUI
import UIKit

/// iOS gives apps no way to lock the screen. Shortcuts has a "Lock Screen" action, so the person
/// makes a one-action shortcut once and "Back to life" runs it by name.
enum ScreenOffShortcut {
    /// Fixed and language-neutral, so changing the iPhone's language never breaks it.
    static let name = "Florsent Lock"

    static var url: URL? {
        var parts = URLComponents()
        parts.scheme = "shortcuts"
        parts.host = "run-shortcut"
        parts.queryItems = [URLQueryItem(name: "name", value: name)]
        return parts.url
    }

    @MainActor static func run() {
        guard let url else { return }
        UIApplication.shared.open(url)
    }
}

/// Optional: turn the screen off after "Back to life" through a one-action Shortcut. Step copy uses
/// the exact iOS 26 Shortcuts labels (zh_TW read from the iOS 26.5 runtime: 鎖定螢幕).
struct ScreenOffGuideView: View {
    @Environment(AppModel.self) private var model
    @Environment(\.palette) private var palette
    @Environment(\.openURL) private var openURL
    @Environment(\.dismiss) private var dismiss

    static let steps: [LocalizedStringKey] = ["screenOff.step1", "screenOff.step2", "screenOff.step3"]

    var body: some View {
        @Bindable var settings = model.settings
        NavigationStack {
            List {
                Section {
                    ForEach(Array(Self.steps.enumerated()), id: \.offset) { i, step in
                        HStack(alignment: .firstTextBaseline, spacing: 12) {
                            Text(verbatim: "\(i + 1)")
                                .font(.subheadline.weight(.semibold).monospacedDigit())
                                .foregroundStyle(palette.onPrimaryContainer.color)
                                .frame(width: 28, height: 28)
                                .background(Circle().fill(palette.primaryContainer.color))
                                .accessibilityHidden(true)
                            Text(step).font(.subheadline)
                        }
                        .padding(.vertical, 4)
                        .accessibilityElement(children: .combine)
                    }
                } footer: {
                    Text("screenOff.footer").font(.footnote).foregroundStyle(palette.onSurfaceVariant.color)
                }
                .listRowBackground(palette.surfaceLow.color)

                Section {
                    Button {
                        if let url = URL(string: "shortcuts://") { openURL(url) }
                    } label: {
                        HintRow(title: Text("shortcut.open"), systemImage: "arrow.up.forward")
                    }
                    .foregroundStyle(palette.primary.color)
                    Toggle(isOn: $settings.screenOffShortcut) {
                        Text("screenOff.toggle").font(.body)
                    }
                    .tint(palette.primary.color)
                }
                .listRowBackground(palette.surfaceLow.color)
            }
            .settingsPage("screenOff.title")
            .toolbar {
                ToolbarItem(placement: .confirmationAction) {
                    Button("shortcut.done") { dismiss() }
                }
            }
        }
        .presentationDetents([.large])
        .florsentTheme()
    }
}
