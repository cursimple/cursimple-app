package com.x500x.cursimple.feature.schedule.sharing

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.x500x.cursimple.feature.plugin.ui.AppCircleIconButton
import com.x500x.cursimple.feature.plugin.ui.AppAtomicText
import com.x500x.cursimple.feature.plugin.ui.AppOutlinedButton

@Composable
fun QrScannerOverlay(onScanned: (String) -> Unit, onCancel: () -> Unit,
    hint: String, cameraFailure: String, retryLabel: String, backLabel: String, tagPrefix: String = "schedule") {
    var viewport by remember { mutableStateOf<Rect?>(null) }
    var frame by remember { mutableStateOf<Rect?>(null) }
    var cameraFailed by remember { mutableStateOf(false) }
    var retry by remember { mutableIntStateOf(0) }
    val scanRegion = frame?.let { f -> viewport?.takeIf { it.width > 0 && it.height > 0 }?.let { v ->
        QrScanRegion((f.left - v.left) / v.width, (f.top - v.top) / v.height,
            (f.right - v.left) / v.width, (f.bottom - v.top) / v.height)
    } }
    BoxWithConstraints(Modifier.fillMaxSize().background(Color.Black)
        .onGloballyPositioned { viewport = it.boundsInRoot() }.testTag("$tagPrefix-scanner")) {
        key(retry) {
            QrScannerView(onScanned = onScanned, scanRegion = scanRegion,
                onCameraError = { cameraFailed = true }, modifier = Modifier.fillMaxSize())
        }
        Canvas(Modifier.fillMaxSize()) {
            scanRegion?.let { r ->
                val l = r.left * size.width; val t = r.top * size.height
                val right = r.right * size.width; val bottom = r.bottom * size.height
                val shade = Color.Black.copy(alpha = 0.6f)
                drawRect(shade, size = Size(size.width, t))
                drawRect(shade, Offset(0f, bottom), Size(size.width, size.height - bottom))
                drawRect(shade, Offset(0f, t), Size(l, bottom - t))
                drawRect(shade, Offset(right, t), Size(size.width - right, bottom - t))
            }
        }
        val frameSize = minOf(320.dp, (maxWidth - 48.dp).coerceAtLeast(48.dp), maxHeight * 0.5f)
        Column(Modifier.align(Alignment.Center).safeDrawingPadding().padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Box(Modifier.size(frameSize).border(3.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(12.dp))
                .onGloballyPositioned { frame = it.boundsInRoot() }.testTag("$tagPrefix-scan-frame"))
            Text(hint, color = Color.White,
                textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.testTag("$tagPrefix-scan-hint"))
            if (cameraFailed) {
                Text(cameraFailure, color = Color.White, textAlign = TextAlign.Center)
                AppOutlinedButton(onClick = { cameraFailed = false; retry++ }) {
                    AppAtomicText(retryLabel, color = Color.White)
                }
            }
        }
        AppCircleIconButton(onClick = onCancel,
            modifier = Modifier.align(Alignment.TopStart).safeDrawingPadding().padding(16.dp)
                .background(Color.Black.copy(alpha = 0.6f), CircleShape)) {
            Icon(Icons.AutoMirrored.Rounded.ArrowBack, backLabel, tint = Color.White)
        }
    }
}
