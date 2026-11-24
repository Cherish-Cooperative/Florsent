import SwiftUI

/// sRGB color with exact component values, so contrast can be audited in tests.
public struct RGBA: Sendable, Hashable {
    public let r: Double, g: Double, b: Double, a: Double

    public init(_ hex: UInt32, alpha: Double = 1) {
        r = Double((hex >> 16) & 0xFF) / 255
        g = Double((hex >> 8) & 0xFF) / 255
        b = Double(hex & 0xFF) / 255
        a = alpha
    }

    init(r: Double, g: Double, b: Double, a: Double) {
        self.r = r; self.g = g; self.b = b; self.a = a
    }

    public func opacity(_ alpha: Double) -> RGBA { RGBA(r: r, g: g, b: b, a: alpha) }

    /// Linear interpolation in sRGB, like Compose's `lerp(Color, Color, Float)` for opaque colors.
    public func mix(_ other: RGBA, _ t: Double) -> RGBA {
        RGBA(r: r + (other.r - r) * t, g: g + (other.g - g) * t, b: b + (other.b - b) * t, a: a + (other.a - a) * t)
    }

    /// Source-over composite onto an opaque background.
    public func over(_ base: RGBA) -> RGBA {
        RGBA(r: r * a + base.r * (1 - a), g: g * a + base.g * (1 - a), b: b * a + base.b * (1 - a), a: 1)
    }

    public var color: Color { Color(.sRGB, red: r, green: g, blue: b, opacity: a) }
}

/// Florsent's palette: sage green (primary), morning-mist gray (secondary), warm stone (neutral).
/// The same values as the Android app's `ui/theme/Color.kt`, so both apps look like one product.
public struct FlorsentPalette: Sendable, Hashable {
    public let isDark: Bool
    public let primary: RGBA
    public let onPrimary: RGBA
    public let primaryContainer: RGBA
    public let onPrimaryContainer: RGBA
    public let secondaryContainer: RGBA
    public let onSecondaryContainer: RGBA
    public let background: RGBA
    public let surfaceLowest: RGBA
    public let surfaceLow: RGBA
    public let surfaceHigh: RGBA
    public let onSurface: RGBA
    public let onSurfaceVariant: RGBA
    public let outline: RGBA
    public let outlineVariant: RGBA

    public static let light = FlorsentPalette(
        isDark: false,
        primary: RGBA(0x56705A), onPrimary: RGBA(0xFFFFFF),
        primaryContainer: RGBA(0xD8E4D5), onPrimaryContainer: RGBA(0x17281A),
        secondaryContainer: RGBA(0xDDE3E1), onSecondaryContainer: RGBA(0x1C2422),
        background: RGBA(0xF5F3EE), surfaceLowest: RGBA(0xFBFAF7), surfaceLow: RGBA(0xF0EEE8),
        surfaceHigh: RGBA(0xE5E2DA),
        onSurface: RGBA(0x23251F), onSurfaceVariant: RGBA(0x5B5E56),
        outline: RGBA(0x8E9087), outlineVariant: RGBA(0xD3D0C6)
    )

    public static let dark = FlorsentPalette(
        isDark: true,
        primary: RGBA(0xA8C3A6), onPrimary: RGBA(0x193320),
        primaryContainer: RGBA(0x304836), onPrimaryContainer: RGBA(0xC9E2C6),
        secondaryContainer: RGBA(0x3A4341), onSecondaryContainer: RGBA(0xD7E0DD),
        background: RGBA(0x141613), surfaceLowest: RGBA(0x0F110E), surfaceLow: RGBA(0x1A1C19),
        surfaceHigh: RGBA(0x282B27),
        onSurface: RGBA(0xE4E3DC), onSurfaceVariant: RGBA(0xC1C3BA),
        outline: RGBA(0x8B8E85), outlineVariant: RGBA(0x43463F)
    )

    public static func resolve(_ scheme: ColorScheme) -> FlorsentPalette { scheme == .dark ? dark : light }

    /// The selected pattern card: primaryContainer at 55 % over the card surface.
    public var selectedCard: RGBA { primaryContainer.opacity(0.55).over(surfaceLow) }

    /// The text/background pairs Florsent draws; every one must reach WCAG AA (4.5:1).
    public var textPairs: [(name: String, fg: RGBA, bg: RGBA)] {
        [
            ("onSurface/background", onSurface, background),
            ("onSurfaceVariant/background", onSurfaceVariant, background),
            ("onSurface/surfaceLow", onSurface, surfaceLow),
            ("onSurfaceVariant/surfaceLow", onSurfaceVariant, surfaceLow),
            ("primary/background", primary, background),
            ("primary/surfaceLow", primary, surfaceLow),
            ("onPrimary/primary", onPrimary, primary),
            ("onSecondaryContainer/secondaryContainer", onSecondaryContainer, secondaryContainer),
            ("onPrimaryContainer/selectedCard", onPrimaryContainer, selectedCard),
            ("onSurfaceVariant/selectedCard", onSurfaceVariant, selectedCard),
        ]
    }
}

public enum ContrastAudit {
    /// WCAG 2.x relative luminance.
    public static func luminance(_ c: RGBA) -> Double {
        func channel(_ v: Double) -> Double { v <= 0.04045 ? v / 12.92 : pow((v + 0.055) / 1.055, 2.4) }
        return 0.2126 * channel(c.r) + 0.7152 * channel(c.g) + 0.0722 * channel(c.b)
    }

    public static func ratio(_ fg: RGBA, _ bg: RGBA) -> Double {
        let a = luminance(fg.a < 1 ? fg.over(bg) : fg), b = luminance(bg)
        return (max(a, b) + 0.05) / (min(a, b) + 0.05)
    }
}

// MARK: - Environment

private struct PaletteKey: EnvironmentKey {
    static let defaultValue = FlorsentPalette.light
}

public extension EnvironmentValues {
    var palette: FlorsentPalette {
        get { self[PaletteKey.self] }
        set { self[PaletteKey.self] = newValue }
    }
}

private struct FlorsentThemeModifier: ViewModifier {
    @Environment(\.colorScheme) private var scheme

    func body(content: Content) -> some View {
        let palette = FlorsentPalette.resolve(scheme)
        content
            .environment(\.palette, palette)
            .tint(palette.primary.color)
    }
}

public extension View {
    /// Injects the light or dark palette that matches the current color scheme.
    func florsentTheme() -> some View { modifier(FlorsentThemeModifier()) }
}
