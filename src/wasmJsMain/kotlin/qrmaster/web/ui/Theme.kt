package qrmaster.web.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val PurplePrimary = Color(0xFF6D28D9)
val PurpleDeep = Color(0xFF5B21B6)
val BlueAccent = Color(0xFF2563EB)
val LightBg = Color(0xFFF5F3FF)
val DarkBg = Color(0xFF0F0D1A)
val DarkSurface = Color(0xFF1B1830)
val DarkSidebar = Color(0xFF17142A)

private val LightColors = lightColorScheme(
    primary = PurplePrimary,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFEDE9FE),
    onPrimaryContainer = PurpleDeep,
    secondary = BlueAccent,
    background = LightBg,
    onBackground = Color(0xFF1E1B2E),
    surface = Color.White,
    onSurface = Color(0xFF1E1B2E),
    surfaceVariant = Color(0xFFEFEAFB),
    onSurfaceVariant = Color(0xFF5B5670),
    outline = Color(0xFFDDD6F3),
    error = Color(0xFFDC2626)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFA78BFA),
    onPrimary = Color(0xFF2A1650),
    primaryContainer = Color(0xFF3B2A63),
    onPrimaryContainer = Color(0xFFE9E4FF),
    secondary = Color(0xFF7FB0FF),
    background = DarkBg,
    onBackground = Color(0xFFF1EEFB),
    surface = DarkSurface,
    onSurface = Color(0xFFF1EEFB),
    surfaceVariant = Color(0xFF262142),
    onSurfaceVariant = Color(0xFFB9B3D9),
    outline = Color(0xFF3A3560),
    error = Color(0xFFF87171)
)

@Composable
fun QrMasterTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content
    )
}
