/// "Back to the present" prompts. The copy lives in the app's Localizable.xcstrings under
/// `grounding.day.N` / `grounding.night.N` (1-based), taken verbatim from Android's string arrays.
public enum GroundingPrompts {
    public static let dayCount = 12
    public static let nightCount = 8

    public static func key(night: Bool, index: Int) -> String {
        "grounding.\(night ? "night" : "day").\(index + 1)"
    }

    /// Bedtime prompts in the late evening and at night, when "go rest" is the kinder way out.
    public static func isNight(hour: Int) -> Bool { hour >= 21 || hour < 5 }

    /// A different prompt from [current], uniformly among the others.
    public static func nextIndex(after current: Int, count: Int, random: Int) -> Int {
        guard count > 1 else { return current }
        let next = ((random % (count - 1)) + (count - 1)) % (count - 1)
        return next >= current ? next + 1 : next
    }
}
