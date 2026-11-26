package app.florsent.ui.breathe

import android.provider.Settings
import androidx.compose.foundation.layout.Spacer
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlin.math.pow
import kotlin.math.sin

@Immutable
private data class FlowerColors(
    val halo: Color,
    val outerCenter: Color,
    val outerEdge: Color,
    val innerCenter: Color,
    val innerEdge: Color,
    val core: Color,
    val ringTrack: Color,
    val ring: Color,
)

private fun ColorScheme.flowerColors(): FlowerColors {
    val dark = background.luminance() < 0.5f
    val sageMid = lerp(primaryContainer, primary, 0.35f)
    return if (dark) {
        FlowerColors(
            halo = primary,
            outerCenter = primaryContainer.copy(alpha = 0.75f),
            outerEdge = secondaryContainer.copy(alpha = 0.40f),
            innerCenter = primary.copy(alpha = 0.60f),
            innerEdge = primaryContainer.copy(alpha = 0.50f),
            core = primary.copy(alpha = 0.70f),
            ringTrack = outlineVariant.copy(alpha = 0.60f),
            ring = primary.copy(alpha = 0.80f),
        )
    } else {
        FlowerColors(
            halo = primary,
            outerCenter = sageMid.copy(alpha = 0.62f),
            outerEdge = primaryContainer.copy(alpha = 0.55f),
            innerCenter = primary.copy(alpha = 0.55f),
            innerEdge = sageMid.copy(alpha = 0.45f),
            core = surfaceContainerLowest.copy(alpha = 0.95f),
            ringTrack = outlineVariant.copy(alpha = 0.70f),
            ring = primary.copy(alpha = 0.80f),
        )
    }
}

/**
 * One ring of petals; rings open from the outside in and close from the inside out. Every ring moves
 * from the first frame of a breath, at its own pace ([pace] is an exponent on the expansion).
 */
private class PetalRing(
    val count: Int,
    /** Petal length and width as fractions of the flower radius, fully open. */
    val length: Float,
    val width: Float,
    /** Below 1 the ring leads (opens early, closes late), above 1 it follows. */
    val pace: Float,
    /** Angle offset so the rings interleave. */
    val offsetDeg: Float,
    /** Bud twist (degrees, signed) when closed, so a closed ring spirals like a bud. */
    val budTwistDeg: Float,
    /** How narrow a closed petal looks (it curls inward), 1 = no change. */
    val budWidth: Float,
    val driftFactor: Float,
    val wobblePhase: Float,
)

private val RINGS = arrayOf(
    PetalRing(count = 8, length = 0.84f, width = 0.34f, pace = 0.75f, offsetDeg = 0f, budTwistDeg = 12f, budWidth = 0.78f, driftFactor = 1f, wobblePhase = 0f),
    PetalRing(count = 8, length = 0.64f, width = 0.28f, pace = 1.15f, offsetDeg = 22.5f, budTwistDeg = -16f, budWidth = 0.74f, driftFactor = -0.6f, wobblePhase = 1f),
    PetalRing(count = 5, length = 0.42f, width = 0.22f, pace = 1.6f, offsetDeg = 36f, budTwistDeg = 20f, budWidth = 0.72f, driftFactor = 0.4f, wobblePhase = 2f),
)

/**
 * Petal length when closed, as a fraction of fully open. A bud squeezed to a third of its size
 * became a dense octagon with no petals left to see, so breathed out the flower stays a small,
 * soft bloom.
 */
private const val BUD_LENGTH = 0.58f

/**
 * A flower that opens and closes with the breath, drawn entirely in the draw phase.
 *
 * Each petal is a flat teardrop seen from above. Opening lays it down from a shorter, narrower,
 * slightly twisted bud petal to its full length. All rings move from the first frame of a breath,
 * the outer ring leading and the inner ones following, so breathing in unfurls the flower from the
 * outside in, and breathing out folds it back from the inside out, the way a real bloom moves. The
 * underside of a standing petal is a shade deeper, which reads as a petal turning towards the light.
 *
 * Performance contract: [expansion], [timeSeconds] and [progress] are lambdas read only inside
 * `onDrawBehind`, so a changing animation clock invalidates drawing but never recomposition. Paths,
 * gradients and the stroke are built once per size/color change in `drawWithCache`; per frame we
 * only apply transforms, with no allocation and no blur (the glow is a pre-built radial gradient).
 *
 * @param expansion 0 = closed bud, 1 = fully open.
 * @param timeSeconds monotonic seconds driving the slow turning of the petals (never their size).
 * @param progress 0..1 session progress ring, or a negative value to hide the ring.
 */
@Composable
fun BreathingFlower(
    expansion: () -> Float,
    timeSeconds: () -> Float,
    modifier: Modifier = Modifier,
    progress: () -> Float = { -1f },
) {
    val scheme = MaterialTheme.colorScheme
    val colors = remember(scheme) { scheme.flowerColors() }

    Spacer(
        modifier.drawWithCache {
            val r = size.minDimension / 2f
            val c = Offset(size.width / 2f, size.height / 2f)
            val petals = Array(RINGS.size) { k -> petalPath(c, length = r * RINGS[k].length, width = r * RINGS[k].width) }
            val halo = Brush.radialGradient(
                0f to colors.halo.copy(alpha = 0.26f),
                0.5f to colors.halo.copy(alpha = 0.10f),
                1f to Color.Transparent,
                center = c,
                radius = r,
            )
            // Face of the petal (open) and its underside (standing up): one brush per ring each.
            val faces = arrayOf(
                Brush.radialGradient(0f to colors.outerCenter, 1f to colors.outerEdge, center = c, radius = r * 0.86f),
                Brush.radialGradient(0f to colors.innerCenter, 1f to colors.outerCenter, center = c, radius = r * 0.66f),
                Brush.radialGradient(0f to colors.innerCenter, 1f to colors.innerEdge, center = c, radius = r * 0.44f),
            )
            val undersides = arrayOf(
                Brush.radialGradient(0f to colors.innerCenter, 1f to colors.outerCenter, center = c, radius = r * 0.5f),
                Brush.radialGradient(0f to colors.innerCenter, 1f to colors.innerCenter, center = c, radius = r * 0.4f),
                Brush.radialGradient(0f to colors.innerCenter, 1f to colors.innerCenter, center = c, radius = r * 0.3f),
            )
            val coreBrush = Brush.radialGradient(
                0f to colors.core, 0.6f to colors.core.copy(alpha = colors.core.alpha * 0.4f),
                1f to Color.Transparent, center = c, radius = r * 0.24f,
            )
            val ringStroke = Stroke(width = 1.5.dp.toPx(), cap = StrokeCap.Round)
            val ringRadius = r * 0.97f
            val ringTopLeft = Offset(c.x - ringRadius, c.y - ringRadius)
            val ringSize = Size(ringRadius * 2f, ringRadius * 2f)

            onDrawBehind {
                val e = expansion().coerceIn(0f, 1f)
                val t = timeSeconds()

                scale(0.72f + 0.28f * e, c) { drawCircle(halo, radius = r, center = c) }

                val p = progress()
                if (p >= 0f) {
                    drawCircle(colors.ringTrack, radius = ringRadius, center = c, style = ringStroke)
                    if (p > 0f) {
                        drawArc(
                            colors.ring, startAngle = -90f, sweepAngle = 360f * p, useCenter = false,
                            topLeft = ringTopLeft, size = ringSize, style = ringStroke,
                        )
                    }
                }

                val drift = t * 2.5f
                for (k in RINGS.indices) {
                    val ring = RINGS[k]
                    val open = ringOpenness(e, ring.pace)
                    // Linear rather than sin(tilt): the projection barely changes near fully open,
                    // which made the start of every exhale look late.
                    val lengthScale = BUD_LENGTH + (1f - BUD_LENGTH) * open
                    val widthScale = ring.budWidth + (1f - ring.budWidth) * open
                    val twist = ring.budTwistDeg * (1f - open)
                    val step = 360f / ring.count
                    for (i in 0 until ring.count) {
                        val wobble = sin(t * 0.8f + i * 1.9f + ring.wobblePhase)
                        val angle = ring.offsetDeg + i * step + drift * ring.driftFactor + twist + wobble * 2f
                        // Time only turns the petals; their size follows the breath alone, so a hold
                        // keeps the flower drifting without growing or shrinking.
                        withTransform({
                            rotate(angle, c)
                            scale(widthScale, lengthScale, c)
                        }) {
                            drawPath(petals[k], undersides[k], alpha = 0.45f * (1f - open))
                            drawPath(petals[k], faces[k], alpha = 0.60f + 0.40f * open)
                        }
                    }
                }

                // The heart of the flower shows as the inner petals part.
                val heart = ringOpenness(e, RINGS.last().pace)
                scale(0.70f + 0.40f * heart, c) { drawCircle(coreBrush, radius = r * 0.24f, center = c) }
            }
        },
    )
}

/** 0..1 openness of a ring; no dead zone, so it moves as soon as the breath does. */
private fun ringOpenness(expansion: Float, pace: Float): Float = expansion.pow(pace)

/** Teardrop petal from the center pointing up; transforms place and size it. */
private fun petalPath(center: Offset, length: Float, width: Float): Path = Path().apply {
    val cx = center.x
    val cy = center.y
    moveTo(cx, cy)
    cubicTo(cx - width, cy - length * 0.22f, cx - width * 0.78f, cy - length * 0.86f, cx, cy - length)
    cubicTo(cx + width * 0.78f, cy - length * 0.86f, cx + width, cy - length * 0.22f, cx, cy)
    close()
}

/** True when the user turned off system animations (Developer options / accessibility). */
@Composable
fun rememberReducedMotion(): Boolean {
    val context = LocalContext.current
    return remember(context) {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
}
