package uz.kassiron.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import uz.kassiron.app.R

/**
 * The website's design (public/assets/css/app.css) and android/design/mockup.html:
 * the same colours in light ("kunduzgi") and dark ("tungi"), Manrope for text,
 * Onest for headings and amounts. Read colours with `K.c.<name>`, text styles
 * with `K.t.<name>` inside any composable.
 */
val Manrope = FontFamily(
    Font(R.font.manrope_400, FontWeight.Normal),
    Font(R.font.manrope_500, FontWeight.Medium),
    Font(R.font.manrope_600, FontWeight.SemiBold),
    Font(R.font.manrope_700, FontWeight.Bold),
    Font(R.font.manrope_800, FontWeight.ExtraBold),
)

val Onest = FontFamily(
    Font(R.font.onest_600, FontWeight.SemiBold),
    Font(R.font.onest_700, FontWeight.Bold),
    Font(R.font.onest_800, FontWeight.ExtraBold),
)

/** A coloured accent: icon/text colour and its soft background (KPI cards, menu tiles). */
@Immutable
data class Tone(val fg: Color, val bg: Color)

@Immutable
data class KColors(
    val dark: Boolean,
    val bg: Color,        // page background
    val s1: Color,        // cards, bars, inputs
    val s2: Color,        // secondary surface (chips, secondary buttons)
    val bd: Color,        // hairlines and borders
    val tx: Color,        // text
    val mu: Color,        // muted text
    val primary: Color,   // the lime
    val onPrimary: Color, // text on lime
    val ink: Color,       // lime as readable text (dark green on light, light lime on dark)
    val tint: Color,      // soft lime background (selected rows, change box)
    val red: Color, val redBg: Color,
    val amb: Color, val ambBg: Color,
    val grn: Color, val grnBg: Color,
    val blue: Color,
    val glow: Color,      // lime glow behind primary buttons and focused fields
    val heroBg: Color,    // the dashboard hero card: dark in both themes
    val lime: Tone, val blueT: Tone, val redT: Tone, val violet: Tone, val amber: Tone, val teal: Tone,
)

private val LightK = KColors(
    dark = false,
    bg = Color(0xFFF4F6F1), s1 = Color.White, s2 = Color(0xFFEEF1EA), bd = Color(0xFFE3E7DC),
    tx = Color(0xFF0E0F0C), mu = Color(0xFF5F675A),
    primary = Color(0xFFA9FA06), onPrimary = Color(0xFF0E1A02), ink = Color(0xFF3A7300), tint = Color(0xFFEEFBD4),
    red = Color(0xFFDC2626), redBg = Color(0xFFFEF2F2),
    amb = Color(0xFFB45309), ambBg = Color(0xFFFFFBEB),
    grn = Color(0xFF16A34A), grnBg = Color(0xFFF0FDF4),
    blue = Color(0xFF2A63C7),
    glow = Color(0x5278BE00), heroBg = Color(0xFF0F130C),
    lime = Tone(Color(0xFF3A7300), Color(0xFFEEFBD4)),
    blueT = Tone(Color(0xFF2A63C7), Color(0xFFE3EDFC)),
    redT = Tone(Color(0xFFD23B3B), Color(0xFFFDE8E8)),
    violet = Tone(Color(0xFF6A4FD8), Color(0xFFECE8FD)),
    amber = Tone(Color(0xFFB06A00), Color(0xFFFDF1DC)),
    teal = Tone(Color(0xFF0E8F78), Color(0xFFDCF5EF)),
)

private val DarkK = KColors(
    dark = true,
    bg = Color(0xFF090C07), s1 = Color(0xFF11150E), s2 = Color(0xFF171C13), bd = Color(0xFF232A1D),
    tx = Color(0xFFEEF3E6), mu = Color(0xFF93A089),
    primary = Color(0xFFA9FA06), onPrimary = Color(0xFF0E1A02), ink = Color(0xFFBDF94A), tint = Color(0x1FA9FA06),
    red = Color(0xFFF87171), redBg = Color(0x24F87171),
    amb = Color(0xFFFBBF24), ambBg = Color(0x24FBBF24),
    grn = Color(0xFF4ADE80), grnBg = Color(0x244ADE80),
    blue = Color(0xFF7FB0FF),
    glow = Color(0x52A9FA06), heroBg = Color(0xFF0F130C),
    lime = Tone(Color(0xFFA9FA06), Color(0x21A9FA06)),
    blueT = Tone(Color(0xFF7FB0FF), Color(0x247FB0FF)),
    redT = Tone(Color(0xFFFF8A8A), Color(0x24FF8A8A)),
    violet = Tone(Color(0xFFB5A4FF), Color(0x24B5A4FF)),
    amber = Tone(Color(0xFFFFC35C), Color(0x24FFC35C)),
    teal = Tone(Color(0xFF5FE0C5), Color(0x245FE0C5)),
)

/** Text styles of the mockup. */
@Immutable
data class KType(
    val screenTitle: TextStyle,  // top bar: Onest 800 20
    val hero: TextStyle,         // big numbers / greetings: Onest 800 24
    val amountXL: TextStyle,     // payment total: Onest 800 32
    val amount: TextStyle,       // list prices: Onest 800 16
    val title: TextStyle,        // row titles: Manrope 700 15
    val body: TextStyle,         // Manrope 600 14.5
    val label: TextStyle,        // field labels: Manrope 700 12.5
    val small: TextStyle,        // secondary lines: Manrope 600 12
    val button: TextStyle,       // Manrope 800 16
    val chip: TextStyle,         // Manrope 700 12
    val section: TextStyle,      // section headers: Onest 800 14
)

private val Type = KType(
    screenTitle = TextStyle(fontFamily = Onest, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp, letterSpacing = (-0.4).sp),
    hero = TextStyle(fontFamily = Onest, fontWeight = FontWeight.ExtraBold, fontSize = 24.sp, letterSpacing = (-0.5).sp),
    amountXL = TextStyle(fontFamily = Onest, fontWeight = FontWeight.ExtraBold, fontSize = 32.sp, letterSpacing = (-0.9).sp),
    amount = TextStyle(fontFamily = Onest, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, letterSpacing = (-0.15).sp),
    title = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 15.sp),
    body = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.SemiBold, fontSize = 14.5.sp),
    label = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 12.5.sp),
    small = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.SemiBold, fontSize = 12.sp),
    button = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp),
    chip = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 12.sp),
    section = TextStyle(fontFamily = Onest, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp),
)

private val LocalK = staticCompositionLocalOf { LightK }

/** Design tokens: `K.c.primary`, `K.t.amount`. */
object K {
    val c: KColors @Composable get() = LocalK.current
    val t: KType get() = Type
}

/** "system" (follow the phone), "light" or "dark" — chosen in Settings (Kv key "theme"). */
object ThemeMode {
    private val _mode = MutableStateFlow("system")
    val mode: StateFlow<String> = _mode.asStateFlow()
    fun set(mode: String) { _mode.value = mode }
}

/** Kept for code written before the design kit: maps the brand colours. */
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

@Composable
fun KassirTheme(content: @Composable () -> Unit) {
    val mode by ThemeMode.mode.collectAsState()
    val dark = when (mode) {
        "light" -> false
        "dark" -> true
        else -> isSystemInDarkTheme()
    }
    val k = if (dark) DarkK else LightK
    val scheme = (if (dark) darkColorScheme() else lightColorScheme()).copy(
        primary = k.primary, onPrimary = k.onPrimary,
        primaryContainer = k.tint, onPrimaryContainer = k.ink,
        secondary = k.ink, onSecondary = if (dark) k.onPrimary else Color.White,
        secondaryContainer = k.tint, onSecondaryContainer = k.ink,
        background = k.bg, onBackground = k.tx,
        surface = k.s1, onSurface = k.tx,
        surfaceVariant = k.s2, onSurfaceVariant = k.mu,
        surfaceContainer = k.s1, surfaceContainerLow = k.s1, surfaceContainerHigh = k.s1, surfaceContainerHighest = k.s2,
        surfaceContainerLowest = k.bg,
        outline = k.bd, outlineVariant = k.bd,
        error = k.red, errorContainer = k.redBg, onErrorContainer = k.red,
    )
    val typography = Typography().let { d ->
        Typography(
            displayLarge = d.displayLarge.copy(fontFamily = Onest), displayMedium = d.displayMedium.copy(fontFamily = Onest),
            displaySmall = d.displaySmall.copy(fontFamily = Onest), headlineLarge = d.headlineLarge.copy(fontFamily = Onest, fontWeight = FontWeight.ExtraBold),
            headlineMedium = d.headlineMedium.copy(fontFamily = Onest, fontWeight = FontWeight.ExtraBold),
            headlineSmall = d.headlineSmall.copy(fontFamily = Onest, fontWeight = FontWeight.ExtraBold),
            titleLarge = Type.screenTitle, titleMedium = Type.title, titleSmall = Type.label,
            bodyLarge = d.bodyLarge.copy(fontFamily = Manrope, fontWeight = FontWeight.Medium),
            bodyMedium = d.bodyMedium.copy(fontFamily = Manrope, fontWeight = FontWeight.Medium),
            bodySmall = d.bodySmall.copy(fontFamily = Manrope, fontWeight = FontWeight.SemiBold),
            labelLarge = d.labelLarge.copy(fontFamily = Manrope, fontWeight = FontWeight.Bold),
            labelMedium = d.labelMedium.copy(fontFamily = Manrope, fontWeight = FontWeight.Bold),
            labelSmall = d.labelSmall.copy(fontFamily = Manrope, fontWeight = FontWeight.Bold),
        )
    }
    CompositionLocalProvider(LocalK provides k) {
        MaterialTheme(colorScheme = scheme, typography = typography, content = content)
    }
}

/**
 * Outlined / text buttons in code written before the kit: their content uses the
 * readable "ink" green instead of the lime.
 */
object Btn {
    @Composable
    fun outlined() = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.secondary)

    @Composable
    fun text() = androidx.compose.material3.ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.secondary)
}
