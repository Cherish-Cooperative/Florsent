import DeviceActivity
import FamilyControls
import FlorsentCore
import FlorsentKit
import SwiftUI

extension DeviceActivityReport.Context {
    /// Rendered by the FlorsentReport extension (same name there).
    static let insights = Self("insights")
}

/// Calm insights: breathing from Florsent's own log, and Screen Time usage of the watched apps,
/// which only the sandboxed report extension can read and draw.
struct InsightsView: View {
    @Environment(AppModel.self) private var model
    @Environment(\.palette) private var palette

    private var guidance: GuidanceController { model.guidance }

    var body: some View {
        ScrollView {
            VStack(spacing: 12) {
                Card {
                    Label {
                        Text("insights.calm").font(.subheadline.weight(.semibold))
                    } icon: {
                        Image(systemName: "wind").foregroundStyle(palette.primary.color)
                    }
                    Text(L10n.format("insights.breathsWeek", guidance.calmLog.count(lastDays: 7, now: Date())))
                        .font(.title3)
                    Text(L10n.format("insights.breathsToday", guidance.calmLog.count(lastDays: 1, now: Date())))
                        .font(.subheadline)
                        .foregroundStyle(palette.onSurfaceVariant.color)
                    let quiet = guidance.state.quietMinutesLeft(at: Date())
                    if guidance.isActive, quiet > 0 {
                        Text(L10n.format("grounding.quiet", quiet))
                            .font(.subheadline)
                            .foregroundStyle(palette.primary.color)
                    }
                }
                .accessibilityElement(children: .combine)

                if guidance.isActive {
                    DeviceActivityReport(.insights, filter: filter)
                        .frame(height: 520)
                } else {
                    Button("home.guidance.setupAction") { model.openSettings() }
                        .buttonStyle(PrimaryButtonStyle())
                }
            }
            .padding(.horizontal, 24)
            .padding(.vertical, 16)
            .readableColumn()
        }
        .florsentBackground()
        .navigationTitle(Text("insights.title"))
        .navigationBarTitleDisplayMode(.inline)
        .onAppear { guidance.refresh() }
    }

    /// The last 14 days, day by day, for the watched apps only (this week against last week).
    private var filter: DeviceActivityFilter {
        let calendar = Calendar.current
        let start = calendar.date(byAdding: .day, value: -13, to: calendar.startOfDay(for: Date())) ?? Date()
        let s = guidance.selection
        return DeviceActivityFilter(
            segment: .daily(during: DateInterval(start: start, end: Date())),
            users: .all,
            devices: .init([.iPhone, .iPad]),
            applications: s.applicationTokens,
            categories: s.categoryTokens,
            webDomains: s.webDomainTokens
        )
    }
}
