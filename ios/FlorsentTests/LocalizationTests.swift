import XCTest
import FlorsentKit
@testable import Florsent

/// The String Catalog must carry the Android copy verbatim (golden file) in both languages, and the
/// compiled .lproj tables must resolve it at runtime.
final class LocalizationTests: XCTestCase {
    private let iosRoot = URL(fileURLWithPath: #filePath).deletingLastPathComponent().deletingLastPathComponent()
    private var golden: [String: Any]!
    private var catalog: [String: [String: Any]]!

    override func setUpWithError() throws {
        let goldenURL = iosRoot.deletingLastPathComponent().appendingPathComponent("shared/test-fixtures/breathing_golden.json")
        // The fixtures live in the repo on the Mac: reachable from the simulator, not from a device.
        try XCTSkipUnless(FileManager.default.fileExists(atPath: goldenURL.path), "repo fixtures not reachable on a device")
        golden = try JSONSerialization.jsonObject(with: Data(contentsOf: goldenURL)) as? [String: Any]
        let catalogURL = iosRoot.appendingPathComponent("Florsent/Resources/Localizable.xcstrings")
        let json = try JSONSerialization.jsonObject(with: Data(contentsOf: catalogURL)) as? [String: Any]
        catalog = json?["strings"] as? [String: [String: Any]]
        XCTAssertNotNil(golden)
        XCTAssertNotNil(catalog)
    }

    private func value(_ key: String, _ lang: String) -> String? {
        let loc = (catalog[key]?["localizations"] as? [String: Any])?[lang] as? [String: Any]
        return (loc?["stringUnit"] as? [String: Any])?["value"] as? String
    }

    private func androidCopy(_ lang: String) -> (strings: [String: String], arrays: [String: [String]]) {
        let copy = (golden["copy"] as! [String: Any])[lang] as! [String: Any]
        return (copy["strings"] as! [String: String], copy["arrays"] as! [String: [String]])
    }

    /// Android "%1$d" / "%s" become "%1$lld" / "%@" on iOS.
    private func iosFormat(_ s: String) -> String {
        var out = s
        for (pattern, template) in [(#"%(\d+\$)?d"#, "%$1lld"), (#"%(\d+\$)?s"#, "%$1@")] {
            out = out.replacingOccurrences(of: pattern, with: template, options: .regularExpression)
        }
        return out
    }

    func testAndroidCopyIsVerbatimInBothLanguages() {
        var checked = 0
        for (key, entry) in catalog {
            guard let comment = entry["comment"] as? String, comment.hasPrefix("Android: ") else { continue }
            let source = String(comment.dropFirst("Android: ".count))
            for lang in ["en", "zh-Hant"] {
                let android = androidCopy(lang)
                let expected: String?
                if let bracket = source.firstIndex(of: "[") {
                    let name = String(source[..<bracket])
                    let index = Int(source[source.index(after: bracket)..<source.index(before: source.endIndex)])!
                    expected = android.arrays[name]?[index]
                } else {
                    expected = android.strings[source].map(iosFormat)
                }
                XCTAssertNotNil(expected, "\(key) → \(source)")
                XCTAssertEqual(value(key, lang), expected, "\(key) [\(lang)]")
                checked += 1
            }
        }
        XCTAssertGreaterThan(checked, 100)
    }

    func testEveryStringHasBothLanguages() {
        for (key, entry) in catalog {
            let locs = entry["localizations"] as? [String: Any] ?? [:]
            XCTAssertNotNil(locs["en"], "\(key) en")
            XCTAssertNotNil(locs["zh-Hant"], "\(key) zh-Hant")
        }
    }

    func testAllTwentyGroundingPromptsResolveAtRuntime() throws {
        for (lang, folder) in [("en", "en"), ("zh-Hant", "zh-Hant")] {
            let path = try XCTUnwrap(Bundle.main.path(forResource: folder, ofType: "lproj"), "\(folder).lproj missing")
            let bundle = try XCTUnwrap(Bundle(path: path))
            let android = androidCopy(lang).arrays
            for night in [false, true] {
                let prompts = android[night ? "grounding_prompts_bedtime" : "grounding_prompts"]!
                XCTAssertEqual(prompts.count, night ? GroundingPrompts.nightCount : GroundingPrompts.dayCount)
                for (i, prompt) in prompts.enumerated() {
                    let key = GroundingPrompts.key(night: night, index: i)
                    XCTAssertEqual(bundle.localizedString(forKey: key, value: "MISSING", table: nil), prompt, "\(lang) \(key)")
                }
            }
        }
    }

    func testKeysBuiltAtRuntimeExist() {
        var keys = BreathPhaseKind.allCases.map { "phase.\($0.rawValue)" }
        for p in BreathingPatterns.all { keys += ["pattern.\(p.id).name", "pattern.\(p.id).desc"] }
        for key in keys { XCTAssertNotNil(value(key, "en"), key) }
    }
}
