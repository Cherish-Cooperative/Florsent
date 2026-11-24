import FlorsentKit
import SwiftUI
import UserNotifications

@main
struct FlorsentApp: App {
    @UIApplicationDelegateAdaptor(AppDelegate.self) private var appDelegate
    @State private var model = AppModel()
    @Environment(\.scenePhase) private var scenePhase

    var body: some Scene {
        WindowGroup {
            RootView()
                .environment(model)
                .florsentTheme()
                .task {
                    appDelegate.model = model
                    NotificationSound.install()
                }
                .onChange(of: scenePhase, initial: true) { _, phase in
                    if phase == .active { model.becameActive() }
                }
        }
    }
}

/// Routes taps on guidance notifications ("How about a short break?") straight into breathing.
final class AppDelegate: NSObject, UIApplicationDelegate, UNUserNotificationCenterDelegate {
    weak var model: AppModel? {
        didSet {
            if let tap = pendingTap { pendingTap = nil; model?.openFromNotification(tap) }
        }
    }
    private var pendingTap: NotificationTap?

    func application(_ application: UIApplication,
                     didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]? = nil) -> Bool {
        UNUserNotificationCenter.current().delegate = self
        return true
    }

    nonisolated func userNotificationCenter(_ center: UNUserNotificationCenter, didReceive response: UNNotificationResponse) async {
        let tap = NotificationTap(userInfo: response.notification.request.content.userInfo)
        await MainActor.run {
            if let model { model.openFromNotification(tap) } else { pendingTap = tap }
        }
    }

    nonisolated func userNotificationCenter(_ center: UNUserNotificationCenter,
                                            willPresent notification: UNNotification) async -> UNNotificationPresentationOptions {
        [.banner, .sound]
    }
}

enum Route: Hashable {
    case breathe
    case grounding
    case settings
    case bedtime
    case budget
    case sound
    case insights
}

struct RootView: View {
    @Environment(AppModel.self) private var model
    @Environment(\.palette) private var palette
    @Environment(\.horizontalSizeClass) private var sizeClass

    var body: some View {
        @Bindable var model = model
        NavigationStack(path: $model.path) {
            HomeView()
                .navigationDestination(for: Route.self) { route in
                    switch route {
                    case .breathe: BreatheView()
                    case .grounding: GroundingView()
                    case .insights: InsightsView()
                    default: SettingsPage(route: route)
                    }
                }
        }
        .background(palette.background.color.ignoresSafeArea())
        .sheet(isPresented: $model.settingsSheetShown) { SettingsSheet() }
        .onChange(of: sizeClass, initial: true) { _, size in placeSettings(regular: size == .regular) }
    }

    /// Moves an open Settings between the sheet (regular width) and the stack (compact width).
    /// Decided by size class, not device: Split View, Slide Over and Stage Manager change the
    /// width at any time (Apple's app-resizability guidance).
    private func placeSettings(regular: Bool) {
        model.settingsAsSheet = regular
        if regular, model.path.first == .settings {
            model.settingsPath = Array(model.path.dropFirst())
            model.path = []
            model.settingsSheetShown = true
        } else if !regular, model.settingsSheetShown {
            model.settingsSheetShown = false
            model.path = [.settings] + model.settingsPath
        }
    }
}

/// Settings and its sub-pages.
private struct SettingsPage: View {
    let route: Route

    var body: some View {
        switch route {
        case .bedtime: BedtimeSettingsView()
        case .budget: BudgetSettingsView()
        case .sound: SoundSettingsView()
        default: SettingsView()
        }
    }
}

/// iPad: Settings as a centered form sheet. Title, Done and rows share one column, so nothing is
/// pinned to the far edge of a wide screen.
private struct SettingsSheet: View {
    @Environment(AppModel.self) private var model
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        @Bindable var model = model
        NavigationStack(path: $model.settingsPath) {
            SettingsView()
                .toolbar {
                    ToolbarItem(placement: .topBarTrailing) { CloseButton { dismiss() } }
                }
                .navigationDestination(for: Route.self) { SettingsPage(route: $0) }
        }
        .environment(\.inFormSheet, true)
        .formSheetSizing()
        .florsentTheme()
    }
}

/// iOS 26: the standard Liquid Glass close button (HIG: use the system Close button, not a text
/// label). Changes in Settings apply at once, so there is nothing to confirm or cancel.
private struct CloseButton: View {
    let action: () -> Void

    var body: some View {
        if #available(iOS 26.0, *) {
            Button(role: .close, action: action)
        } else {
            Button("shortcut.done", action: action)
        }
    }
}

private extension View {
    @ViewBuilder func formSheetSizing() -> some View {
        if #available(iOS 18.0, *) { presentationSizing(.form) } else { self }
    }
}
