package app.florsent.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import android.os.Build
import android.util.Log
import app.florsent.BuildConfig
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

internal val LightColors = lightColorScheme(
    primary = SageLight,
    onPrimary = Color.White,
    primaryContainer = SageContainerLight,
    onPrimaryContainer = OnSageContainerLight,
    secondary = MistLight,
    onSecondary = Color.White,
    secondaryContainer = MistContainerLight,
    onSecondaryContainer = OnMistContainerLight,
    tertiary = StoneLight,
    onTertiary = Color.White,
    tertiaryContainer = StoneContainerLight,
    onTertiaryContainer = OnStoneContainerLight,
    background = BackgroundLight,
    onBackground = InkLight,
    surface = BackgroundLight,
    onSurface = InkLight,
    surfaceVariant = SurfaceHighLight,
    onSurfaceVariant = InkMutedLight,
    surfaceContainerLowest = SurfaceLowestLight,
    surfaceContainerLow = SurfaceLowLight,
    surfaceContainer = SurfaceContainerLight,
    surfaceContainerHigh = SurfaceHighLight,
    surfaceContainerHighest = SurfaceHighestLight,
    outline = OutlineLight,
    outlineVariant = OutlineVariantLight,
)

internal val DarkColors = darkColorScheme(
    primary = SageDark,
    onPrimary = OnSageDark,
    primaryContainer = SageContainerDark,
    onPrimaryContainer = OnSageContainerDark,
    secondary = MistDark,
    onSecondary = OnMistDark,
    secondaryContainer = MistContainerDark,
    onSecondaryContainer = OnMistContainerDark,
    tertiary = StoneDark,
    onTertiary = OnStoneDark,
    tertiaryContainer = StoneContainerDark,
    onTertiaryContainer = OnStoneContainerDark,
    background = BackgroundDark,
    onBackground = InkDark,
    surface = BackgroundDark,
    onSurface = InkDark,
    surfaceVariant = SurfaceHighDark,
    onSurfaceVariant = InkMutedDark,
    surfaceContainerLowest = SurfaceLowestDark,
    surfaceContainerLow = SurfaceLowDark,
    surfaceContainer = SurfaceContainerDark,
    surfaceContainerHigh = SurfaceHighDark,
    surfaceContainerHighest = SurfaceHighestDark,
    outline = OutlineDark,
    outlineVariant = OutlineVariantDark,
)

/** Whether the device can derive a palette from the wallpaper (Material You, Android 12+). */
val supportsDynamicColor: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

/**
 * Florsent's sage palette by default; the system's wallpaper-based palette when the user turns on
 * "follow system colours". Everything (including the breathing flower) reads colours from the
 * scheme, so both look coherent. Debug builds log a WCAG audit of the dynamic palette.
 */
@Composable
fun FlorsentTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val useDynamic = dynamicColor && supportsDynamicColor
    val scheme = when {
        useDynamic && darkTheme -> dynamicDarkColorScheme(context)
        useDynamic -> dynamicLightColorScheme(context)
        darkTheme -> DarkColors
        else -> LightColors
    }
    if (BuildConfig.DEBUG && useDynamic) {
        LaunchedEffect(scheme) { logContrast(scheme, darkTheme) }
    }
    MaterialTheme(
        colorScheme = scheme,
        typography = FlorsentTypography,
        content = content,
    )
}

/** The text/background pairs Florsent actually draws. */
fun ColorScheme.textPairs(): List<ContrastAudit.Pair> = listOf(
    ContrastAudit.Pair("onSurface/background", onSurface.toArgb(), background.toArgb()),
    ContrastAudit.Pair("onSurfaceVariant/background", onSurfaceVariant.toArgb(), background.toArgb()),
    // Cards (today, invitation, insights) sit on surfaceContainerLow.
    ContrastAudit.Pair("onSurface/surfaceContainerLow", onSurface.toArgb(), surfaceContainerLow.toArgb()),
    ContrastAudit.Pair("onSurfaceVariant/surfaceContainerLow", onSurfaceVariant.toArgb(), surfaceContainerLow.toArgb()),
    ContrastAudit.Pair("primary/background", primary.toArgb(), background.toArgb()),
    ContrastAudit.Pair("primary/surfaceContainerLow", primary.toArgb(), surfaceContainerLow.toArgb()),
    ContrastAudit.Pair("onPrimary/primary", onPrimary.toArgb(), primary.toArgb()),
    ContrastAudit.Pair("onSecondaryContainer/secondaryContainer", onSecondaryContainer.toArgb(), secondaryContainer.toArgb()),
    ContrastAudit.Pair(
        "onPrimaryContainer/selectedCard", onPrimaryContainer.toArgb(), primaryContainer.toArgb(),
        base = surfaceContainerLow.toArgb(), overlayAlpha = 0.55f,
    ),
    ContrastAudit.Pair(
        "onSurfaceVariant/selectedCard", onSurfaceVariant.toArgb(), primaryContainer.toArgb(),
        base = surfaceContainerLow.toArgb(), overlayAlpha = 0.55f,
    ),
)

private fun logContrast(scheme: ColorScheme, dark: Boolean) {
    val results = ContrastAudit.audit(scheme.textPairs())
    val worst = results.minBy { it.ratio }
    Log.i(
        "FlorsentContrast",
        "dynamic ${if (dark) "dark" else "light"}: " +
            results.joinToString { "${it.name}=${"%.2f".format(it.ratio)}${if (it.passes) "" else "!"}" } +
            " | worst=${worst.name} ${"%.2f".format(worst.ratio)} ${if (results.all { it.passes }) "PASS" else "FAIL"}",
    )
}
