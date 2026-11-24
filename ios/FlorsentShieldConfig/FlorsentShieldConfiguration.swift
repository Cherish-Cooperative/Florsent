import FlorsentCore
import ManagedSettings
import ManagedSettingsUI
import UIKit

/// The soft sage shield (stage two), static and calm. It asks rather than blocks: "Time for a break"
/// (or "Time to wind down?" in the bedtime window), with "Go breathe" and a plain "5 more minutes".
final class FlorsentShieldConfiguration: ShieldConfigurationDataSource {
    override func configuration(shielding application: Application) -> ShieldConfiguration { gentle() }

    override func configuration(shielding application: Application, in category: ActivityCategory) -> ShieldConfiguration {
        gentle()
    }

    override func configuration(shielding webDomain: WebDomain) -> ShieldConfiguration { gentle() }

    override func configuration(shielding webDomain: WebDomain, in category: ActivityCategory) -> ShieldConfiguration {
        gentle()
    }

    private func gentle() -> ShieldConfiguration {
        let bedtime = SharedStore().guidanceState.context == .bedtime
        let title = bedtime ? String(localized: "shield.bedtime.title") : String(localized: "shield.daily.title")
        let subtitle = bedtime ? String(localized: "shield.bedtime.subtitle") : String(localized: "shield.daily.subtitle")
        return ShieldConfiguration(
            backgroundBlurStyle: .systemUltraThinMaterial,
            backgroundColor: Self.dynamic(light: 0xF5F3EE, dark: 0x141613).withAlphaComponent(0.88),
            icon: UIImage(named: "ShieldLeaf"),
            title: .init(text: title, color: Self.dynamic(light: 0x23251F, dark: 0xE4E3DC)),
            subtitle: .init(text: subtitle, color: Self.dynamic(light: 0x5B5E56, dark: 0xC1C3BA)),
            primaryButtonLabel: .init(text: String(localized: "shield.primary"), color: Self.dynamic(light: 0xFFFFFF, dark: 0x193320)),
            primaryButtonBackgroundColor: Self.dynamic(light: 0x56705A, dark: 0xA8C3A6),
            secondaryButtonLabel: .init(text: String(localized: "shield.secondary"), color: Self.dynamic(light: 0x56705A, dark: 0xA8C3A6))
        )
    }

    /// Florsent palette (same values as FlorsentKit, without linking SwiftUI into the extension).
    static func dynamic(light: UInt32, dark: UInt32) -> UIColor {
        UIColor { traits in
            let hex = traits.userInterfaceStyle == .dark ? dark : light
            return UIColor(red: CGFloat((hex >> 16) & 0xFF) / 255, green: CGFloat((hex >> 8) & 0xFF) / 255,
                           blue: CGFloat(hex & 0xFF) / 255, alpha: 1)
        }
    }
}
