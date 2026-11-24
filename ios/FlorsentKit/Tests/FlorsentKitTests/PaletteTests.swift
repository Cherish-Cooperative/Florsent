import XCTest
@testable import FlorsentKit

final class PaletteTests: XCTestCase {
    /// WCAG AA (4.5:1) for every text pair, in light and dark.
    func testAllTextPairsPassWCAGAA() {
        for palette in [FlorsentPalette.light, .dark] {
            for pair in palette.textPairs {
                let ratio = ContrastAudit.ratio(pair.fg, pair.bg)
                XCTAssertGreaterThanOrEqual(ratio, 4.5, "\(palette.isDark ? "dark" : "light") \(pair.name) = \(ratio)")
            }
        }
    }

    func testContrastMath() {
        XCTAssertEqual(ContrastAudit.ratio(RGBA(0x000000), RGBA(0xFFFFFF)), 21, accuracy: 0.01)
        XCTAssertEqual(ContrastAudit.ratio(RGBA(0x777777), RGBA(0xFFFFFF)), 4.48, accuracy: 0.01)
    }

    func testFlowerColorsFollowAndroid() {
        let light = FlowerColors(.light)
        XCTAssertEqual(light.core.a, 0.95)
        XCTAssertEqual(light.ring, FlorsentPalette.light.primary.opacity(0.8))
        let dark = FlowerColors(.dark)
        XCTAssertEqual(dark.outerCenter, FlorsentPalette.dark.primaryContainer.opacity(0.75))
    }

    func testPetalPathPointsUp() {
        let bounds = BreathingFlower.petalPath(length: 100, width: 40).boundingRect
        XCTAssertEqual(bounds.minY, -100, accuracy: 0.5)
        XCTAssertEqual(bounds.maxY, 0, accuracy: 0.5)
        XCTAssertEqual(bounds.midX, 0, accuracy: 0.5)
    }
}
