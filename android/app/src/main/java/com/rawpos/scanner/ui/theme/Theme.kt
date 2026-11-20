package com.rawpos.scanner.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.unit.LayoutDirection
import com.rawpos.scanner.core.settings.LanguageMode
import com.rawpos.scanner.core.settings.ThemeMode
import com.rawpos.scanner.ui.locale.LocaleSupport

@Composable
fun RawPosTheme(
    themeMode: ThemeMode,
    language: LanguageMode,
    content: @Composable () -> Unit,
) {
    val dark = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val colors = if (dark) RawPosTokens.DarkColorScheme else RawPosTokens.LightColorScheme
    val extras = if (dark) DarkExtraColors else LightExtraColors
    val direction = if (LocaleSupport.isRtl(language)) LayoutDirection.Rtl else LayoutDirection.Ltr

    CompositionLocalProvider(
        LocalRawPosExtraColors provides extras,
        androidx.compose.ui.platform.LocalLayoutDirection provides direction,
    ) {
        MaterialTheme(
            colorScheme = colors,
            typography = RawPosTokens.Typography,
            shapes = RawPosTokens.Shapes,
            content = content,
        )
    }
}
