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

                Button {
                    model.path = [.grounding]
                } label: {
                    HStack {
                        Image(systemName: "leaf")
                            .foregroundStyle(palette.primary.color)
                            .accessibilityHidden(true)
                        Text("home.grounding").font(.body.weight(.medium))
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
                .padding(.top, 12)

                Card {
                    Label {
                        Text("home.guidance.title").font(.subheadline.weight(.semibold))
                    } icon: {
                        Image(systemName: "hourglass").foregroundStyle(palette.primary.color)
                    }
                    Text("home.guidance.body")
                        .font(.subheadline)
                        .foregroundStyle(palette.onSurfaceVariant.color)
                }
                .accessibilityElement(children: .combine)
                .padding(.top, 12)
            }
            .padding(.horizontal, 24)
            .padding(.bottom, 24)
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
