package com.rawpos.scanner.feature.scanner

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
fun ScannerScreen(state: ScannerUiState) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(Dimens.ScreenPadding)
            .testTag("scanner_screen"),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.scanner_title),
                style = MaterialTheme.typography.headlineSmall,
            )
            StatusPill(
                text = stringResource(R.string.scanner_status_phase1),
                kind = StatusKind.WARNING,
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(360.dp)
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
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(Dimens.TargetWidth, Dimens.TargetHeight)
                        .border(
                            width = 2.dp,
                            color = Color.White,
                            shape = RoundedCornerShape(8.dp),
                        ),
                )
                Text(
                    text = stringResource(R.string.scanner_target_hint),
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color.White,
                    textAlign = TextAlign.Center,
                )
                Text(
                    text = stringResource(R.string.scanner_viewfinder_placeholder),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.72f),
                    textAlign = TextAlign.Center,
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ScannerControlButton(
                label = stringResource(R.string.scanner_torch),
                modifier = Modifier.weight(1f),
            )
            ScannerControlButton(
                label = stringResource(R.string.scanner_pause),
                modifier = Modifier.weight(1f),
            )
            ScannerControlButton(
                label = stringResource(R.string.scanner_zoom),
                modifier = Modifier.weight(1f),
            )
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurface,
            ),
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = stringResource(R.string.scanner_last_code_label),
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = state.lastCode ?: stringResource(R.string.scanner_last_code_empty),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                StatusPill(
                    text = stringResource(R.string.scanner_status_idle),
                    kind = StatusKind.NEUTRAL,
                )
            }
        }

        PhaseOneNotice(
            title = stringResource(R.string.phase1_notice_title),
            body = stringResource(R.string.scanner_phase1_note),
        )
    }
}

@Composable
private fun ScannerControlButton(
    label: String,
    modifier: Modifier = Modifier,
) {
    OutlinedButton(
        onClick = {},
        enabled = false,
        modifier = modifier.height(Dimens.ScannerControl),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            textAlign = TextAlign.Center,
        )
    }
}
