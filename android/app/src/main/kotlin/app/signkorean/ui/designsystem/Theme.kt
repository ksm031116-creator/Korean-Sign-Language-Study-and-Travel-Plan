package app.signkorean.ui.designsystem

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import kotlin.math.max
import kotlin.math.min

/**
 * 디자인 시스템 "Sign Korean" 토큰 (설계서 §5.4). 이름은 디자인 시스템과 같다.
 * primary 는 판정에 쓰지 않는다. 알아요는 know(초록 채움), 몰라요는 accent(연한 주황 바탕 + 진한 주황).
 */
@Immutable
data class SignKoreanTokens(
    val paper: Color,
    val surface: Color,
    val line: Color,
    val ink: Color,
    val muted: Color,
    val primary: Color,
    val primaryDark: Color,
    val primarySoft: Color,
    val know: Color,
    val knowSoft: Color,
    val knowInk: Color,
    val accent: Color,
    val accentSoft: Color,
    val accentInk: Color,
    val danger: Color,
    val dangerSoft: Color,
) {
    companion object {
        val Light = SignKoreanTokens(
            paper = Color(0xFFF6F7F9),
            surface = Color(0xFFFFFFFF),
            line = Color(0xFFE3E6EA),
            ink = Color(0xFF1F2933),
            muted = Color(0xFF5B6672),
            primary = Color(0xFF2B4C7E),
            primaryDark = Color(0xFF1D3A63),
            primarySoft = Color(0xFFE8EEF6),
            know = Color(0xFF1E7A4F),
            knowSoft = Color(0xFFE2F3E9),
            knowInk = Color(0xFF17613E),
            accent = Color(0xFFC4661F),
            accentSoft = Color(0xFFFDEEE4),
            accentInk = Color(0xFFA8460F),
            danger = Color(0xFFB42318),
            dangerSoft = Color(0xFFFDECEA),
        )
    }
}

/** WCAG 2 대비 비율. */
object Contrast {
    fun ratio(a: Color, b: Color): Double {
        val la = a.luminance().toDouble() + 0.05
        val lb = b.luminance().toDouble() + 0.05
        return max(la, lb) / min(la, lb)
    }
}

val LocalSignKoreanTokens = staticCompositionLocalOf { SignKoreanTokens.Light }

/** 본문 16sp, 제목 20~24sp (PRD UX06). */
private val SignKoreanTypography = Typography(
    headlineSmall = TextStyle(fontSize = 24.sp, lineHeight = 32.sp, fontWeight = FontWeight.SemiBold),
    titleLarge = TextStyle(fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontSize = 20.sp, lineHeight = 26.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontSize = 16.sp, lineHeight = 22.sp),
    labelLarge = TextStyle(fontSize = 16.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold),
    labelMedium = TextStyle(fontSize = 14.sp, lineHeight = 18.sp, fontWeight = FontWeight.Medium),
)

@Composable
fun SignKoreanTheme(content: @Composable () -> Unit) {
    val t = SignKoreanTokens.Light
    val scheme = lightColorScheme(
        primary = t.primary,
        onPrimary = Color.White,
        primaryContainer = t.primarySoft,
        onPrimaryContainer = t.primaryDark,
        secondary = t.accent,
        onSecondary = Color.White,
        background = t.paper,
        onBackground = t.ink,
        surface = t.surface,
        onSurface = t.ink,
        surfaceVariant = t.paper,
        onSurfaceVariant = t.muted,
        outline = t.line,
        outlineVariant = t.line,
        error = t.danger,
        onError = Color.White,
        errorContainer = t.dangerSoft,
    )
    CompositionLocalProvider(LocalSignKoreanTokens provides t) {
        MaterialTheme(colorScheme = scheme, typography = SignKoreanTypography, content = content)
    }
}

object SignKoreanTheme {
    val tokens: SignKoreanTokens
        @Composable get() = LocalSignKoreanTokens.current
}
