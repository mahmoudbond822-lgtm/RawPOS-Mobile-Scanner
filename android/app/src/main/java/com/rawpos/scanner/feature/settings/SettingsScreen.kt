package com.rawpos.scanner.feature.settings

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.rawpos.scanner.R
import com.rawpos.scanner.core.settings.LanguageMode
import com.rawpos.scanner.core.settings.ThemeMode
import com.rawpos.scanner.ui.components.InfoRow
import com.rawpos.scanner.ui.components.PhaseOneNotice
import com.rawpos.scanner.ui.theme.Dimens

@Composable
fun SettingsScreen(
    state: SettingsUiState,
    onLanguageSelected: (LanguageMode) -> Unit,
    onThemeSelected: (ThemeMode) -> Unit,
    onOpenDiagnostics: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(Dimens.ScreenPadding)
            .testTag("settings_screen"),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        SectionTitle(stringResource(R.string.settings_appearance))

        Text(
            text = stringResource(R.string.settings_language),
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            text = stringResource(R.string.settings_language_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            LanguageMode.entries.forEach { mode ->
                FilterChip(
                    selected = state.language == mode,
                    onClick = { onLanguageSelected(mode) },
                    label = { Text(languageLabel(mode)) },
                )
            }
        }

        Text(
            text = stringResource(R.string.settings_theme),
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            text = stringResource(R.string.settings_theme_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ThemeMode.entries.forEach { mode ->
                FilterChip(
                    selected = state.theme == mode,
                    onClick = { onThemeSelected(mode) },
                    label = { Text(themeLabel(mode)) },
                )
            }
        }

        HorizontalDivider()

        SectionTitle(stringResource(R.string.settings_support))
        InfoRow(
            label = stringResource(R.string.settings_version),
            value = state.versionName,
            forceLtr = true,
        )
        InfoRow(
            label = stringResource(R.string.diagnostics_build_type),
            value = state.buildType,
            forceLtr = true,
        )
        OutlinedButton(
            onClick = onOpenDiagnostics,
            modifier = Modifier
                .fillMaxWidth()
                .height(Dimens.ControlHeight),
        ) {
            Text(text = stringResource(R.string.settings_open_diagnostics))
        }

        PhaseOneNotice(
            title = stringResource(R.string.settings_about),
            body = stringResource(R.string.settings_phase1_note),
        )
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleLarge,
        color = MaterialTheme.colorScheme.onBackground,
    )
}

@Composable
private fun languageLabel(mode: LanguageMode): String = stringResource(
    when (mode) {
        LanguageMode.SYSTEM -> R.string.settings_language_system
        LanguageMode.ARABIC -> R.string.settings_language_arabic
        LanguageMode.ENGLISH -> R.string.settings_language_english
    },
)

@Composable
private fun themeLabel(mode: ThemeMode): String = stringResource(
    when (mode) {
        ThemeMode.SYSTEM -> R.string.settings_theme_system
        ThemeMode.LIGHT -> R.string.settings_theme_light
        ThemeMode.DARK -> R.string.settings_theme_dark
    },
)
