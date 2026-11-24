import FlorsentKit
import Foundation

/// Lookups for keys built at runtime and for format strings (Localizable.xcstrings).
enum L10n {
    static func string(_ key: String) -> String {
        NSLocalizedString(key, comment: "")
    }

    /// Plural-aware (variations in the catalog compile to a stringsdict).
    static func format(_ key: String, _ args: CVarArg...) -> String {
        String.localizedStringWithFormat(NSLocalizedString(key, comment: ""), args)
    }

    static func phase(_ kind: BreathPhaseKind) -> String { string("phase.\(kind.rawValue)") }
    static func patternName(_ p: BreathingPattern) -> String { string("pattern.\(p.id).name") }
    static func patternDescription(_ p: BreathingPattern) -> String { string("pattern.\(p.id).desc") }

    /// "6 rounds · about 1 min 12 s"
    static func patternDuration(_ p: BreathingPattern) -> String {
        let minutes = p.totalSeconds / 60, seconds = p.totalSeconds % 60
        return seconds == 0
            ? format("pattern.durationEven", p.cycles, minutes)
            : format("pattern.duration", p.cycles, minutes, seconds)
    }

    /// VoiceOver: "Rhythm 4, 7, 8 seconds" instead of reading the dots.
    static func rhythmForVoiceOver(_ p: BreathingPattern) -> String {
        format("breathe.rhythm.a11y", p.rhythmLabel.replacingOccurrences(of: " · ", with: ", "))
    }

    static func greeting(hour: Int) -> String {
        switch hour {
        case 5..<12: string("greeting.morning")
        case 12..<18: string("greeting.afternoon")
        case 18..<23: string("greeting.evening")
        default: string("greeting.night")
        }
    }
}
