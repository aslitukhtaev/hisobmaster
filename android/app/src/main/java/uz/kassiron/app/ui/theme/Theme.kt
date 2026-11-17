package uz.kassiron.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/** The website's palette (public/assets/css/app.css): lime on near-black / off-white. */
object Brand {
    val Lime = Color(0xFFA9FA06)
    val LimeHover = Color(0xFF97E600)
    val OnLime = Color(0xFF0E1A02)
    val LimeInk = Color(0xFF3A7300)
    val LimeInkDark = Color(0xFFBDF94A)
    val Danger = Color(0xFFDC2626)
    val Success = Color(0xFF16A34A)
    val Warning = Color(0xFFB45309)
}

private val Light = lightColorScheme(
    primary = Brand.Lime,
    onPrimary = Brand.OnLime,
    primaryContainer = Color(0xFFEEFBD4),
    onPrimaryContainer = Brand.LimeInk,
    secondary = Brand.LimeInk,
    onSecondary = Color.White,
    background = Color(0xFFF4F6F1),
    onBackground = Color(0xFF0E0F0C),
    surface = Color.White,
    onSurface = Color(0xFF0E0F0C),
    surfaceVariant = Color(0xFFEEF1EA),
    onSurfaceVariant = Color(0xFF5F675A),
    outline = Color(0xFFE3E7DC),
    outlineVariant = Color(0xFFE3E7DC),
    error = Brand.Danger,
)

private val Dark = darkColorScheme(
    primary = Brand.Lime,
    onPrimary = Brand.OnLime,
    primaryContainer = Color(0x1FA9FA06),
    onPrimaryContainer = Brand.LimeInkDark,
    secondary = Brand.LimeInkDark,
    onSecondary = Brand.OnLime,
    background = Color(0xFF090C07),
    onBackground = Color(0xFFF1F4EC),
    surface = Color(0xFF11150E),
    onSurface = Color(0xFFF1F4EC),
    surfaceVariant = Color(0xFF1A2015),
    onSurfaceVariant = Color(0xFF9AA392),
    outline = Color(0xFF252C1F),
    outlineVariant = Color(0xFF252C1F),
    error = Color(0xFFF87171),
)

private val AppTypography = Typography(
    headlineMedium = TextStyle(fontWeight = FontWeight.ExtraBold, fontSize = 26.sp),
    titleLarge = TextStyle(fontWeight = FontWeight.Bold, fontSize = 21.sp),
    titleMedium = TextStyle(fontWeight = FontWeight.Bold, fontSize = 17.sp),
    bodyLarge = TextStyle(fontSize = 16.sp),
    bodyMedium = TextStyle(fontSize = 14.sp),
    labelLarge = TextStyle(fontWeight = FontWeight.Bold, fontSize = 15.sp),
)

@Composable
fun KassirTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (dark) Dark else Light, typography = AppTypography, content = content)
}

/**
 * Outlined / text buttons: the lime brand colour is unreadable as text on the
 * light theme, so their content uses the darker "ink" green (secondary).
 */
object Btn {
    @Composable
    fun outlined() = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.secondary)

    @Composable
    fun text() = androidx.compose.material3.ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.secondary)
}
