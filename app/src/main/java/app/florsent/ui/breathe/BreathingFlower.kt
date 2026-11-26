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
import kotlin.math.PI
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

/** One ring of petals; rings open from the outside in and close from the inside out. */
private class PetalRing(
    val count: Int,
    /** Petal length and width as fractions of the flower radius, fully open. */
    val length: Float,
    val width: Float,
    /** Expansion at which this ring starts to open; inner rings follow the outer ones. */
    val opensAt: Float,
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
    PetalRing(count = 8, length = 0.84f, width = 0.34f, opensAt = 0.00f, offsetDeg = 0f, budTwistDeg = 26f, budWidth = 0.62f, driftFactor = 1f, wobblePhase = 0f),
    PetalRing(count = 8, length = 0.64f, width = 0.28f, opensAt = 0.14f, offsetDeg = 22.5f, budTwistDeg = -32f, budWidth = 0.58f, driftFactor = -0.6f, wobblePhase = 1f),
    PetalRing(count = 5, length = 0.42f, width = 0.22f, opensAt = 0.30f, offsetDeg = 36f, budTwistDeg = 40f, budWidth = 0.55f, driftFactor = 0.4f, wobblePhase = 2f),
)

/** Width of the expansion range over which one ring goes from closed to open. */
private const val RING_SPAN = 0.70f

/** Tilt of a petal away from upright, in radians: closed petals stand up, open ones lie flat. */
private const val BUD_TILT = 0.30f
private const val OPEN_TILT = (PI / 2).toFloat()

/**
 * A flower that opens and closes with the breath, drawn entirely in the draw phase.
 *
 * Each petal is a flat teardrop seen from above. Opening tilts it from upright (a short, narrow,
 * slightly twisted sliver: the bud) down to flat (its full length), so the projected length follows
 * sin(tilt). Rings start one after another, outer first, so breathing in unfurls the flower from the
 * outside in, and breathing out folds it back from the inside out, the way a real bloom moves. The
 * underside of a standing petal is a shade deeper, which reads as a petal turning towards the light.
 *
 * Performance contract: [expansion], [timeSeconds] and [progress] are lambdas read only inside
 * `onDrawBehind`, so a changing animation clock invalidates drawing but never recomposition. Paths,
 * gradients and the stroke are built once per size/color change in `drawWithCache`; per frame we
 * only apply transforms, with no allocation and no blur (the glow is a pre-built radial gradient).
 *
 * @param expansion 0 = closed bud, 1 = fully open.
 * @param timeSeconds monotonic seconds driving the slow drift and petal "breathing" wobble.
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

                scale(0.60f + 0.40f * e, c) { drawCircle(halo, radius = r, center = c) }

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
                    val open = ringOpenness(e, ring.opensAt)
                    val tilt = BUD_TILT + (OPEN_TILT - BUD_TILT) * open
                    val lengthScale = sin(tilt)
                    val widthScale = ring.budWidth + (1f - ring.budWidth) * open
                    val twist = ring.budTwistDeg * (1f - open)
                    val step = 360f / ring.count
                    for (i in 0 until ring.count) {
                        val wobble = sin(t * 0.8f + i * 1.9f + ring.wobblePhase)
                        val angle = ring.offsetDeg + i * step + drift * ring.driftFactor + twist + wobble * 2f
                        withTransform({
                            rotate(angle, c)
                            scale(widthScale * (1f + 0.04f * wobble), lengthScale, c)
                        }) {
                            drawPath(petals[k], undersides[k], alpha = 0.85f * (1f - open))
                            drawPath(petals[k], faces[k], alpha = 0.35f + 0.65f * open)
                        }
                    }
                }

                // The heart of the flower shows as the inner petals part.
                val heart = ringOpenness(e, RINGS.last().opensAt)
                scale(0.55f + 0.55f * heart, c) { drawCircle(coreBrush, radius = r * 0.24f, center = c) }
            }
        },
    )
}

/** 0..1 openness of a ring that starts opening at [opensAt]; eased so each ring settles softly. */
private fun ringOpenness(expansion: Float, opensAt: Float): Float {
    val x = ((expansion - opensAt) / RING_SPAN).coerceIn(0f, 1f)
    return x * x * (3f - 2f * x)
}

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
