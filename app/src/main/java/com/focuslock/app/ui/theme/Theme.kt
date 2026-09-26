package com.focuslock.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ---------------------------------------------------------------- 品牌色

val Indigo = Color(0xFF5B4BE0)
val IndigoDeep = Color(0xFF3A2CB8)
val IndigoSoft = Color(0xFFE7E3FF)
val Amber = Color(0xFFF59E0B)
val AmberSoft = Color(0xFFFFF0D6)
val Mint = Color(0xFF10B981)
val Coral = Color(0xFFEF4444)

/** 锁屏界面用的一套独立颜色，和主界面主题解耦 */
data class LockPalette(
    val clock: Color = Color.White,
    val subtitle: Color = Color(0xCCFFFFFF),
    val faint: Color = Color(0x8CFFFFFF),
    val ring: Color = Color(0xFFA594FF),
    val ringTrack: Color = Color(0x40FFFFFF)
)

val LocalLockPalette = staticCompositionLocalOf { LockPalette() }

private val LightColors = lightColorScheme(
    primary = Indigo,
    onPrimary = Color.White,
    primaryContainer = IndigoSoft,
    onPrimaryContainer = Color(0xFF18006E),
    secondary = Amber,
    onSecondary = Color.White,
    secondaryContainer = AmberSoft,
    onSecondaryContainer = Color(0xFF3D2A00),
    tertiary = Mint,
    onTertiary = Color.White,
    error = Coral,
    onError = Color.White,
    background = Color(0xFFF6F6FB),
    onBackground = Color(0xFF1B1B22),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF1B1B22),
    surfaceVariant = Color(0xFFEFEEF7),
    onSurfaceVariant = Color(0xFF5A5A6B),
    outline = Color(0xFFC9C8D6),
    outlineVariant = Color(0xFFE3E2EE)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFB9B0FF),
    onPrimary = Color(0xFF22146E),
    primaryContainer = Color(0xFF3B2CA8),
    onPrimaryContainer = Color(0xFFE7E3FF),
    secondary = Color(0xFFFFC664),
    onSecondary = Color(0xFF3D2A00),
    secondaryContainer = Color(0xFF5A4200),
    onSecondaryContainer = Color(0xFFFFF0D6),
    tertiary = Color(0xFF4ADEA8),
    onTertiary = Color(0xFF00382A),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    background = Color(0xFF111116),
    onBackground = Color(0xFFE6E4EE),
    surface = Color(0xFF191920),
    onSurface = Color(0xFFE6E4EE),
    surfaceVariant = Color(0xFF26262F),
    onSurfaceVariant = Color(0xFFB9B8C6),
    outline = Color(0xFF575765),
    outlineVariant = Color(0xFF34343E)
)

private val AppTypography = Typography(
    displayLarge = TextStyle(fontSize = 57.sp, lineHeight = 64.sp, fontWeight = FontWeight.Light),
    headlineMedium = TextStyle(fontSize = 28.sp, lineHeight = 36.sp, fontWeight = FontWeight.SemiBold),
    headlineSmall = TextStyle(fontSize = 24.sp, lineHeight = 32.sp, fontWeight = FontWeight.SemiBold),
    titleLarge = TextStyle(fontSize = 20.sp, lineHeight = 28.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontSize = 16.sp, lineHeight = 24.sp, fontWeight = FontWeight.Medium),
    titleSmall = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontSize = 12.sp, lineHeight = 16.sp),
    labelLarge = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium),
    labelMedium = TextStyle(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium),
    labelSmall = TextStyle(fontSize = 11.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium)
)

/** 常用圆角 */
object Shapes {
    val card = androidx.compose.foundation.shape.RoundedCornerShape(20.dp)
    val cardSmall = androidx.compose.foundation.shape.RoundedCornerShape(14.dp)
    val chip = androidx.compose.foundation.shape.RoundedCornerShape(50)
}

@Composable
fun FocusLockTheme(
    themeMode: Int = 0,
    content: @Composable () -> Unit
) {
    val dark = when (themeMode) {
        1 -> false
        2 -> true
        else -> isSystemInDarkTheme()
    }
    // 锁屏界面永远是深色底，两套主题共用同一组配色
    val palette = LockPalette()
    MaterialTheme(
        colorScheme = if (dark) DarkColors else LightColors,
        typography = AppTypography,
        shapes = MaterialTheme.shapes.copy(
            extraSmall = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
            small = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
            medium = androidx.compose.foundation.shape.RoundedCornerShape(18.dp),
            large = androidx.compose.foundation.shape.RoundedCornerShape(24.dp)
        )
    ) {
        CompositionLocalProvider(LocalLockPalette provides palette, content = content)
    }
}
