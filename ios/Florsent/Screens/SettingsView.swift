import FlorsentKit
import SwiftUI

struct SettingsView: View {
    @Environment(AppModel.self) private var model
    @Environment(\.palette) private var palette
    @Environment(\.openURL) private var openURL

    var body: some View {
        @Bindable var settings = model.settings
        List {
            Section {
                toggle("settings.ambient", hint: "settings.ambientHint", isOn: $settings.ambientSound)
                toggle("settings.chime", hint: "settings.chimeHint", isOn: $settings.cueSound)
                toggle("settings.haptics", hint: "settings.hapticsHint", isOn: $settings.haptics)
            } header: {
                header("settings.section.breathing")
            }
            .listRowBackground(palette.surfaceLow.color)

            Section {
                Button {
                    if let url = URL(string: UIApplication.openSettingsURLString) { openURL(url) }
                } label: {
                    row(title: "settings.language", hint: "settings.languageHint", trailing: "arrow.up.forward")
                }
                .foregroundStyle(palette.onSurface.color)
                row(title: "settings.section.appearance", hint: "settings.appearanceHint", trailing: nil)
                    .accessibilityElement(children: .combine)
            } header: {
                header("settings.section.appearance")
            }
            .listRowBackground(palette.surfaceLow.color)

            Section {
                LabeledContent {
                    Text("settings.screenTimeValue").foregroundStyle(palette.onSurfaceVariant.color)
                } label: {
                    Text("settings.screenTime")
                }
                .accessibilityElement(children: .combine)
                row(title: "settings.shortcut", hint: "settings.shortcutHint", trailing: nil)
                    .accessibilityElement(children: .combine)
            } header: {
                header("settings.section.guidance")
            } footer: {
                Text(L10n.format("settings.footer", Self.version))
                    .font(.footnote)
                    .foregroundStyle(palette.onSurfaceVariant.color)
                    .frame(maxWidth: .infinity)
                    .padding(.top, 24)
            }
            .listRowBackground(palette.surfaceLow.color)
        }
        .listStyle(.insetGrouped)
        .scrollContentBackground(.hidden)
        .florsentBackground()
        .navigationTitle(Text("settings.title"))
        .navigationBarTitleDisplayMode(.large)
    }

    private func toggle(_ title: LocalizedStringKey, hint: LocalizedStringKey, isOn: Binding<Bool>) -> some View {
        Toggle(isOn: isOn) {
            VStack(alignment: .leading, spacing: 2) {
                Text(title)
                Text(hint).font(.footnote).foregroundStyle(palette.onSurfaceVariant.color)
            }
        }
        .tint(palette.primary.color)
    }

    private func row(title: LocalizedStringKey, hint: LocalizedStringKey, trailing: String?) -> some View {
        HStack(alignment: .center) {
            VStack(alignment: .leading, spacing: 2) {
                Text(title)
                Text(hint).font(.footnote).foregroundStyle(palette.onSurfaceVariant.color)
            }
            Spacer(minLength: 8)
            if let trailing {
                Image(systemName: trailing)
                    .font(.footnote.weight(.semibold))
                    .foregroundStyle(palette.onSurfaceVariant.color)
                    .accessibilityHidden(true)
            }
        }
    }

    private func header(_ key: LocalizedStringKey) -> some View {
        Text(key)
            .font(.footnote.weight(.semibold))
            .foregroundStyle(palette.primary.color)
            .textCase(nil)
    }

    static var version: String {
        Bundle.main.object(forInfoDictionaryKey: "CFBundleShortVersionString") as? String ?? "–"
    }
}
