import FlorsentKit
import SwiftUI

/// Pattern picker, then the session (one route, two stages, like Android's BreatheScreen).
struct BreatheView: View {
    @Environment(AppModel.self) private var model
    @State private var session: BreathingSessionModel?
    @State private var sessionCount = 0

    var body: some View {
        ZStack {
            if let session {
                BreathingSessionView(session: session) { endSession() }
                    .transition(.opacity)
            } else {
                PatternPicker(onStart: startSession)
                    .transition(.opacity)
            }
        }
        .animation(.easeInOut(duration: 0.5), value: session == nil)
        .onAppear {
            #if DEBUG
            if DebugLaunch.screen == "session", session == nil { startSession() }
            #endif
        }
        .onDisappear { session?.stop(); model.sound.stopAmbient() }
    }

    private func startSession() {
        sessionCount += 1
        var frozen: Int64?
        #if DEBUG
        frozen = DebugLaunch.freezeMs
        #endif
        let s = BreathingSessionModel(pattern: model.settings.pattern, sessionId: sessionCount, frozenMs: frozen)
        let settings = model.settings
        s.onPhase = { [weak model] display in
            guard let model else { return }
            if settings.haptics { model.haptics.play(display.kind) }
            if settings.cueSound { model.sound.playCue(display.kind) }
            AccessibilityNotification.Announcement(L10n.phase(display.kind)).post()
        }
        s.onFinish = { [weak model] in model?.finishBreathing() }
        if settings.haptics { model.haptics.prepare() }
        if settings.ambientSound, frozen == nil { model.sound.startAmbient() }
        session = s
        s.start()
    }

    private func endSession() {
        session?.stop()
        model.sound.stopAmbient()
        model.finishBreathing()
    }
}

// MARK: - Picker

private struct PatternPicker: View {
    let onStart: () -> Void
    @Environment(AppModel.self) private var model
    @Environment(\.palette) private var palette

    var body: some View {
        @Bindable var settings = model.settings
        ScrollView {
            VStack(spacing: 8) {
                IdleBreathingFlower()
                    .frame(width: 140, height: 140)
                    .padding(.bottom, 8)
                if model.bedtimeSuggestion { BedtimeHint() }
                ForEach(BreathingPatterns.all) { pattern in
                    PatternCard(pattern: pattern, selected: pattern.id == settings.patternId) {
                        settings.patternId = pattern.id
                    }
                }
            }
            .padding(.horizontal, 24)
            .padding(.bottom, 16)
            .readableColumn()
        }
        .scrollBounceBehavior(.basedOnSize)
        .safeAreaInset(edge: .bottom) {
            VStack(spacing: 12) {
                HStack(spacing: 8) {
                    ToggleChip(title: "breathe.ambient", isOn: $settings.ambientSound)
                    ToggleChip(title: "breathe.chime", isOn: $settings.cueSound)
                }
                .frame(maxWidth: .infinity) // centered, as on Android
                Button("breathe.start", action: onStart)
                    .buttonStyle(PrimaryButtonStyle())
            }
            .padding(.horizontal, 24)
            .padding(.top, 12)
            .padding(.bottom, 8)
            .readableColumn()
            .background(palette.background.color.opacity(0.96).ignoresSafeArea())
        }
        .florsentBackground()
        .navigationTitle(Text("breathe.title"))
        .navigationBarTitleDisplayMode(.inline)
        .onChange(of: settings.cueSound, initial: true) { _, on in if on { model.sound.warmUp() } }
    }
}

/// In the bedtime window: a gentle suggestion of 4-7-8, never a silent switch.
private struct BedtimeHint: View {
    @Environment(AppModel.self) private var model
    @Environment(\.palette) private var palette

    var body: some View {
        let using478 = model.settings.patternId == BreathingPatterns.relax478.id
        VStack(alignment: .leading, spacing: 6) {
            Label {
                Text("bedtime.suggest.title").font(.subheadline.weight(.semibold))
            } icon: {
                Image(systemName: "moon.stars").foregroundStyle(palette.primary.color)
            }
            Text("bedtime.suggest.body")
                .font(.subheadline)
                .foregroundStyle(palette.onSurfaceVariant.color)
            if !using478 {
                Button("bedtime.suggest.use") { model.settings.patternId = BreathingPatterns.relax478.id }
                    .font(.subheadline.weight(.semibold))
                    .foregroundStyle(palette.primary.color)
                    .frame(minHeight: 44)
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(.horizontal, 20)
        .padding(.vertical, 14)
        .background(RoundedRectangle(cornerRadius: 20, style: .continuous).fill(palette.secondaryContainer.color))
        .accessibilityElement(children: .combine)
        .padding(.bottom, 4)
    }
}

private struct PatternCard: View {
    let pattern: BreathingPattern
    let selected: Bool
    let onSelect: () -> Void
    @Environment(\.palette) private var palette

    var body: some View {
        Button(action: onSelect) {
            VStack(alignment: .leading, spacing: 2) {
                ViewThatFits(in: .horizontal) {
                    HStack(alignment: .firstTextBaseline) { name; Spacer(minLength: 12); rhythm }
                    VStack(alignment: .leading, spacing: 2) { name; rhythm }
                }
                Text(L10n.patternDescription(pattern))
                    .font(.subheadline)
                    .foregroundStyle(palette.onSurfaceVariant.color)
                Text(L10n.patternDuration(pattern))
                    .font(.footnote)
                    .foregroundStyle(palette.onSurfaceVariant.color)
                    .padding(.top, 2)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(.horizontal, 20)
            .padding(.vertical, 13)
            .background(
                RoundedRectangle(cornerRadius: 20, style: .continuous)
                    .fill((selected ? palette.selectedCard : palette.surfaceLow).color)
            )
            .overlay(
                RoundedRectangle(cornerRadius: 20, style: .continuous)
                    .strokeBorder(selected ? palette.primary.opacity(0.6).color : .clear, lineWidth: 1.5)
            )
            .contentShape(RoundedRectangle(cornerRadius: 20))
        }
        .buttonStyle(.plain)
        .animation(.easeOut(duration: 0.2), value: selected)
        .sensoryFeedback(.selection, trigger: selected) { _, now in now }
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(Text(verbatim: [
            L10n.patternName(pattern), L10n.rhythmForVoiceOver(pattern),
            L10n.patternDescription(pattern), L10n.patternDuration(pattern),
        ].joined(separator: ", ")))
        .accessibilityAddTraits(selected ? [.isButton, .isSelected] : .isButton)
    }

    private var name: some View {
        Text(L10n.patternName(pattern)).font(.headline)
    }

    private var rhythm: some View {
        Text(pattern.rhythmLabel)
            .font(.subheadline.weight(.medium).monospacedDigit())
            // primary on the tinted selected card falls just under 4.5:1 (same as Android).
            .foregroundStyle((selected ? palette.onPrimaryContainer : palette.primary).color)
    }
}
