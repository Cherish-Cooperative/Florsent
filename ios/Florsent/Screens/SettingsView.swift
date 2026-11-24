import FamilyControls
import FlorsentKit
import SwiftUI

struct SettingsView: View {
    @Environment(AppModel.self) private var model
    @Environment(\.palette) private var palette
    @Environment(\.openURL) private var openURL

    var body: some View {
        @Bindable var settings = model.settings
        List {
            GuidanceSection()

            ShortcutGuideSection()

            Section {
                toggle("settings.ambient", hint: "settings.ambientHint", isOn: $settings.ambientSound)
                toggle("settings.chime", hint: "settings.chimeHint", isOn: $settings.cueSound)
                toggle("settings.haptics", hint: "settings.hapticsHint", isOn: $settings.haptics)
            } header: {
                SectionHeader(key: "settings.section.breathing")
            }
            .listRowBackground(palette.surfaceLow.color)

            Section {
                Button {
                    if let url = URL(string: UIApplication.openSettingsURLString) { openURL(url) }
                } label: {
                    HintRow(title: Text("settings.language"), hint: Text("settings.languageHint"), systemImage: "arrow.up.forward")
                }
                .foregroundStyle(palette.onSurface.color)
                HintRow(title: Text("settings.section.appearance"), hint: Text("settings.appearanceHint"))
            } header: {
                SectionHeader(key: "settings.section.appearance")
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
        .readableListWidth()
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

    static var version: String {
        Bundle.main.object(forInfoDictionaryKey: "CFBundleShortVersionString") as? String ?? "–"
    }
}

struct SectionHeader: View {
    let key: LocalizedStringKey
    @Environment(\.palette) private var palette

    var body: some View {
        Text(key)
            .font(.footnote.weight(.semibold))
            .foregroundStyle(palette.primary.color)
            .textCase(nil)
            .accessibilityAddTraits(.isHeader)
    }
}

// MARK: - Gentle guidance

/// Screen Time: authorize (individual, on this device), pick the watched apps, then budgets.
private struct GuidanceSection: View {
    @Environment(AppModel.self) private var model
    @Environment(\.palette) private var palette
    @State private var pickerShown = false
    @State private var picked = FamilyActivitySelection()

    private var guidance: GuidanceController { model.guidance }

    var body: some View {
        Section {
            if guidance.isAuthorized {
                Button {
                    picked = guidance.selection
                    pickerShown = true
                } label: {
                    HintRow(title: Text("settings.guidance.apps"), hint: Text("settings.guidance.appsHint"),
                            value: guidance.selection.isEmpty ? Text("settings.guidance.appsNone")
                                : Text(L10n.format("settings.guidance.appsCount", guidance.selection.itemCount)),
                            systemImage: "chevron.right")
                }
                .foregroundStyle(palette.onSurface.color)

                Toggle(isOn: Binding(get: { guidance.settings.enabled },
                                     set: { on in Task { await guidance.setEnabled(on) } })) {
                    VStack(alignment: .leading, spacing: 2) {
                        Text("settings.guidance.toggle")
                        Text("settings.guidance.toggleHint").font(.footnote).foregroundStyle(palette.onSurfaceVariant.color)
                    }
                }
                .tint(palette.primary.color)
                .disabled(guidance.selection.isEmpty)

                minutesPicker("budget.daily", hint: "budget.dailyHint", choices: GuidanceSettings.dailyChoices,
                              value: guidance.settings.dailyBudgetMinutes) { m in guidance.update { $0.dailyBudgetMinutes = m } }
                minutesPicker("budget.quiet", hint: "budget.quietHint", choices: GuidanceSettings.quietChoices,
                              value: guidance.settings.quietMinutes) { m in guidance.update { $0.quietMinutes = m } }

                NavigationLink(value: Route.bedtime) {
                    HintRow(title: Text("bedtime.title"),
                            value: guidance.settings.bedtime.enabled
                                ? Text(verbatim: L10n.clock(hour: guidance.settings.bedtime.hour, minute: guidance.settings.bedtime.minute))
                                : Text("bedtime.offShort"))
                }
            } else {
                Button {
                    Task {
                        await guidance.requestAuthorization()
                        if guidance.isAuthorized { picked = guidance.selection; pickerShown = true }
                    }
                } label: {
                    HintRow(title: Text("settings.guidance.enable"), hint: Text("settings.guidance.enableHint"),
                            systemImage: "hourglass")
                }
                .foregroundStyle(palette.onSurface.color)
                if guidance.failed || guidance.authorization == .denied {
                    Text("settings.guidance.denied")
                        .font(.footnote)
                        .foregroundStyle(palette.onSurfaceVariant.color)
                }
            }
        } header: {
            SectionHeader(key: "settings.section.guidance")
        } footer: {
            Text("settings.guidance.footer")
                .font(.footnote)
                .foregroundStyle(palette.onSurfaceVariant.color)
        }
        .listRowBackground(palette.surfaceLow.color)
        .familyActivityPicker(isPresented: $pickerShown, selection: $picked)
        .onChange(of: pickerShown) { _, shown in
            if !shown, picked != guidance.selection { guidance.setSelection(picked) }
        }
    }

    private func minutesPicker(_ title: LocalizedStringKey, hint: LocalizedStringKey, choices: [Int], value: Int,
                               onChange: @escaping (Int) -> Void) -> some View {
        Picker(selection: Binding(get: { value }, set: onChange)) {
            ForEach(choices, id: \.self) { m in Text(verbatim: L10n.duration(minutes: m)).tag(m) }
        } label: {
            VStack(alignment: .leading, spacing: 2) {
                Text(title)
                Text(hint).font(.footnote).foregroundStyle(palette.onSurfaceVariant.color)
            }
        }
        .pickerStyle(.menu)
        .tint(palette.primary.color)
        .disabled(!guidance.settings.enabled)
    }
}

// MARK: - Shortcuts grayscale

/// Three steps to turn on grayscale with a Shortcuts automation while over budget. iOS gives apps no
/// API for Color Filters, so the person's own automation does it, asking Florsent's App Intent.
private struct ShortcutGuideSection: View {
    @Environment(AppModel.self) private var model
    @Environment(\.palette) private var palette
    @Environment(\.openURL) private var openURL

    static let steps: [LocalizedStringKey] = ["shortcut.step1", "shortcut.step2", "shortcut.step3"]

    var body: some View {
        Section {
            HintRow(title: Text("intent.overBudget.title"),
                    value: model.guidance.state.isOverBudget(at: Date()) ? Text("shortcut.statusYes") : Text("shortcut.statusNo"))
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
                .padding(.vertical, 2)
                .accessibilityElement(children: .combine)
            }
            Button {
                if let url = URL(string: "shortcuts://") { openURL(url) }
            } label: {
                HintRow(title: Text("shortcut.open"), systemImage: "arrow.up.forward")
            }
            .foregroundStyle(palette.primary.color)
        } header: {
            SectionHeader(key: "shortcut.title")
        } footer: {
            Text("shortcut.footer").font(.footnote).foregroundStyle(palette.onSurfaceVariant.color)
        }
        .listRowBackground(palette.surfaceLow.color)
    }
}
