import DeviceActivity
import ExtensionKit
import FlorsentCore
import SwiftUI

/// Calm insights: Screen Time usage of the watched apps, which only this sandboxed process may
/// read. The app embeds `DeviceActivityReport(.insights, filter:)` with the last 14 days, daily.
@main
struct FlorsentReport: DeviceActivityReportExtension {
    var body: some DeviceActivityReportScene {
        InsightsReport { InsightsReportView(data: $0) }
    }
}

extension DeviceActivityReport.Context {
    static let insights = Self("insights")
}

struct InsightsData: Sendable {
    struct Day: Sendable, Identifiable {
        let date: Date
        let minutes: Int
        var id: Date { date }
    }

    struct App: Sendable, Identifiable {
        let name: String
        let minutes: Int
        var id: String { name }
    }

    var today = 0
    var days: [Day] = []        // last 7 days, oldest first
    var thisWeek = 0
    var lastWeek = 0
    var topApps: [App] = []

    var comparison: WeekComparison { .compare(thisWeekMinutes: thisWeek, lastWeekMinutes: lastWeek) }
}

nonisolated struct InsightsReport: DeviceActivityReportScene {
    let context: DeviceActivityReport.Context = .insights
    let content: (InsightsData) -> InsightsReportView

    func makeConfiguration(representing data: DeviceActivityResults<DeviceActivityData>) async -> InsightsData {
        let calendar = Calendar.current
        let today = calendar.startOfDay(for: Date())
        let weekStart = calendar.date(byAdding: .day, value: -6, to: today) ?? today
        let lastWeekStart = calendar.date(byAdding: .day, value: -13, to: today) ?? today
        var perDay: [Date: TimeInterval] = [:]
        var perApp: [String: TimeInterval] = [:]

        for await activity in data {
            for await segment in activity.activitySegments {
                let day = calendar.startOfDay(for: segment.dateInterval.start)
                perDay[day, default: 0] += segment.totalActivityDuration
                guard day >= weekStart else { continue }
                for await category in segment.categories {
                    for await app in category.applications {
                        let name = app.application.localizedDisplayName ?? app.application.bundleIdentifier ?? "–"
                        perApp[name, default: 0] += app.totalActivityDuration
                    }
                }
            }
        }

        func minutes(_ t: TimeInterval) -> Int { Int(t / 60) }
        var result = InsightsData()
        result.today = minutes(perDay[today] ?? 0)
        result.days = (0..<7).compactMap { offset in
            calendar.date(byAdding: .day, value: offset, to: weekStart).map { Day(date: $0, minutes: minutes(perDay[$0] ?? 0)) }
        }
        result.thisWeek = minutes(perDay.filter { $0.key >= weekStart }.values.reduce(0, +))
        result.lastWeek = minutes(perDay.filter { $0.key >= lastWeekStart && $0.key < weekStart }.values.reduce(0, +))
        result.topApps = perApp.sorted { $0.value > $1.value }.prefix(3)
            .map { App(name: $0.key, minutes: minutes($0.value)) }
            .filter { $0.minutes > 0 }
        return result
    }
}

private typealias Day = InsightsData.Day
private typealias App = InsightsData.App

// MARK: - View

struct InsightsReportView: View {
    let data: InsightsData
    @Environment(\.colorScheme) private var scheme

    private var dark: Bool { scheme == .dark }
    private var primary: Color { dark ? Color(hex: 0xA8C3A6) : Color(hex: 0x56705A) }
    private var muted: Color { dark ? Color(hex: 0xC1C3BA) : Color(hex: 0x5B5E56) }
    private var onSurface: Color { dark ? Color(hex: 0xE4E3DC) : Color(hex: 0x23251F) }
    private var surface: Color { dark ? Color(hex: 0x1A1C19) : Color(hex: 0xF0EEE8) }
    private var track: Color { dark ? Color(hex: 0x43463F) : Color(hex: 0xD3D0C6) }

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            card {
                Text("report.today").font(.subheadline).foregroundStyle(muted)
                Text(Self.duration(data.today))
                    .font(.system(.largeTitle, design: .rounded, weight: .light).monospacedDigit())
                    .foregroundStyle(primary)
                Text(insight).font(.subheadline).foregroundStyle(onSurface)
            }
            .accessibilityElement(children: .combine)

            card {
                Text("report.lastSeven").font(.subheadline.weight(.semibold)).foregroundStyle(onSurface)
                bars
            }

            if !data.topApps.isEmpty {
                card {
                    Text("report.topApps").font(.subheadline.weight(.semibold)).foregroundStyle(onSurface)
                    ForEach(data.topApps) { app in
                        HStack {
                            Text(verbatim: app.name).foregroundStyle(onSurface)
                            Spacer(minLength: 8)
                            Text(Self.duration(app.minutes)).monospacedDigit().foregroundStyle(muted)
                        }
                        .font(.body)
                        .accessibilityElement(children: .combine)
                    }
                }
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }

    private var insight: String {
        switch data.comparison {
        case .notEnough: String(localized: "report.notEnough")
        case let .less(minutes): String(format: String(localized: "report.less"), Self.duration(minutes))
        case .more: String(localized: "report.more")
        case .similar: String(localized: "report.similar")
        }
    }

    private var bars: some View {
        let peak = max(data.days.map(\.minutes).max() ?? 0, 1)
        return HStack(alignment: .bottom, spacing: 8) {
            ForEach(data.days) { day in
                VStack(spacing: 6) {
                    GeometryReader { geo in
                        VStack {
                            Spacer(minLength: 0)
                            Capsule()
                                .fill(day.minutes > 0 ? primary : track)
                                .frame(height: max(6, geo.size.height * CGFloat(day.minutes) / CGFloat(peak)))
                        }
                    }
                    .frame(height: 96)
                    Text(day.date, format: .dateTime.weekday(.narrow))
                        .font(.caption2)
                        .foregroundStyle(muted)
                }
                .frame(maxWidth: .infinity)
                .accessibilityElement(children: .ignore)
                .accessibilityLabel(Text(verbatim: String(format: String(localized: "report.bar.a11y"),
                    day.date.formatted(.dateTime.weekday(.wide).month().day()),
                    day.minutes > 0 ? Self.duration(day.minutes) : String(localized: "report.noRecord"))))
            }
        }
    }

    private func card<C: View>(@ViewBuilder _ content: () -> C) -> some View {
        VStack(alignment: .leading, spacing: 6) { content() }
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(.horizontal, 20)
            .padding(.vertical, 16)
            .background(RoundedRectangle(cornerRadius: 20, style: .continuous).fill(surface))
    }

    static func duration(_ minutes: Int) -> String {
        let h = minutes / 60, m = minutes % 60
        if h == 0 { return String(format: String(localized: "report.minutes"), m) }
        if m == 0 { return String(format: String(localized: "report.hours"), h) }
        return String(format: String(localized: "report.hoursMinutes"), h, m)
    }
}

private extension Color {
    init(hex: UInt32) {
        self.init(red: Double((hex >> 16) & 0xFF) / 255, green: Double((hex >> 8) & 0xFF) / 255, blue: Double(hex & 0xFF) / 255)
    }
}
