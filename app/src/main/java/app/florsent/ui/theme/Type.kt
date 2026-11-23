package app.florsent.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val Base = Typography()

val FlorsentTypography = Base.copy(
    displaySmall = Base.displaySmall.copy(fontWeight = FontWeight.Light),
    headlineMedium = Base.headlineMedium.copy(fontWeight = FontWeight.Medium, letterSpacing = 0.5.sp),
    titleLarge = Base.titleLarge.copy(fontWeight = FontWeight.Medium),
    titleMedium = Base.titleMedium.copy(fontWeight = FontWeight.Medium),
    bodyLarge = Base.bodyLarge.copy(lineHeight = 26.sp),
)
