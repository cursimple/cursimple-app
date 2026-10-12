package com.x500x.cursimple.feature.schedule.sharing

import android.util.Size
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.UseCaseGroup
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.view.doOnLayout
import androidx.lifecycle.compose.LocalLifecycleOwner
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

@Composable
fun QrScannerView(
    onScanned: (String) -> Unit,
    scanRegion: QrScanRegion?,
    onCameraError: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val previewView = remember(context) {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        }
    }
    var viewSize by remember { mutableStateOf(IntSize.Zero) }
    val region = remember { AtomicReference<QrScanRegion?>() }
    val scanned by rememberUpdatedState(onScanned)
    val cameraError by rememberUpdatedState(onCameraError)
    SideEffect { region.set(scanRegion) }

    DisposableEffect(lifecycleOwner, viewSize) {
        val stopped = AtomicBoolean(false)
        val handled = AtomicBoolean(false)
        val executor = Executors.newSingleThreadExecutor()
        val mainExecutor = ContextCompat.getMainExecutor(context)
        val future = ProcessCameraProvider.getInstance(context)
        val preview = Preview.Builder().build().also { it.surfaceProvider = previewView.surfaceProvider }
        val analysis = ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
            .setResolutionSelector(ResolutionSelector.Builder().setResolutionStrategy(
                ResolutionStrategy(Size(1280, 720), ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER),
            ).build())
            .build()
        if (viewSize.width > 0 && viewSize.height > 0) {
            previewView.doOnLayout {
              future.addListener({
                if (!stopped.get()) runCatching {
                    val provider = future.get()
                    // Match preview and analysis sensor crops instead of guessing from resolution.
                    val rotation = checkNotNull(previewView.display).rotation
                    preview.targetRotation = rotation
                    analysis.targetRotation = rotation
                    val viewport = checkNotNull(previewView.viewPort)
                    analysis.setAnalyzer(executor) { proxy ->
                        val text = try {
                            if (stopped.get() || handled.get()) null else region.get()?.let { runCatching { decodeYuv(proxy, it) }.getOrNull() }
                        } finally {
                            proxy.close()
                        }
                        if (text != null && handled.compareAndSet(false, true)) {
                            mainExecutor.execute { if (!stopped.get()) scanned(text) }
                        }
                    }
                    provider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA,
                        UseCaseGroup.Builder().setViewPort(viewport).addUseCase(preview).addUseCase(analysis).build())
                }.onFailure { if (!stopped.get()) cameraError() }
              }, mainExecutor)
            }
        }
        onDispose {
            stopped.set(true)
            analysis.clearAnalyzer()
            if (future.isDone) runCatching { future.get().unbind(preview, analysis) }
            executor.shutdown()
        }
    }

    AndroidView(factory = { previewView }, modifier = modifier.onSizeChanged { viewSize = it })
}

private fun decodeYuv(proxy: ImageProxy, region: QrScanRegion): String? {
    val crop = proxy.cropRect
    val pixels = region.inCameraCrop(QrPixelRegion(crop.left, crop.top, crop.right, crop.bottom),
        proxy.imageInfo.rotationDegrees) ?: return null
    val plane = proxy.planes.firstOrNull() ?: return null
    val buffer = plane.buffer.duplicate()
    val offset = buffer.position()
    val luminance = ByteArray(pixels.width * pixels.height)
    for (y in 0 until pixels.height) {
        val row = offset + (pixels.top + y) * plane.rowStride + pixels.left * plane.pixelStride
        for (x in 0 until pixels.width) {
            val index = row + x * plane.pixelStride
            if (index >= buffer.limit()) return null
            luminance[y * pixels.width + x] = buffer.get(index)
        }
    }
    return decodeQrLuminance(luminance, pixels.width, pixels.height)
}
