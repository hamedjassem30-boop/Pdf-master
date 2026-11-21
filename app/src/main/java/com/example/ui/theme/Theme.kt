package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

private val LightColorScheme = lightColorScheme(
    primary = Crimson600,
    onPrimary = PureWhite,
    primaryContainer = Crimson100,
    onPrimaryContainer = Crimson900,
    secondary = Navy900,
    onSecondary = PureWhite,
    secondaryContainer = Slate200,
    onSecondaryContainer = Navy900,
    tertiary = CatConvertToColor,
    onTertiary = PureWhite,
    background = Slate50,
    onBackground = Navy900,
    surface = PureWhite,
    onSurface = Navy900,
    surfaceVariant = Slate100,
    onSurfaceVariant = Slate600,
    outline = Slate200,
    outlineVariant = Slate200,
    error = ErrorRed,
    onError = PureWhite
)

private val DarkColorScheme = darkColorScheme(
    primary = Crimson500,
    onPrimary = PureWhite,
    primaryContainer = Crimson900,
    onPrimaryContainer = Crimson100,
    secondary = Slate200,
    onSecondary = Navy900,
    secondaryContainer = Navy800,
    onSecondaryContainer = Slate100,
    tertiary = ColorTokens.DarkBlueAccent,
    onTertiary = Navy950,
    background = Navy950,
    onBackground = Slate50,
    surface = Navy900,
    onSurface = Slate50,
    surfaceVariant = Navy800,
    onSurfaceVariant = Slate400,
    outline = Navy700,
    outlineVariant = Navy800,
    error = ErrorRed,
    onError = PureWhite
)

private object ColorTokens {
    val DarkBlueAccent = androidx.compose.ui.graphics.Color(0xFF60A5FA)
}

val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun PdfMasterTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    isArabic: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    val typography = buildAppTypography(isArabic = isArabic)
    val layoutDirection = if (isArabic) LayoutDirection.Rtl else LayoutDirection.Ltr

    CompositionLocalProvider(LocalLayoutDirection provides layoutDirection) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = typography,
            shapes = AppShapes,
            content = content
        )
    }
}
