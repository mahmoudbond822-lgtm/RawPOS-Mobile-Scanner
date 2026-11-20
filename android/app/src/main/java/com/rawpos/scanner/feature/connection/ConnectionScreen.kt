package com.rawpos.scanner.feature.connection

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.rawpos.scanner.R
import com.rawpos.scanner.ui.components.PhaseOneNotice
import com.rawpos.scanner.ui.components.StatusKind
import com.rawpos.scanner.ui.components.StatusPill
import com.rawpos.scanner.ui.theme.Dimens
import com.rawpos.scanner.ui.theme.RawPosTokens

@Composable
fun ConnectionScreen(
    state: ConnectionUiState,
    onPreviewScanner: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(Dimens.ScreenPadding)
            .testTag("connection_screen"),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.connection_title),
                style = MaterialTheme.typography.headlineSmall,
            )
            StatusPill(
                text = stringResource(R.string.phase1_badge),
                kind = StatusKind.INFO,
            )
        }

        Text(
            text = stringResource(R.string.connection_subtitle),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(4f / 3f)
                .clip(RoundedCornerShape(Dimens.ViewfinderRadius))
                .background(RawPosTokens.ScannerStage)
                .border(
                    width = 2.dp,
                    color = MaterialTheme.colorScheme.outline,
                    shape = RoundedCornerShape(Dimens.ViewfinderRadius),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(24.dp),
            ) {
                Text(
                    text = stringResource(R.string.connection_viewfinder_placeholder),
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                    textAlign = TextAlign.Center,
                )
                Text(
                    text = stringResource(R.string.connection_status_phase1),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.72f),
                    textAlign = TextAlign.Center,
                )
            }
        }

        Text(
            text = stringResource(R.string.connection_status_title),
            style = MaterialTheme.typography.titleMedium,
        )
        StatusPill(
            text = stringResource(R.string.connection_status_phase1),
            kind = StatusKind.WARNING,
        )

        Button(
            onClick = {},
            enabled = state.pairingEnabled,
            modifier = Modifier
                .fillMaxWidth()
                .height(Dimens.ControlHeight),
        ) {
            Text(text = stringResource(R.string.connection_action_pair))
        }

        OutlinedButton(
            onClick = onPreviewScanner,
            modifier = Modifier
                .fillMaxWidth()
                .height(Dimens.ControlHeight),
        ) {
            Text(text = stringResource(R.string.connection_action_preview))
        }

        PhaseOneNotice(
            title = stringResource(R.string.phase1_notice_title),
            body = stringResource(R.string.phase1_notice_body),
        )
    }
}
