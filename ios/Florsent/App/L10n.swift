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

    /// "45 min", "1 h", "1 h 30 min"
    static func duration(minutes: Int) -> String {
        let h = minutes / 60, m = minutes % 60
        if h == 0 { return format("duration.minutes", m) }
        return m == 0 ? format("duration.hours", h) : format("duration.hoursMinutes", h, m)
    }

    /// A wall-clock time in the person's locale and 12/24-hour setting ("23:00", "11:00 PM").
    static func clock(hour: Int, minute: Int, calendar: Calendar = .current) -> String {
        let date = calendar.date(bySettingHour: hour, minute: minute, second: 0, of: Date()) ?? Date()
        return date.formatted(date: .omitted, time: .shortened)
    }
}
