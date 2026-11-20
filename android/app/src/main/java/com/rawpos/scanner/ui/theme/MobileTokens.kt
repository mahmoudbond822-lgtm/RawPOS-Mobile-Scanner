package com.rawpos.scanner.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Single source of mobile design tokens, derived from the verified RawPOS desktop roles.
 * Views must reference these tokens (or MaterialTheme roles), never raw hex values.
 */
object RawPosTokens {
    // Light
    val LightBackground = Color(0xFFF5F6F8)
    val LightSurface = Color(0xFFFFFFFF)
    val LightSurfaceVariant = Color(0xFFEAEEF2)
    val LightTextPrimary = Color(0xFF1F2328)
    val LightTextSecondary = Color(0xFF57606A)
    val LightOutline = Color(0xFF6E7781)
    val LightPrimary = Color(0xFF0969DA)
    val LightOnPrimary = Color(0xFFFFFFFF)
    val LightSuccess = Color(0xFF1A7F37)
    val LightWarning = Color(0xFF9A6700)
    val LightDanger = Color(0xFFCF222E)
    val LightInfo = Color(0xFF0E7490)
    val LightSuccessSurface = Color(0xFFDAFBE1)
    val LightWarningSurface = Color(0xFFFFF8C5)
    val LightDangerSurface = Color(0xFFFFEBE9)
    val LightInfoSurface = Color(0xFFDDF4FF)

    // Scanner stage (fixed in both themes)
    val ScannerStage = Color(0xFF000000)
    val ScannerTarget = Color(0xFFFFFFFF)

    // Dark
    val DarkBackground = Color(0xFF0D1117)
    val DarkSurface = Color(0xFF161B22)
    val DarkSurfaceVariant = Color(0xFF21262D)
    val DarkTextPrimary = Color(0xFFE6EDF3)
    val DarkTextSecondary = Color(0xFF8B949E)
    val DarkOutline = Color(0xFF8B949E)
    val DarkPrimary = Color(0xFF58A6FF)
    val DarkOnPrimary = Color(0xFF0D1117)
    val DarkSuccess = Color(0xFF3FB950)
    val DarkWarning = Color(0xFFD29922)
    val DarkDanger = Color(0xFFF85149)
    val DarkInfo = Color(0xFF2DD4BF)
    val DarkSuccessSurface = Color(0xFF12261E)
    val DarkWarningSurface = Color(0xFF272115)
    val DarkDangerSurface = Color(0xFF2D1618)
    val DarkInfoSurface = Color(0xFF102A3A)

    val LightColorScheme = lightColorScheme(
        primary = LightPrimary,
        onPrimary = LightOnPrimary,
        primaryContainer = LightInfoSurface,
        onPrimaryContainer = LightTextPrimary,
        secondary = LightTextSecondary,
        onSecondary = Color.White,
        secondaryContainer = LightSurfaceVariant,
        onSecondaryContainer = LightTextPrimary,
        tertiary = LightInfo,
        onTertiary = Color.White,
        background = LightBackground,
        onBackground = LightTextPrimary,
        surface = LightSurface,
        onSurface = LightTextPrimary,
        surfaceVariant = LightSurfaceVariant,
        onSurfaceVariant = LightTextSecondary,
        outline = LightOutline,
        error = LightDanger,
        onError = Color.White,
        errorContainer = LightDangerSurface,
        onErrorContainer = LightDanger,
    )

    val DarkColorScheme = darkColorScheme(
        primary = DarkPrimary,
        onPrimary = DarkOnPrimary,
        primaryContainer = DarkInfoSurface,
        onPrimaryContainer = DarkTextPrimary,
        secondary = DarkTextSecondary,
        onSecondary = DarkBackground,
        secondaryContainer = DarkSurfaceVariant,
        onSecondaryContainer = DarkTextPrimary,
        tertiary = DarkInfo,
        onTertiary = DarkBackground,
        background = DarkBackground,
        onBackground = DarkTextPrimary,
        surface = DarkSurface,
        onSurface = DarkTextPrimary,
        surfaceVariant = DarkSurfaceVariant,
        onSurfaceVariant = DarkTextSecondary,
        outline = DarkOutline,
        error = DarkDanger,
        onError = DarkBackground,
        errorContainer = DarkDangerSurface,
        onErrorContainer = DarkDanger,
    )

    val Typography = Typography(
        displaySmall = TextStyle(
            fontFamily = FontFamily.SansSerif,
            fontWeight = FontWeight.Bold,
            fontSize = 32.sp,
            lineHeight = 40.sp,
        ),
        headlineSmall = TextStyle(
            fontFamily = FontFamily.SansSerif,
            fontWeight = FontWeight.Bold,
            fontSize = 24.sp,
            lineHeight = 32.sp,
        ),
        titleLarge = TextStyle(
            fontFamily = FontFamily.SansSerif,
            fontWeight = FontWeight.Bold,
            fontSize = 20.sp,
            lineHeight = 28.sp,
        ),
        titleMedium = TextStyle(
            fontFamily = FontFamily.SansSerif,
            fontWeight = FontWeight.Medium,
            fontSize = 18.sp,
            lineHeight = 26.sp,
        ),
        bodyLarge = TextStyle(
            fontFamily = FontFamily.SansSerif,
            fontWeight = FontWeight.Normal,
            fontSize = 16.sp,
            lineHeight = 24.sp,
        ),
        bodySmall = TextStyle(
            fontFamily = FontFamily.SansSerif,
            fontWeight = FontWeight.Normal,
            fontSize = 14.sp,
            lineHeight = 20.sp,
        ),
        labelLarge = TextStyle(
            fontFamily = FontFamily.SansSerif,
            fontWeight = FontWeight.Medium,
            fontSize = 16.sp,
            lineHeight = 24.sp,
        ),
        labelMedium = TextStyle(
            fontFamily = FontFamily.SansSerif,
            fontWeight = FontWeight.Medium,
            fontSize = 14.sp,
            lineHeight = 20.sp,
        ),
    )

    val Shapes = Shapes(
        extraSmall = RoundedCornerShape(4.dp),
        small = RoundedCornerShape(4.dp),
        medium = RoundedCornerShape(8.dp),
        large = RoundedCornerShape(12.dp),
        extraLarge = RoundedCornerShape(16.dp),
    )
}

data class RawPosExtraColors(
    val success: Color,
    val onSuccess: Color,
    val successSurface: Color,
    val warning: Color,
    val onWarning: Color,
    val warningSurface: Color,
    val danger: Color,
    val onDanger: Color,
    val dangerSurface: Color,
    val info: Color,
    val onInfo: Color,
    val infoSurface: Color,
)

val LightExtraColors = RawPosExtraColors(
    success = RawPosTokens.LightSuccess,
    onSuccess = Color.White,
    successSurface = RawPosTokens.LightSuccessSurface,
    warning = RawPosTokens.LightWarning,
    onWarning = Color.White,
    warningSurface = RawPosTokens.LightWarningSurface,
    danger = RawPosTokens.LightDanger,
    onDanger = Color.White,
    dangerSurface = RawPosTokens.LightDangerSurface,
    info = RawPosTokens.LightInfo,
    onInfo = Color.White,
    infoSurface = RawPosTokens.LightInfoSurface,
)

val DarkExtraColors = RawPosExtraColors(
    success = RawPosTokens.DarkSuccess,
    onSuccess = RawPosTokens.DarkBackground,
    successSurface = RawPosTokens.DarkSuccessSurface,
    warning = RawPosTokens.DarkWarning,
    onWarning = RawPosTokens.DarkBackground,
    warningSurface = RawPosTokens.DarkWarningSurface,
    danger = RawPosTokens.DarkDanger,
    onDanger = RawPosTokens.DarkBackground,
    dangerSurface = RawPosTokens.DarkDangerSurface,
    info = RawPosTokens.DarkInfo,
    onInfo = RawPosTokens.DarkBackground,
    infoSurface = RawPosTokens.DarkInfoSurface,
)

val LocalRawPosExtraColors = staticCompositionLocalOf { LightExtraColors }

object Dimens {
    val ScreenPadding = 16.dp
    val SectionGap = 24.dp
    val CardGap = 12.dp
    val ControlHeight = 48.dp
    val ScannerControl = 64.dp
    val ViewfinderRadius = 12.dp
    val TargetWidth = 260.dp
    val TargetHeight = 160.dp
}
