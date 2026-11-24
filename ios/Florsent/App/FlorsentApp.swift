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
    case insights
}

struct RootView: View {
    @Environment(AppModel.self) private var model
    @Environment(\.palette) private var palette

    var body: some View {
        @Bindable var model = model
        NavigationStack(path: $model.path) {
            HomeView()
                .navigationDestination(for: Route.self) { route in
                    switch route {
                    case .breathe: BreatheView()
                    case .grounding: GroundingView()
                    case .settings: SettingsView()
                    case .bedtime: BedtimeSettingsView()
                    case .insights: InsightsView()
                    }
                }
        }
        .background(palette.background.color.ignoresSafeArea())
    }
}
