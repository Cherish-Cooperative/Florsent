import DeviceActivity
import ExtensionKit
import SwiftUI

/// Screen Time report extension: renders usage that only this sandboxed process may read. The app
/// will embed `DeviceActivityReport(.today, filter:)` on the home screen in Phase 2.
@main
struct FlorsentReport: DeviceActivityReportExtension {
    var body: some DeviceActivityReportScene {
        TodayReport { minutes in TodayReportView(minutes: minutes) }
    }
}

extension DeviceActivityReport.Context {
    static let today = Self("today")
}

nonisolated struct TodayReport: DeviceActivityReportScene {
    let context: DeviceActivityReport.Context = .today
    let content: (Int) -> TodayReportView

    func makeConfiguration(representing data: DeviceActivityResults<DeviceActivityData>) async -> Int {
        var total: TimeInterval = 0
        for await activity in data {
            for await segment in activity.activitySegments {
                total += segment.totalActivityDuration
            }
        }
        return Int(total / 60)
    }
}

struct TodayReportView: View {
    let minutes: Int
    @Environment(\.colorScheme) private var scheme

    var body: some View {
        VStack(alignment: .leading, spacing: 4) {
            Text("report.today")
                .font(.subheadline)
                .foregroundStyle(scheme == .dark ? Color(red: 0.757, green: 0.765, blue: 0.729) : Color(red: 0.357, green: 0.369, blue: 0.337))
            Text(String(format: String(localized: "report.minutes"), minutes))
                .font(.system(.largeTitle, design: .rounded, weight: .light).monospacedDigit())
                .foregroundStyle(scheme == .dark ? Color(red: 0.659, green: 0.765, blue: 0.651) : Color(red: 0.337, green: 0.439, blue: 0.353))
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .accessibilityElement(children: .combine)
    }
}
