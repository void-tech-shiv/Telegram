package dev.glacia.app.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import dev.glacia.app.settings.ThemeChoice

internal val IceBlue = Color(0xFF83DDF7)
internal val Violet = Color(0xFFA98BFF)

@Composable
fun GlaciaTheme(choice: ThemeChoice, content: @Composable (Boolean) -> Unit) {
    val dark = when (choice) {
        ThemeChoice.SYSTEM -> isSystemInDarkTheme()
        ThemeChoice.DARK -> true
        ThemeChoice.LIGHT -> false
    }
    val scheme = if (dark) darkColorScheme(
        primary = IceBlue,
        secondary = Violet,
        background = Color(0xFF080B1B),
        surface = Color(0xFF13182D),
        onBackground = Color(0xFFF7F8FF),
        onSurface = Color(0xFFF7F8FF)
    ) else lightColorScheme(
        primary = Color(0xFF475AE0),
        secondary = Color(0xFF6753B3),
        background = Color(0xFFEDF5FF),
        surface = Color(0xFFFFFFFF),
        onBackground = Color(0xFF1B2240),
        onSurface = Color(0xFF1B2240)
    )
    MaterialTheme(colorScheme = scheme) { content(dark) }
}
