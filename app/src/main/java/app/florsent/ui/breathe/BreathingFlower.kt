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
import kotlin.math.sin

private const val PETALS = 6

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
 * Organic two-ring flower with a glow, drawn entirely in the draw phase.
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
            val outerPetal = petalPath(c, length = r * 0.80f, width = r * 0.36f)
            val innerPetal = petalPath(c, length = r * 0.54f, width = r * 0.25f)
            val halo = Brush.radialGradient(
                0f to colors.halo.copy(alpha = 0.26f),
                0.5f to colors.halo.copy(alpha = 0.10f),
                1f to Color.Transparent,
                center = c,
                radius = r,
            )
            val outerBrush = Brush.radialGradient(
                0f to colors.outerCenter, 1f to colors.outerEdge, center = c, radius = r * 0.82f,
            )
            val innerBrush = Brush.radialGradient(
                0f to colors.innerCenter, 1f to colors.innerEdge, center = c, radius = r * 0.56f,
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

                scale(0.68f + 0.32f * e, c) { drawCircle(halo, radius = r, center = c) }

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

                val drift = t * 3.5f
                val outerLength = 0.52f + 0.48f * e
                val outerWidth = 0.78f + 0.27f * e
                for (i in 0 until PETALS) {
                    val wobble = sin(t * 0.7f + i * 1.7f)
                    val angle = i * (360f / PETALS) + drift + wobble * 2.5f
                    withTransform({
                        rotate(angle, c)
                        scale(outerWidth * (1f + 0.05f * wobble), outerLength, c)
                    }) { drawPath(outerPetal, outerBrush) }
                }

                val innerLength = 0.60f + 0.40f * e
                val innerWidth = 0.84f + 0.20f * e
                for (i in 0 until PETALS) {
                    val wobble = sin(t * 0.9f + i * 2.3f + 1f)
                    val angle = i * (360f / PETALS) + 180f / PETALS - drift * 0.6f + wobble * 2f
                    withTransform({
                        rotate(angle, c)
                        scale(innerWidth * (1f + 0.06f * wobble), innerLength, c)
                    }) { drawPath(innerPetal, innerBrush) }
                }

                scale(0.85f + 0.30f * e, c) { drawCircle(coreBrush, radius = r * 0.24f, center = c) }
            }
        },
    )
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
