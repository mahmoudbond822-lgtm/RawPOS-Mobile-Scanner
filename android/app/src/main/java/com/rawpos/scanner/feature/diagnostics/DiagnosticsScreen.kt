package com.rawpos.scanner.feature.diagnostics

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.rawpos.scanner.R
import com.rawpos.scanner.core.AppInfo
import com.rawpos.scanner.ui.components.InfoRow
import com.rawpos.scanner.ui.components.PhaseOneNotice
import com.rawpos.scanner.ui.components.StatusKind
import com.rawpos.scanner.ui.components.StatusPill
import com.rawpos.scanner.ui.theme.Dimens

@Composable
fun DiagnosticsScreen(info: AppInfo) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(Dimens.ScreenPadding)
            .testTag("diagnostics_screen"),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = stringResource(R.string.diagnostics_environment),
            style = MaterialTheme.typography.titleLarge,
        )
        StatusPill(
            text = stringResource(R.string.phase1_badge),
            kind = StatusKind.INFO,
        )

        InfoRow(stringResource(R.string.diagnostics_package), info.packageName, forceLtr = true)
        InfoRow(stringResource(R.string.diagnostics_version), info.versionName, forceLtr = true)
        InfoRow(stringResource(R.string.diagnostics_build_type), info.buildType, forceLtr = true)
        InfoRow(stringResource(R.string.diagnostics_min_sdk), info.minSdk.toString(), forceLtr = true)
        InfoRow(stringResource(R.string.diagnostics_target_sdk), info.targetSdk.toString(), forceLtr = true)
        InfoRow(stringResource(R.string.diagnostics_phase), stringResource(R.string.phase1_badge))

        HorizontalDivider()

        Text(
            text = stringResource(R.string.diagnostics_no_camera),
            style = MaterialTheme.typography.bodySmall,
        )
        Text(
            text = stringResource(R.string.diagnostics_no_network),
            style = MaterialTheme.typography.bodySmall,
        )

        PhaseOneNotice(
            title = stringResource(R.string.phase1_notice_title),
            body = stringResource(R.string.diagnostics_phase1_note),
        )
    }
}
