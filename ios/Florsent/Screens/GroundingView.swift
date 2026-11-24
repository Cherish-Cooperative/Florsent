import FlorsentKit
import SwiftUI

/// "Back to the present": one short sensory prompt, lots of space, one clear way out. Twelve daytime
/// prompts, and eight bedtime ones late in the evening (taken verbatim from the Android app).
struct GroundingView: View {
    @Environment(AppModel.self) private var model
    @Environment(\.palette) private var palette
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    @State private var night = GroundingPrompts.isNight(hour: Calendar.current.component(.hour, from: Date()))
    @State private var index: Int?

    private var count: Int { night ? GroundingPrompts.nightCount : GroundingPrompts.dayCount }

    var body: some View {
        GeometryReader { geo in
            ScrollView {
                VStack(spacing: 0) {
                    Spacer(minLength: 24)
                    BreathingFlower(expansion: 0.35)
                        .frame(width: 72, height: 72)
                    Text("grounding.label")
                        .font(.subheadline.weight(.semibold))
                        .foregroundStyle(palette.primary.color)
                        .accessibilityAddTraits(.isHeader)
                        .padding(.top, 20)

                    ZStack {
                        if let index {
                            Text(L10n.string(GroundingPrompts.key(night: night, index: index)))
                                .font(.title2)
                                .lineSpacing(8)
                                .multilineTextAlignment(.center)
                                .frame(maxWidth: .infinity)
                                .id(index)
                                .transition(.opacity)
                        }
                    }
                    .animation(.easeInOut(duration: reduceMotion ? 0.2 : 0.7), value: index)
                    .padding(.top, 28)

                    Button("grounding.another") {
                        guard let current = index else { return }
                        index = GroundingPrompts.nextIndex(after: current, count: count, random: Int.random(in: 0..<1_000))
                    }
                    .buttonStyle(QuietButtonStyle())
                    .padding(.top, 16)

                    Spacer(minLength: 32)
                    Button(night ? "grounding.returnNight" : "grounding.return") { model.backToLife() }
                        .buttonStyle(PrimaryButtonStyle())
                        .padding(.bottom, 24)
                }
                .padding(.horizontal, 32)
                .frame(maxWidth: .infinity, minHeight: geo.size.height)
            }
            .scrollBounceBehavior(.basedOnSize)
        }
        .florsentBackground()
        .toolbar(.hidden, for: .navigationBar)
        .navigationBarBackButtonHidden()
        .onAppear {
            #if DEBUG
            if let n = DebugLaunch.night { night = n }
            if let i = DebugLaunch.groundingIndex { index = min(max(i, 0), count - 1) }
            #endif
            if index == nil { index = Int.random(in: 0..<count) }
        }
    }
}
