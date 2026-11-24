import FlorsentKit
import SwiftUI

/// Full-width primary action: sage capsule, at least 56 pt tall, grows with Dynamic Type.
struct PrimaryButtonStyle: ButtonStyle {
    @Environment(\.palette) private var palette

    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .font(.headline)
            .multilineTextAlignment(.center)
            .foregroundStyle(palette.onPrimary.color)
            .frame(maxWidth: .infinity, minHeight: 56)
            .padding(.horizontal, 20)
            .background(Capsule().fill(palette.primary.color))
            .opacity(configuration.isPressed ? 0.82 : 1)
            .scaleEffect(configuration.isPressed ? 0.985 : 1)
            .animation(.easeOut(duration: 0.15), value: configuration.isPressed)
            .contentShape(Capsule())
    }
}

/// Quiet text action ("End early", "Another one").
struct QuietButtonStyle: ButtonStyle {
    @Environment(\.palette) private var palette

    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .font(.body)
            .foregroundStyle(palette.onSurfaceVariant.color)
            .padding(.horizontal, 16)
            .frame(minHeight: 44)
            .contentShape(Rectangle())
            .opacity(configuration.isPressed ? 0.6 : 1)
    }
}

/// On/off chip (Ambience, Chime), mirrors the Android PillChip.
struct ToggleChip: View {
    let title: LocalizedStringKey
    @Binding var isOn: Bool
    @Environment(\.palette) private var palette

    var body: some View {
        Button {
            isOn.toggle()
        } label: {
            HStack(spacing: 6) {
                if isOn {
                    Image(systemName: "checkmark").font(.footnote.weight(.semibold))
                }
                Text(title).font(.subheadline.weight(.medium))
            }
            .foregroundStyle((isOn ? palette.onPrimaryContainer : palette.onSurfaceVariant).color)
            .padding(.horizontal, 16)
            .frame(minHeight: 40)
            .background(
                Capsule().fill(isOn ? palette.primaryContainer.color : .clear)
            )
            .overlay(Capsule().strokeBorder(isOn ? .clear : palette.outlineVariant.color, lineWidth: 1))
            .contentShape(Capsule())
        }
        .buttonStyle(.plain)
        .accessibilityAddTraits(.isToggle)
        .accessibilityValue(isOn ? Text(verbatim: "1") : Text(verbatim: "0"))
        .sensoryFeedback(.selection, trigger: isOn)
    }
}

/// Rounded card on the low surface.
struct Card<Content: View>: View {
    @Environment(\.palette) private var palette
    @ViewBuilder var content: Content

    var body: some View {
        VStack(alignment: .leading, spacing: 6) { content }
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(.horizontal, 20)
            .padding(.vertical, 16)
            .background(RoundedRectangle(cornerRadius: 20, style: .continuous).fill(palette.surfaceLow.color))
    }
}

extension View {
    /// The calm page background used by every screen.
    func florsentBackground() -> some View {
        modifier(BackgroundModifier())
    }
}

private struct BackgroundModifier: ViewModifier {
    @Environment(\.palette) private var palette

    func body(content: Content) -> some View {
        content
            .foregroundStyle(palette.onSurface.color)
            .background(palette.background.color.ignoresSafeArea())
            .toolbarBackground(palette.background.color, for: .navigationBar)
    }
}
