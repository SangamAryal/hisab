package app.hisab.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

private val Light = lightColorScheme(
    primary = Color(0xFF0F766E),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFCCF0EA),
    onPrimaryContainer = Color(0xFF00201C),
    secondary = Color(0xFFB45309),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFDE7C3),
    onSecondaryContainer = Color(0xFF2E1500),
    background = Color(0xFFF7F5EF),
    onBackground = Color(0xFF1C1B17),
    surface = Color(0xFFF7F5EF),
    onSurface = Color(0xFF1C1B17),
    surfaceVariant = Color(0xFFE8E4D9),
    onSurfaceVariant = Color(0xFF4A4740),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF2EFE7),
    surfaceContainer = Color(0xFFECE9E0),
    surfaceContainerHigh = Color(0xFFE6E2D9),
    outline = Color(0xFF7B776D),
    outlineVariant = Color(0xFFCCC7BA),
    error = Color(0xFFB3261E),
)

private val Dark = darkColorScheme(
    primary = Color(0xFF5EDBC9),
    onPrimary = Color(0xFF003731),
    primaryContainer = Color(0xFF005048),
    onPrimaryContainer = Color(0xFFCCF0EA),
    secondary = Color(0xFFFBBF24),
    onSecondary = Color(0xFF3F2400),
    secondaryContainer = Color(0xFF5B3A00),
    onSecondaryContainer = Color(0xFFFDE7C3),
    background = Color(0xFF13130F),
    onBackground = Color(0xFFE6E2D9),
    surface = Color(0xFF13130F),
    onSurface = Color(0xFFE6E2D9),
    surfaceVariant = Color(0xFF34322B),
    onSurfaceVariant = Color(0xFFCAC5B9),
    surfaceContainerLowest = Color(0xFF0E0E0B),
    surfaceContainerLow = Color(0xFF1C1B17),
    surfaceContainer = Color(0xFF201F1B),
    surfaceContainerHigh = Color(0xFF2B2A25),
    outline = Color(0xFF949086),
    outlineVariant = Color(0xFF4A4740),
    error = Color(0xFFF2B8B5),
)

/** Colors for money: green when you're owed, amber when you owe. */
@Immutable
data class MoneyColors(val owed: Color, val owes: Color, val settled: Color)

val LocalMoneyColors = staticCompositionLocalOf { MoneyColors(Color.Unspecified, Color.Unspecified, Color.Unspecified) }

@Composable
fun HisabTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val money = if (dark) {
        MoneyColors(owed = Color(0xFF5EDBC9), owes = Color(0xFFFBBF24), settled = Color(0xFF949086))
    } else {
        MoneyColors(owed = Color(0xFF0F766E), owes = Color(0xFFB45309), settled = Color(0xFF7B776D))
    }
    androidx.compose.runtime.CompositionLocalProvider(LocalMoneyColors provides money) {
        MaterialTheme(colorScheme = if (dark) Dark else Light, content = content)
    }
}
