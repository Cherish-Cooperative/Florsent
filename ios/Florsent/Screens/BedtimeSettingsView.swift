import FlorsentKit
import SwiftUI

/// Bedtime guidance: from the chosen time until 05:00, a reminder after a few minutes of scrolling
/// (notification first, then the soft shield, at most two a night), with 4-7-8 breathing suggested.
struct BedtimeSettingsView: View {
    @Environment(AppModel.self) private var model
    @Environment(\.palette) private var palette

    private var guidance: GuidanceController { model.guidance }
    private var bed: BedtimeSettings { guidance.settings.bedtime }

    /// Half-hour steps from 20:00 to 02:00, as minutes after midnight.
    static let times: [Int] = (0..<13).map { (20 * 60 + $0 * 30) % (24 * 60) }

    var body: some View {
        List {
            Section {
                Toggle(isOn: Binding(get: { bed.enabled }, set: { on in guidance.update { $0.bedtime.enabled = on } })) {
                    VStack(alignment: .leading, spacing: 2) {
                        Text("bedtime.title")
                        Text(verbatim: status).font(.footnote).foregroundStyle(palette.onSurfaceVariant.color)
                    }
                }
                .tint(palette.primary.color)
                .disabled(!guidance.isActive)

                if bed.enabled {
                    Picker(selection: Binding(get: { bed.hour * 60 + bed.minute }, set: { v in
                        guidance.update { $0.bedtime.hour = v / 60; $0.bedtime.minute = v % 60 }
                    })) {
                        ForEach(Self.times, id: \.self) { v in
                            Text(verbatim: L10n.clock(hour: v / 60, minute: v % 60)).tag(v)
                        }
                    } label: {
                        Text("bedtime.time")
                    }
                    .pickerStyle(.menu)
                    .tint(palette.primary.color)

                    Picker(selection: Binding(get: { bed.thresholdMinutes }, set: { m in
                        guidance.update { $0.bedtime.thresholdMinutes = m }
                    })) {
                        ForEach(BedtimeSettings.thresholdChoices, id: \.self) { m in
                            Text(verbatim: L10n.duration(minutes: m)).tag(m)
                        }
                    } label: {
                        Text("bedtime.threshold")
                    }
                    .pickerStyle(.menu)
                    .tint(palette.primary.color)
                }
            } header: {
                SectionHeader(key: "bedtime.title")
            } footer: {
                Text(guidance.isActive ? "bedtime.note" : "bedtime.needsGuidance")
                    .font(.footnote)
                    .foregroundStyle(palette.onSurfaceVariant.color)
            }
            .listRowBackground(palette.surfaceLow.color)

            Section {
                VStack(alignment: .leading, spacing: 6) {
                    Label {
                        Text("bedtime.suggest.title").font(.subheadline.weight(.semibold))
                    } icon: {
                        Image(systemName: "moon.stars").foregroundStyle(palette.primary.color)
                    }
                    Text("bedtime.suggest.body")
                        .font(.subheadline)
                        .foregroundStyle(palette.onSurfaceVariant.color)
                }
                .padding(.vertical, 4)
                .accessibilityElement(children: .combine)
                Button {
                    model.settings.patternId = BreathingPatterns.relax478.id
                    model.openBreathing(bedtime: true)
                } label: {
                    HintRow(title: Text("bedtime.suggest.try"), systemImage: "chevron.right")
                }
                .foregroundStyle(palette.primary.color)
            }
            .listRowBackground(palette.surfaceLow.color)
        }
        .listStyle(.insetGrouped)
        .scrollContentBackground(.hidden)
        .readableListWidth()
        .florsentBackground()
        .navigationTitle(Text("bedtime.title"))
        .navigationBarTitleDisplayMode(.inline)
    }

    private var status: String {
        bed.enabled && guidance.isActive
            ? L10n.format("bedtime.current", L10n.clock(hour: bed.hour, minute: bed.minute), bed.thresholdMinutes)
            : L10n.string("bedtime.off")
    }
}
