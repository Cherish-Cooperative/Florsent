package app.florsent.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
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

private val DarkColors = darkColorScheme(
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

/** Fixed calm palette; dynamic color is intentionally off so the brand stays consistent. */
@Composable
fun FlorsentTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = FlorsentTypography,
        content = content,
    )
}
