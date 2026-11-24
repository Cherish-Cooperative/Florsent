// Renders the 展葉之 F app icon (and the shield leaf) from the Android vector drawables
// (res/drawable/ic_launcher_{background,foreground}.xml), so both platforms share one mark.
//   swift ios/tools/render_icons.swift   (run from the repo root)
// Android draws on a 108-unit adaptive canvas whose visible area is the middle 72 units; iOS shows
// the whole square, so we crop to 18...90.
import CoreGraphics
import Foundation
import ImageIO
import UniformTypeIdentifiers

let out = URL(fileURLWithPath: "ios/Florsent/Resources/Assets.xcassets/AppIcon.appiconset")
let shieldOut = URL(fileURLWithPath: "ios/FlorsentShieldConfig/Assets.xcassets/ShieldLeaf.imageset")

func rgb(_ hex: UInt32, _ a: CGFloat = 1) -> CGColor {
    CGColor(srgbRed: CGFloat((hex >> 16) & 0xFF) / 255, green: CGFloat((hex >> 8) & 0xFF) / 255,
            blue: CGFloat(hex & 0xFF) / 255, alpha: a)
}

// Paths in Android viewport units (y down).
func leaf() -> CGPath {
    let p = CGMutablePath()
    p.move(to: CGPoint(x: 38.8, y: 76.0))
    p.addCurve(to: CGPoint(x: 43.2, y: 38.8), control1: CGPoint(x: 34.4, y: 63.2), control2: CGPoint(x: 35.0, y: 47.2))
    p.addCurve(to: CGPoint(x: 72.6, y: 34.4), control1: CGPoint(x: 50.4, y: 31.4), control2: CGPoint(x: 62.0, y: 30.6))
    p.addCurve(to: CGPoint(x: 50.4, y: 46.0), control1: CGPoint(x: 67.4, y: 41.6), control2: CGPoint(x: 58.6, y: 44.4))
    p.addCurve(to: CGPoint(x: 43.4, y: 58.2), control1: CGPoint(x: 45.2, y: 47.0), control2: CGPoint(x: 43.4, y: 50.8))
    p.addCurve(to: CGPoint(x: 38.8, y: 76.0), control1: CGPoint(x: 43.4, y: 65.6), control2: CGPoint(x: 42.0, y: 71.8))
    p.closeSubpath()
    return p
}
func petal() -> CGPath {
    let p = CGMutablePath()
    p.move(to: CGPoint(x: 47.8, y: 61.0))
    p.addCurve(to: CGPoint(x: 67.4, y: 49.0), control1: CGPoint(x: 47.8, y: 53.2), control2: CGPoint(x: 53.8, y: 48.6))
    p.addCurve(to: CGPoint(x: 47.8, y: 61.0), control1: CGPoint(x: 64.2, y: 57.0), control2: CGPoint(x: 57.2, y: 61.8))
    p.closeSubpath()
    return p
}
func dew() -> CGPath { CGPath(ellipseIn: CGRect(x: 54.4, y: 64.6, width: 10.8, height: 10.8), transform: nil) }

enum Variant { case light, dark, tinted, shield }

func render(_ variant: Variant, size: Int, crop: CGRect, to url: URL) {
    let ctx = CGContext(data: nil, width: size, height: size, bitsPerComponent: 8, bytesPerRow: 0,
                        space: CGColorSpace(name: CGColorSpace.sRGB)!,
                        bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue)!
    let s = CGFloat(size) / crop.width
    ctx.translateBy(x: 0, y: CGFloat(size)); ctx.scaleBy(x: s, y: -s) // y down, viewport units
    ctx.translateBy(x: -crop.minX, y: -crop.minY)
    let space = CGColorSpace(name: CGColorSpace.sRGB)!

    if variant == .light {
        // Background: sage linear gradient + soft top-left glow (ic_launcher_background.xml).
        let linear = CGGradient(colorsSpace: space, colors: [rgb(0x58735E), rgb(0x4F6855), rgb(0x455C4B)] as CFArray,
                                locations: [0, 0.52, 1])!
        ctx.drawLinearGradient(linear, start: CGPoint(x: 12.96, y: 8.64), end: CGPoint(x: 95.04, y: 99.36),
                               options: [.drawsBeforeStartLocation, .drawsAfterEndLocation])
        let glow = CGGradient(colorsSpace: space, colors: [rgb(0x708D76, 0x42 / 255.0), rgb(0x455C4B, 0)] as CFArray,
                              locations: [0, 1])!
        ctx.drawRadialGradient(glow, startCenter: CGPoint(x: 37.8, y: 30.24), startRadius: 0,
                               endCenter: CGPoint(x: 37.8, y: 30.24), endRadius: 62.64, options: [])
    }
    let (leafColor, petalColor): (CGColor, CGColor) = switch variant {
    case .light: (rgb(0xF5F4EF), rgb(0xB8CBB9))
    case .dark: (rgb(0xC9E2C6), rgb(0x6F8F72))     // sage glyph; iOS draws the dark backdrop
    case .tinted: (rgb(0xFFFFFF), rgb(0x9A9A9A))   // grayscale; iOS applies the tint
    case .shield: (rgb(0x56705A), rgb(0xA8C3A6))
    }
    for (path, color) in [(leaf(), leafColor), (petal(), petalColor), (dew(), leafColor)] {
        ctx.addPath(path); ctx.setFillColor(color); ctx.fillPath()
    }
    let dest = CGImageDestinationCreateWithURL(url as CFURL, UTType.png.identifier as CFString, 1, nil)!
    CGImageDestinationAddImage(dest, ctx.makeImage()!, nil)
    precondition(CGImageDestinationFinalize(dest))
}

try FileManager.default.createDirectory(at: out, withIntermediateDirectories: true)
try FileManager.default.createDirectory(at: shieldOut, withIntermediateDirectories: true)
let visible = CGRect(x: 18, y: 18, width: 72, height: 72)
render(.light, size: 1024, crop: visible, to: out.appendingPathComponent("AppIcon.png"))
render(.dark, size: 1024, crop: visible, to: out.appendingPathComponent("AppIcon-Dark.png"))
render(.tinted, size: 1024, crop: visible, to: out.appendingPathComponent("AppIcon-Tinted.png"))
let glyph = CGRect(x: 30, y: 29, width: 48, height: 48) // the glyph alone, for the shield
for scale in 1...3 {
    render(.shield, size: 60 * scale, crop: glyph,
           to: shieldOut.appendingPathComponent("ShieldLeaf@\(scale)x.png"))
}
print("icons written")
