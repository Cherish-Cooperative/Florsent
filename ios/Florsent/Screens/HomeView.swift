import FlorsentKit
import SwiftUI

struct HomeView: View {
    @Environment(AppModel.self) private var model
    @Environment(\.palette) private var palette
    @Environment(\.dynamicTypeSize) private var typeSize

    var body: some View {
        ScrollView {
            VStack(spacing: 0) {
                IdleBreathingFlower()
                    .frame(width: flowerSize, height: flowerSize)
                    .padding(.top, 8)

                Text(L10n.greeting(hour: Calendar.current.component(.hour, from: Date())))
                    .font(.title.weight(.medium))
                    .multilineTextAlignment(.center)
                    .accessibilityAddTraits(.isHeader)
                    .padding(.top, 12)
                Text("home.subtitle")
                    .font(.body)
                    .foregroundStyle(palette.onSurfaceVariant.color)
                    .multilineTextAlignment(.center)
                    .padding(.top, 6)

                Button("home.breatheNow") { model.openBreathing() }
                    .buttonStyle(PrimaryButtonStyle())
                    .padding(.top, 28)

                NavRow(title: "home.grounding", systemImage: "leaf") { model.path = [.grounding] }
                    .padding(.top, 12)

                if model.guidance.isActive {
                    NavRow(title: "home.insights", systemImage: "chart.bar") { model.path = [.insights] }
                        .padding(.top, 8)
                }

                GuidanceCard()
                    .padding(.top, 12)
            }
            .padding(.horizontal, 24)
            .padding(.bottom, 24)
            .readableColumn()
        }
        .florsentBackground()
        .toolbar {
            ToolbarItem(placement: .topBarTrailing) {
                NavigationLink(value: Route.settings) {
                    Image(systemName: "gearshape")
                }
                .accessibilityLabel(Text("settings.title"))
            }
        }
        .navigationBarTitleDisplayMode(.inline)
    }

    private var flowerSize: CGFloat { typeSize.isAccessibilitySize ? 140 : 220 }
}

/// A quiet full-width row ("Back to the present", "Calm insights").
private struct NavRow: View {
    let title: LocalizedStringKey
    let systemImage: String
    let action: () -> Void
    @Environment(\.palette) private var palette

    var body: some View {
        Button(action: action) {
            HStack {
                Image(systemName: systemImage)
                    .foregroundStyle(palette.primary.color)
                    .accessibilityHidden(true)
                Text(title).font(.body.weight(.medium))
                Spacer(minLength: 8)
                Image(systemName: "chevron.right")
                    .font(.footnote.weight(.semibold))
                    .foregroundStyle(palette.onSurfaceVariant.color)
                    .accessibilityHidden(true)
            }
            .padding(.horizontal, 20)
            .frame(minHeight: 56)
            .background(RoundedRectangle(cornerRadius: 20, style: .continuous).fill(palette.surfaceLow.color))
            .contentShape(RoundedRectangle(cornerRadius: 20))
        }
        .buttonStyle(.plain)
    }
}

/// Gentle guidance at a glance: an invitation to set it up, or today's budget, bedtime and quiet time.
private struct GuidanceCard: View {
    @Environment(AppModel.self) private var model
    @Environment(\.palette) private var palette

    var body: some View {
        let guidance = model.guidance
        Card {
            Label {
                Text("home.guidance.title").font(.subheadline.weight(.semibold))
            } icon: {
                Image(systemName: "hourglass").foregroundStyle(palette.primary.color)
            }
            if guidance.isActive {
                Text(verbatim: summary(guidance))
                    .font(.subheadline)
                    .foregroundStyle(palette.onSurfaceVariant.color)
                let quiet = guidance.state.quietMinutesLeft(at: Date())
                if quiet > 0 {
                    Text(L10n.format("grounding.quiet", quiet))
                        .font(.subheadline)
                        .foregroundStyle(palette.primary.color)
                } else if guidance.state.isOverBudget(at: Date()) {
                    Text("home.guidance.over")
                        .font(.subheadline)
                        .foregroundStyle(palette.primary.color)
                }
            } else {
                Text("home.guidance.body")
                    .font(.subheadline)
                    .foregroundStyle(palette.onSurfaceVariant.color)
                Button("home.guidance.setupAction") { model.path = [.settings] }
                    .font(.subheadline.weight(.semibold))
                    .foregroundStyle(palette.primary.color)
                    .frame(minHeight: 44)
            }
        }
        .accessibilityElement(children: .combine)
    }

    private func summary(_ g: GuidanceController) -> String {
        let daily = L10n.format("home.guidance.daily", L10n.duration(minutes: g.settings.dailyBudgetMinutes))
        guard g.settings.bedtime.enabled else { return daily }
        let bed = L10n.format("home.guidance.bedtime", L10n.clock(hour: g.settings.bedtime.hour, minute: g.settings.bedtime.minute))
        return daily + " · " + bed
    }
}
