package com.foodpos.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight

private val LightColors = lightColorScheme(
    primary = Color(0xFFB4441C),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFDBCF),
    onPrimaryContainer = Color(0xFF3A0B00),
    secondary = Color(0xFF2E6B4F),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFB2F1CE),
    onSecondaryContainer = Color(0xFF002113),
    tertiary = Color(0xFF8A5A00),
    tertiaryContainer = Color(0xFFFFDDB0),
    background = Color(0xFFFFFBF8),
    surface = Color(0xFFFFFBF8),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFFCF3EF),
    surfaceContainer = Color(0xFFF7EDE8),
    surfaceContainerHigh = Color(0xFFF1E7E2),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFFFB59C),
    onPrimary = Color(0xFF5E1700),
    primaryContainer = Color(0xFF8E2F08),
    onPrimaryContainer = Color(0xFFFFDBCF),
    secondary = Color(0xFF96D5B3),
    onSecondary = Color(0xFF003824),
    secondaryContainer = Color(0xFF115238),
    onSecondaryContainer = Color(0xFFB2F1CE),
    tertiary = Color(0xFFFFB95C),
    tertiaryContainer = Color(0xFF694300),
    background = Color(0xFF1A1210),
    surface = Color(0xFF1A1210),
    surfaceContainerLowest = Color(0xFF140D0B),
    surfaceContainerLow = Color(0xFF221A17),
    surfaceContainer = Color(0xFF271E1B),
    surfaceContainerHigh = Color(0xFF322825),
)

/** Status colours used for stock levels, alerts and profit/loss. */
@Immutable
data class StatusColors(
    val good: Color,
    val goodContainer: Color,
    val warning: Color,
    val warningContainer: Color,
    val danger: Color,
    val dangerContainer: Color,
    /** Categorical palette for chart series and pie slices. */
    val chart: List<Color>,
)

private val LightStatus = StatusColors(
    good = Color(0xFF1B7A4B), goodContainer = Color(0xFFD3F2E0),
    warning = Color(0xFF9A5B00), warningContainer = Color(0xFFFFE7C2),
    danger = Color(0xFFBA1A1A), dangerContainer = Color(0xFFFFDAD6),
    chart = listOf(
        Color(0xFFB4441C), Color(0xFF2E6B4F), Color(0xFFD08A00), Color(0xFF3D5BA9),
        Color(0xFF8C3F8F), Color(0xFF00838F), Color(0xFF6D4C41), Color(0xFF7A7A2A),
    ),
)

private val DarkStatus = StatusColors(
    good = Color(0xFF6FD69C), goodContainer = Color(0xFF0F3D26),
    warning = Color(0xFFFFC266), warningContainer = Color(0xFF4A3000),
    danger = Color(0xFFFFB4AB), dangerContainer = Color(0xFF5C1111),
    chart = listOf(
        Color(0xFFFF9C7A), Color(0xFF7FD4A8), Color(0xFFFFC94D), Color(0xFF9DB4FF),
        Color(0xFFE29BE5), Color(0xFF5FD3DE), Color(0xFFC9A193), Color(0xFFD1D17A),
    ),
)

val LocalStatusColors = staticCompositionLocalOf { LightStatus }

private val AppTypography = Typography().run {
    copy(
        headlineMedium = headlineMedium.copy(fontWeight = FontWeight.Bold),
        headlineSmall = headlineSmall.copy(fontWeight = FontWeight.Bold),
        titleLarge = titleLarge.copy(fontWeight = FontWeight.SemiBold),
        titleMedium = titleMedium.copy(fontWeight = FontWeight.SemiBold),
    )
}

@Composable
fun PosTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalStatusColors provides if (darkTheme) DarkStatus else LightStatus) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkColors else LightColors,
            typography = AppTypography,
            content = content,
        )
    }
}
