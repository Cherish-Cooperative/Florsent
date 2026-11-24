import FlorsentKit
import SwiftUI

@main
struct FlorsentApp: App {
    @State private var model = AppModel()
    @Environment(\.scenePhase) private var scenePhase

    var body: some Scene {
        WindowGroup {
            RootView()
                .environment(model)
                .florsentTheme()
                .task { NotificationSound.install() }
                .onChange(of: scenePhase, initial: true) { _, phase in
                    if phase == .active { model.openPendingBreathingRequest() }
                }
        }
    }
}

enum Route: Hashable {
    case breathe
    case grounding
    case settings
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
                    }
                }
        }
        .background(palette.background.color.ignoresSafeArea())
    }
}
