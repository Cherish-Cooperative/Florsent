import FlorsentKit
import SwiftUI

struct BreathingSessionView: View {
    let session: BreathingSessionModel
    let onEndEarly: () -> Void

    @Environment(AppModel.self) private var model
    @Environment(\.palette) private var palette
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @Environment(\.scenePhase) private var scenePhase

    var body: some View {
        GeometryReader { geo in
            ScrollView {
                VStack(spacing: 0) {
                    Spacer(minLength: 24)
                    flower
                        .frame(width: flowerSize(geo.size), height: flowerSize(geo.size))
                    PhaseLabel(kind: session.display.kind)
                        .padding(.top, 32)
                    counters
                        .padding(.top, 4)
                    Spacer(minLength: 24)
                    Button("breathe.endEarly", action: onEndEarly)
                        .buttonStyle(QuietButtonStyle())
                        .padding(.bottom, 16)
                }
                .padding(.horizontal, 24)
                .readableColumn()
                .frame(minHeight: geo.size.height)
            }
            .scrollBounceBehavior(.basedOnSize)
        }
        .florsentBackground()
        .toolbar(.hidden, for: .navigationBar)
        .navigationBarBackButtonHidden()
        .persistentSystemOverlays(.hidden)
        .onAppear { UIApplication.shared.isIdleTimerDisabled = true }
        .onDisappear { UIApplication.shared.isIdleTimerDisabled = false }
        .onChange(of: scenePhase) { _, phase in
            switch phase {
            case .active:
                session.resume()
                model.sound.resume()
            case .background:
                session.pause()
                model.sound.pause()
            default: break
            }
        }
    }

    /// The flower redraws every frame only while it moves: Reduce Motion and breath holds pause it.
    private var flower: some View {
        let display = session.display
        let still = reduceMotion || display.kind.isHold || !session.isRunning
        return TimelineView(.animation(paused: still)) { context in
            let ms = session.elapsedMs(at: still ? Date() : context.date)
            let pattern = session.pattern
            BreathingFlower(
                expansion: reduceMotion
                    // No continuous motion: open while breathing in or holding, closed otherwise.
                    ? (display.kind == .inhale || display.kind == .holdIn ? 0.9 : 0.35)
                    : BreathingTimeline.expansion(pattern, at: ms),
                // Petal drift runs on breathing time only, so the flower is completely still while holding.
                time: reduceMotion ? 0 : Double(BreathingTimeline.movingMs(pattern, at: ms)) / 1000,
                progress: BreathingTimeline.sessionProgress(pattern, at: ms)
            )
        }
    }

    /// Seconds and round, read by VoiceOver as one quiet element (not announced every second).
    private var counters: some View {
        let d = session.display
        return VStack(spacing: 12) {
            Text(d.finished ? " " : "\(d.secondsLeft)")
                .font(.system(.largeTitle, design: .rounded, weight: .light).monospacedDigit())
                .foregroundStyle(palette.primary.color)
                .contentTransition(.numericText(countsDown: true))
                .animation(reduceMotion ? nil : .easeInOut(duration: 0.3), value: d.secondsLeft)
            Text(L10n.format("breathe.cycle", d.cycleIndex + 1, session.pattern.cycles))
                .font(.subheadline)
                .foregroundStyle(palette.onSurfaceVariant.color)
        }
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(Text(verbatim: L10n.format("breathe.secondsLeft", d.secondsLeft) + ", "
            + L10n.format("breathe.cycle", d.cycleIndex + 1, session.pattern.cycles)))
    }

    private func flowerSize(_ size: CGSize) -> CGFloat {
        // Fits the flower to the space (landscape, small phones, large text); scrolls as a last resort.
        max(min(size.height * 0.38, 300, min(size.width, Layout.column) - 48), 120)
    }
}

/// "Inhale" / "Hold" / "Exhale", crossfading between phases. VoiceOver hears it through an
/// announcement from the session (see BreatheView), so only one label is ever read.
private struct PhaseLabel: View {
    let kind: BreathPhaseKind
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    var body: some View {
        ZStack {
            Text(L10n.phase(kind))
                .font(.title.weight(.medium))
                .kerning(0.5)
                .multilineTextAlignment(.center)
                .id(kind)
                .transition(.opacity)
        }
        .animation(.easeInOut(duration: reduceMotion ? 0.2 : 0.6), value: kind)
        .accessibilityElement(children: .combine)
        .accessibilityAddTraits(.isHeader)
    }
}
