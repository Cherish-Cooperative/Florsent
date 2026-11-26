import FamilyControls
import FlorsentKit
import SwiftUI

/// Settings home: one calm card of a few rows, as on Android. Everything that only matters once
/// gentle guidance is on (budgets, bedtime, grayscale) appears only then, one tap deeper.
struct SettingsView: View {
    @Environment(AppModel.self) private var model
    @Environment(\.palette) private var palette
    @Environment(\.openURL) private var openURL
    @Environment(\.inFormSheet) private var inFormSheet
    @State private var pickerShown = false
    @State private var picked = FamilyActivitySelection()
    @State private var grayscaleShown = false
    @State private var screenOffShown = false

    private var guidance: GuidanceController { model.guidance }

    var body: some View {
        List {
            Section {
                if guidance.isAuthorized {
                    guidanceToggle
                    Button {
                        picked = guidance.selection
                        pickerShown = true
                    } label: {
                        HintRow(title: Text("settings.guidance.apps"), value: appsValue, systemImage: "chevron.right")
                    }
                    .foregroundStyle(palette.onSurface.color)
                    if guidance.settings.enabled {
                        NavigationLink(value: Route.budget) {
                            HintRow(title: Text("settings.row.budget"),
                                    value: Text(verbatim: L10n.duration(minutes: guidance.settings.dailyBudgetMinutes)))
                        }
                        NavigationLink(value: Route.bedtime) {
                            HintRow(title: Text("bedtime.title"), value: bedtimeValue)
                        }
                        Button { grayscaleShown = true } label: {
                            HintRow(title: Text("shortcut.title"), value: Text("shortcut.optional"), systemImage: "chevron.right")
                        }
                        .foregroundStyle(palette.onSurface.color)
                    }
                } else {
                    Button {
                        Task {
                            await guidance.requestAuthorization()
                            if guidance.isAuthorized { picked = guidance.selection; pickerShown = true }
                        }
                    } label: {
                        HintRow(title: Text("settings.guidance.toggle"), hint: authHint, value: Text("bedtime.offShort"),
                                systemImage: "chevron.right")
                    }
                    .foregroundStyle(palette.onSurface.color)
                }
            }
            .listRowBackground(palette.surfaceLow.color)

            Section {
                NavigationLink(value: Route.sound) {
                    HintRow(title: Text("settings.section.breathing"))
                }
                Button { screenOffShown = true } label: {
                    HintRow(title: Text("screenOff.title"),
                            value: model.settings.screenOffShortcut ? Text("screenOff.on") : Text("shortcut.optional"),
                            systemImage: "chevron.right")
                }
                .foregroundStyle(palette.onSurface.color)
                Button {
                    if let url = URL(string: UIApplication.openSettingsURLString) { openURL(url) }
                } label: {
                    HintRow(title: Text("settings.language"), value: Text(verbatim: Self.languageName),
                            systemImage: "arrow.up.forward")
                }
                .foregroundStyle(palette.onSurface.color)
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
        // iPhone: a large title over the list. iPad sheet: a centered inline title beside Done,
        // like the settings sheets in Apple's own iPad apps.
        .navigationBarTitleDisplayMode(inFormSheet ? .inline : .large)
        .familyActivityPicker(isPresented: $pickerShown, selection: $picked)
        .onChange(of: pickerShown) { _, shown in
            if !shown, picked != guidance.selection { guidance.setSelection(picked) }
        }
        .sheet(isPresented: $grayscaleShown) { GrayscaleGuideView() }
        .sheet(isPresented: $screenOffShown) { ScreenOffGuideView() }
    }

    private var guidanceToggle: some View {
        Toggle(isOn: Binding(get: { guidance.settings.enabled }, set: { on in
            if on, !guidance.hasWatchedApps {
                picked = guidance.selection // pick the apps first, then turn it on
                pickerShown = true
            } else {
                Task { await guidance.setEnabled(on) }
            }
        })) {
            Text("settings.guidance.toggle")
        }
        .tint(palette.primary.color)
    }

    private var appsValue: Text {
        guidance.selection.isEmpty ? Text("settings.guidance.appsNone")
            : Text(L10n.format("settings.guidance.appsCount", guidance.selection.itemCount))
    }

    private var bedtimeValue: Text {
        let bed = guidance.settings.bedtime
        return bed.enabled ? Text(verbatim: L10n.clock(hour: bed.hour, minute: bed.minute)) : Text("bedtime.offShort")
    }

    /// Only when the last request failed: one short line on what to do.
    private var authHint: Text? {
        if let hint = guidance.authorizationHint { return Text(LocalizedStringKey(hint.key)) }
        return guidance.authorization == .denied ? Text(LocalizedStringKey(AuthorizationHint.generic.key)) : nil
    }

    static var version: String {
        Bundle.main.object(forInfoDictionaryKey: "CFBundleShortVersionString") as? String ?? "–"
    }

    /// The app's current language in its own words ("繁體中文", "English").
    static var languageName: String {
        let id = Bundle.main.preferredLocalizations.first ?? "en"
        return Locale(identifier: id).localizedString(forIdentifier: id)?.localizedCapitalized ?? id
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

// MARK: - Sub-pages

/// Daily budget and quiet time after breathing.
struct BudgetSettingsView: View {
    @Environment(AppModel.self) private var model
    @Environment(\.palette) private var palette

    private var guidance: GuidanceController { model.guidance }

    var body: some View {
        List {
            Section {
                minutes("budget.daily", GuidanceSettings.dailyChoices, guidance.settings.dailyBudgetMinutes) { m in
                    guidance.update { $0.dailyBudgetMinutes = m }
                }
                minutes("budget.quiet", GuidanceSettings.quietChoices, guidance.settings.quietMinutes) { m in
                    guidance.update { $0.quietMinutes = m }
                }
            } footer: {
                Text("budget.quietHint").font(.footnote).foregroundStyle(palette.onSurfaceVariant.color)
            }
            .listRowBackground(palette.surfaceLow.color)
        }
        .settingsPage("settings.row.budget")
    }

    private func minutes(_ title: LocalizedStringKey, _ choices: [Int], _ value: Int,
                         onChange: @escaping (Int) -> Void) -> some View {
        Picker(title, selection: Binding(get: { value }, set: onChange)) {
            ForEach(choices, id: \.self) { m in Text(verbatim: L10n.duration(minutes: m)).tag(m) }
        }
        .pickerStyle(.menu)
        .tint(palette.primary.color)
    }
}

/// Breathing & sound: ambience, chime, haptics.
struct SoundSettingsView: View {
    @Environment(AppModel.self) private var model
    @Environment(\.palette) private var palette

    var body: some View {
        @Bindable var settings = model.settings
        List {
            Section {
                toggle("settings.ambient", hint: "settings.ambientHint", isOn: $settings.ambientSound)
                toggle("settings.chime", hint: "settings.chimeHint", isOn: $settings.cueSound)
                toggle("settings.haptics", hint: "settings.hapticsHint", isOn: $settings.haptics)
            }
            .listRowBackground(palette.surfaceLow.color)
        }
        .settingsPage("settings.section.breathing")
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
}

extension View {
    /// The shared look of a settings sub-page.
    func settingsPage(_ title: LocalizedStringKey) -> some View {
        listStyle(.insetGrouped)
            .scrollContentBackground(.hidden)
            .readableListWidth()
            .florsentBackground()
            .navigationTitle(Text(title))
            .navigationBarTitleDisplayMode(.inline)
    }
}
