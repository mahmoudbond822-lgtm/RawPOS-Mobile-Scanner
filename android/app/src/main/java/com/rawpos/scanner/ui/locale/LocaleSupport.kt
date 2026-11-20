package com.rawpos.scanner.ui.locale

import android.content.Context
import android.content.res.Configuration
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import com.rawpos.scanner.core.settings.LanguageMode
import java.util.Locale

object LocaleSupport {

    /**
     * Applies an explicit app language without AppCompat. A SYSTEM preference keeps the
     * platform configuration; an explicit preference wraps the base context with that locale.
     */
    fun wrap(base: Context, mode: LanguageMode): Context {
        val tag = mode.languageTag
        if (tag == null) {
            val locales = base.resources.configuration.locales
            if (!locales.isEmpty) {
                Locale.setDefault(locales[0])
            }
            return base
        }
        val locale = Locale.forLanguageTag(tag)
        Locale.setDefault(locale)
        val configuration = Configuration(base.resources.configuration).apply {
            setLocale(locale)
            setLayoutDirection(locale)
        }
        return base.createConfigurationContext(configuration)
    }

    @Composable
    fun isRtl(mode: LanguageMode): Boolean {
        val configuration = LocalConfiguration.current
        return when (mode) {
            LanguageMode.ARABIC -> true
            LanguageMode.ENGLISH -> false
            LanguageMode.SYSTEM -> configuration.layoutDirection == View.LAYOUT_DIRECTION_RTL
        }
    }
}
