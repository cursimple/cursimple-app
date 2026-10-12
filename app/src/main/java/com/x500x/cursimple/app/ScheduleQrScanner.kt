package com.x500x.cursimple.app

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.x500x.cursimple.R

@Composable
internal fun ScannerOverlay(onScanned: (String) -> Unit, onCancel: () -> Unit) =
    com.x500x.cursimple.feature.schedule.sharing.QrScannerOverlay(
        onScanned, onCancel, stringResource(R.string.ie_scanner_hint),
        stringResource(R.string.ie_camera_failed), stringResource(R.string.ie_retry), stringResource(R.string.ie_back))
