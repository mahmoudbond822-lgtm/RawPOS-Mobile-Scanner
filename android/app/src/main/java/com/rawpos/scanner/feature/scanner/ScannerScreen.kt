package com.rawpos.scanner.feature.scanner

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.SystemClock
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.rawpos.scanner.R
import com.rawpos.scanner.core.barcode.DecodedBarcode
import com.rawpos.scanner.core.barcode.RejectionReason
import com.rawpos.scanner.core.camera.BarcodeAnalyzer
import com.rawpos.scanner.ui.components.StatusKind
import com.rawpos.scanner.ui.components.StatusPill
import com.rawpos.scanner.ui.theme.Dimens
import com.rawpos.scanner.ui.theme.RawPosTokens
import java.util.concurrent.Executors

/**
 * The scanner screen: live camera, one target window, and the last accepted barcode.
 *
 * The camera is bound only while this composable is in the composition, so it is released when the
 * cashier leaves the screen. No scan is sent anywhere: Phase 3 has no transport and no network.
 */
@Composable
fun ScannerScreen(
    state: ScannerUiState,
    onFrame: (List<DecodedBarcode>, Long) -> Unit,
    onTogglePause: () -> Unit,
    onTorchChanged: (Boolean) -> Unit,
    onCameraStarted: () -> Unit,
) {
    val context = LocalContext.current
    var granted by remember { mutableStateOf(context.hasCameraPermission()) }
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { allowed -> granted = allowed }

    LaunchedEffect(Unit) {
        if (!granted) {
            launcher.launch(Manifest.permission.CAMERA)
        }
    }

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
                text = stringResource(statusText(state)),
                kind = statusKind(state),
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
            if (granted) {
                CameraPreview(
                    paused = state.status == ScannerStatus.PAUSED,
                    torchOn = state.torchOn,
                    onFrame = onFrame,
                    onCameraStarted = onCameraStarted,
                )
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
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
                }
            } else {
                PermissionPanel(onRequest = { launcher.launch(Manifest.permission.CAMERA) })
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedButton(
                onClick = { onTorchChanged(!state.torchOn) },
                modifier = Modifier
                    .weight(1f)
                    .height(Dimens.ScannerControl),
            ) {
                Text(
                    text = stringResource(
                        if (state.torchOn) R.string.scanner_torch_on else R.string.scanner_torch,
                    ),
                    style = MaterialTheme.typography.labelMedium,
                    textAlign = TextAlign.Center,
                )
            }
            OutlinedButton(
                onClick = onTogglePause,
                modifier = Modifier
                    .weight(1f)
                    .height(Dimens.ScannerControl),
            ) {
                Text(
                    text = stringResource(
                        if (state.status == ScannerStatus.PAUSED) {
                            R.string.scanner_resume
                        } else {
                            R.string.scanner_pause
                        },
                    ),
                    style = MaterialTheme.typography.labelMedium,
                    textAlign = TextAlign.Center,
                )
            }
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
                val rejection = state.rejection
                if (rejection != null) {
                    Text(
                        text = stringResource(rejectionText(rejection)),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                Text(
                    text = stringResource(R.string.scanner_offline_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun PermissionPanel(onRequest: () -> Unit) {
    Column(
        modifier = Modifier.padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = stringResource(R.string.scanner_permission_title),
            style = MaterialTheme.typography.titleMedium,
            color = Color.White,
            textAlign = TextAlign.Center,
        )
        Text(
            text = stringResource(R.string.scanner_permission_body),
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White.copy(alpha = 0.8f),
            textAlign = TextAlign.Center,
        )
        Button(onClick = onRequest) {
            Text(stringResource(R.string.scanner_permission_action))
        }
    }
}

@Composable
private fun CameraPreview(
    paused: Boolean,
    torchOn: Boolean,
    onFrame: (List<DecodedBarcode>, Long) -> Unit,
    onCameraStarted: () -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val previewView = remember { PreviewView(context) }
    val currentOnFrame by rememberUpdatedState(onFrame)
    val currentPaused by rememberUpdatedState(paused)
    val currentOnStarted by rememberUpdatedState(onCameraStarted)
    var camera by remember { mutableStateOf<Camera?>(null) }
    val executor = remember { Executors.newSingleThreadExecutor() }
    val scanner = remember { BarcodeScanning.getClient(BarcodeAnalyzer.scannerOptions()) }

    DisposableEffect(lifecycleOwner) {
        val future = ProcessCameraProvider.getInstance(context)
        var provider: ProcessCameraProvider? = null
        val bind = Runnable {
            val resolved = future.get()
            provider = resolved
            val preview = Preview.Builder().build()
            preview.setSurfaceProvider(previewView.surfaceProvider)
            val analysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
            analysis.setAnalyzer(
                executor,
                BarcodeAnalyzer(
                    scanner = scanner,
                    paused = { currentPaused },
                    onDecoded = { values -> currentOnFrame(values, SystemClock.elapsedRealtime()) },
                ),
            )
            try {
                resolved.unbindAll()
                camera = resolved.bindToLifecycle(
                    lifecycleOwner,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    preview,
                    analysis,
                )
                currentOnStarted()
            } catch (_: IllegalArgumentException) {
                // No back camera on this device: the screen shows the empty stage rather than crashing.
            }
        }
        future.addListener(bind, ContextCompat.getMainExecutor(context))

        onDispose {
            provider?.unbindAll()
            executor.shutdown()
            scanner.close()
        }
    }

    AndroidView(
        factory = { previewView },
        modifier = Modifier.fillMaxSize(),
    )

    LaunchedEffect(torchOn, camera) {
        camera?.cameraControl?.enableTorch(torchOn)
    }
}

@StringRes
private fun statusText(state: ScannerUiState): Int {
    val rejection = state.rejection
    return when {
        rejection != null -> rejectionText(rejection)
        state.status == ScannerStatus.MULTIPLE_CODES -> R.string.scanner_status_multiple
        state.status == ScannerStatus.PAUSED -> R.string.scanner_status_paused
        state.status == ScannerStatus.SENT -> R.string.scanner_status_sent
        else -> R.string.scanner_status_ready
    }
}

private fun statusKind(state: ScannerUiState): StatusKind = when {
    state.rejection != null -> StatusKind.WARNING
    state.status == ScannerStatus.MULTIPLE_CODES -> StatusKind.WARNING
    state.status == ScannerStatus.SENT -> StatusKind.SUCCESS
    state.status == ScannerStatus.PAUSED -> StatusKind.INFO
    else -> StatusKind.NEUTRAL
}

@StringRes
private fun rejectionText(reason: RejectionReason): Int = when (reason) {
    RejectionReason.EMPTY -> R.string.scanner_reject_empty
    RejectionReason.NON_DIGITS -> R.string.scanner_reject_non_digits
    RejectionReason.TOO_LONG -> R.string.scanner_reject_too_long
    RejectionReason.FORMAT_DISABLED -> R.string.scanner_reject_disabled
}

private fun Context.hasCameraPermission(): Boolean =
    ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) ==
        PackageManager.PERMISSION_GRANTED
