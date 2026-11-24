import SwiftUI

/// Colors of the flower, derived from the palette exactly as Android's `flowerColors()`.
public struct FlowerColors: Sendable, Hashable {
    public let halo: RGBA
    public let outerCenter: RGBA
    public let outerEdge: RGBA
    public let innerCenter: RGBA
    public let innerEdge: RGBA
    public let core: RGBA
    public let ringTrack: RGBA
    public let ring: RGBA

    public init(_ p: FlorsentPalette) {
        let sageMid = p.primaryContainer.mix(p.primary, 0.35)
        halo = p.primary
        ring = p.primary.opacity(0.80)
        if p.isDark {
            outerCenter = p.primaryContainer.opacity(0.75)
            outerEdge = p.secondaryContainer.opacity(0.40)
            innerCenter = p.primary.opacity(0.60)
            innerEdge = p.primaryContainer.opacity(0.50)
            core = p.primary.opacity(0.70)
            ringTrack = p.outlineVariant.opacity(0.60)
        } else {
            outerCenter = sageMid.opacity(0.62)
            outerEdge = p.primaryContainer.opacity(0.55)
            innerCenter = p.primary.opacity(0.55)
            innerEdge = sageMid.opacity(0.45)
            core = p.surfaceLowest.opacity(0.95)
            ringTrack = p.outlineVariant.opacity(0.70)
        }
    }
}

/// Organic two-ring flower with six petals per ring and a soft glow, drawn in a single `Canvas`.
///
/// Pure function of its inputs, so a `TimelineView` above it decides the frame rate (and can pause it
/// for Reduce Motion or while holding the breath). Per frame it only applies transforms to two cached
/// petal shapes: no blur, no offscreen passes; the glow is a radial gradient.
///
/// - Parameters:
///   - expansion: 0 = closed bud, 1 = fully open.
///   - time: seconds driving the slow drift and petal wobble (breathing time only, so holds are still).
///   - progress: 0...1 session ring, or nil to hide it.
public struct BreathingFlower: View {
    public var expansion: Float
    public var time: Double
    public var progress: Float?

    @Environment(\.palette) private var palette

    public init(expansion: Float, time: Double = 0, progress: Float? = nil) {
        self.expansion = expansion
        self.time = time
        self.progress = progress
    }

    public var body: some View {
        let colors = FlowerColors(palette)
        Canvas(rendersAsynchronously: false) { context, size in
            Self.draw(in: &context, size: size, colors: colors,
                      expansion: CGFloat(min(max(expansion, 0), 1)), time: time, progress: progress)
        }
        .accessibilityHidden(true)
    }

    static let petals = 6

    static func draw(
        in context: inout GraphicsContext, size: CGSize, colors: FlowerColors,
        expansion e: CGFloat, time t: Double, progress: Float?
    ) {
        let r = min(size.width, size.height) / 2
        context.translateBy(x: size.width / 2, y: size.height / 2) // origin = flower center

        // Halo
        var halo = context
        halo.scaleBy(x: 0.68 + 0.32 * e, y: 0.68 + 0.32 * e)
        halo.fill(
            Path(ellipseIn: CGRect(x: -r, y: -r, width: 2 * r, height: 2 * r)),
            with: .radialGradient(
                Gradient(stops: [
                    .init(color: colors.halo.opacity(0.26).color, location: 0),
                    .init(color: colors.halo.opacity(0.10).color, location: 0.5),
                    .init(color: colors.halo.opacity(0).color, location: 1),
                ]),
                center: .zero, startRadius: 0, endRadius: r
            )
        )

        // Progress ring
        if let p = progress, p >= 0 {
            let ringRadius = r * 0.97
            let style = StrokeStyle(lineWidth: 1.5, lineCap: .round)
            context.stroke(
                Path(ellipseIn: CGRect(x: -ringRadius, y: -ringRadius, width: 2 * ringRadius, height: 2 * ringRadius)),
                with: .color(colors.ringTrack.color), style: style
            )
            if p > 0 {
                var arc = Path()
                arc.addArc(center: .zero, radius: ringRadius, startAngle: .degrees(-90),
                           endAngle: .degrees(-90 + 360 * Double(min(p, 1))), clockwise: false)
                context.stroke(arc, with: .color(colors.ring.color), style: style)
            }
        }

        let drift = t * 3.5
        let outer = petalPath(length: r * 0.80, width: r * 0.36)
        let outerShading = GraphicsContext.Shading.radialGradient(
            Gradient(colors: [colors.outerCenter.color, colors.outerEdge.color]),
            center: .zero, startRadius: 0, endRadius: r * 0.82
        )
        let outerLength = 0.52 + 0.48 * e
        let outerWidth = 0.78 + 0.27 * e
        for i in 0..<petals {
            let wobble = sin(t * 0.7 + Double(i) * 1.7)
            let angle = Double(i) * (360.0 / Double(petals)) + drift + wobble * 2.5
            var petal = context
            petal.rotate(by: .degrees(angle))
            petal.scaleBy(x: outerWidth * (1 + 0.05 * wobble), y: outerLength)
            petal.fill(outer, with: outerShading)
        }

        let inner = petalPath(length: r * 0.54, width: r * 0.25)
        let innerShading = GraphicsContext.Shading.radialGradient(
            Gradient(colors: [colors.innerCenter.color, colors.innerEdge.color]),
            center: .zero, startRadius: 0, endRadius: r * 0.56
        )
        let innerLength = 0.60 + 0.40 * e
        let innerWidth = 0.84 + 0.20 * e
        for i in 0..<petals {
            let wobble = sin(t * 0.9 + Double(i) * 2.3 + 1)
            let angle = Double(i) * (360.0 / Double(petals)) + 180.0 / Double(petals) - drift * 0.6 + wobble * 2
            var petal = context
            petal.rotate(by: .degrees(angle))
            petal.scaleBy(x: innerWidth * (1 + 0.06 * wobble), y: innerLength)
            petal.fill(inner, with: innerShading)
        }

        // Core
        var core = context
        core.scaleBy(x: 0.85 + 0.30 * e, y: 0.85 + 0.30 * e)
        let coreRadius = r * 0.24
        core.fill(
            Path(ellipseIn: CGRect(x: -coreRadius, y: -coreRadius, width: 2 * coreRadius, height: 2 * coreRadius)),
            with: .radialGradient(
                Gradient(stops: [
                    .init(color: colors.core.color, location: 0),
                    .init(color: colors.core.opacity(colors.core.a * 0.4).color, location: 0.6),
                    .init(color: colors.core.opacity(0).color, location: 1),
                ]),
                center: .zero, startRadius: 0, endRadius: coreRadius
            )
        )
    }

    /// Teardrop petal from the origin pointing up; transforms place and size it.
    static func petalPath(length: CGFloat, width: CGFloat) -> Path {
        var path = Path()
        path.move(to: .zero)
        path.addCurve(to: CGPoint(x: 0, y: -length),
                      control1: CGPoint(x: -width, y: -length * 0.22),
                      control2: CGPoint(x: -width * 0.78, y: -length * 0.86))
        path.addCurve(to: .zero,
                      control1: CGPoint(x: width * 0.78, y: -length * 0.86),
                      control2: CGPoint(x: width, y: -length * 0.22))
        path.closeSubpath()
        return path
    }
}

/// The idle "resting breath" flower for pickers and the home screen: about 30 fps, still under
/// Reduce Motion.
public struct IdleBreathingFlower: View {
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    private let start = Date()

    public init() {}

    public var body: some View {
        TimelineView(.animation(minimumInterval: 1.0 / 30, paused: reduceMotion)) { context in
            let ms = Int64(max(context.date.timeIntervalSince(start), 0) * 1000)
            BreathingFlower(
                expansion: reduceMotion ? 0.45 : BreathingTimeline.idleExpansion(at: ms),
                time: reduceMotion ? 0 : Double(ms) / 1000
            )
        }
    }
}
