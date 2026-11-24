import FlorsentKit
import SwiftUI

/// Optional: grayscale through the person's own Shortcuts automation (iOS gives apps no API for
/// Color Filters). Step copy uses the exact Shortcuts and Settings labels of iOS 26, so each step can
/// be followed by searching for the quoted word.
struct GrayscaleGuideView: View {
    @Environment(AppModel.self) private var model
    @Environment(\.palette) private var palette
    @Environment(\.openURL) private var openURL
    @Environment(\.dismiss) private var dismiss

    static let steps: [LocalizedStringKey] = ["shortcut.step1", "shortcut.step2", "shortcut.step3", "shortcut.step4"]

    var body: some View {
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
                    Text("shortcut.footer").font(.footnote).foregroundStyle(palette.onSurfaceVariant.color)
                }
                .listRowBackground(palette.surfaceLow.color)

                Section {
                    HintRow(title: Text("intent.overBudget.title"),
                            value: model.guidance.state.isOverBudget(at: Date()) ? Text("shortcut.statusYes") : Text("shortcut.statusNo"))
                    Button {
                        if let url = URL(string: "shortcuts://") { openURL(url) }
                    } label: {
                        HintRow(title: Text("shortcut.open"), systemImage: "arrow.up.forward")
                    }
                    .foregroundStyle(palette.primary.color)
                }
                .listRowBackground(palette.surfaceLow.color)
            }
            .settingsPage("shortcut.title")
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
