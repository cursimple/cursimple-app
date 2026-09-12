package com.x500x.cursimple.app.util

import android.util.Size
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.common.HybridBinarizer
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

@Composable
fun QrScannerView(
    onScanned: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val previewView = remember { PreviewView(context) }
    val handled = remember { AtomicBoolean(false) }
    val analyzerExecutor = remember { Executors.newSingleThreadExecutor() }
    val reader = remember {
        MultiFormatReader().apply {
            setHints(
                mapOf(
                    DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE),
                    DecodeHintType.TRY_HARDER to true,
                    DecodeHintType.CHARACTER_SET to "UTF-8",
                ),
            )
        }
    }

    DisposableEffect(lifecycleOwner) {
        val providerFuture = ProcessCameraProvider.getInstance(context)
        val mainExecutor = ContextCompat.getMainExecutor(context)
        providerFuture.addListener({
            val provider = providerFuture.get()
            val preview = Preview.Builder().build().also {
                it.surfaceProvider = previewView.surfaceProvider
            }
            val resolutionSelector = ResolutionSelector.Builder()
                .setResolutionStrategy(
                    ResolutionStrategy(
                        Size(1280, 720),
                        ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER,
                    ),
                )
                .build()
            val analysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
                .setResolutionSelector(resolutionSelector)
                .build()
            analysis.setAnalyzer(analyzerExecutor) { proxy ->
                if (handled.get()) {
                    proxy.close()
                    return@setAnalyzer
                }
                val text = decodeYuv(proxy, reader)
                proxy.close()
                if (text != null && handled.compareAndSet(false, true)) {
                    mainExecutor.execute { onScanned(text) }
                }
            }
            runCatching {
                provider.unbindAll()
                provider.bindToLifecycle(
                    lifecycleOwner,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    preview,
                    analysis,
                )
            }
        }, mainExecutor)

        onDispose {
            runCatching { providerFuture.get().unbindAll() }
            analyzerExecutor.shutdown()
        }
    }

    Box(modifier = modifier.background(Color.Black)) {
        AndroidView(
            factory = { previewView },
            modifier = Modifier.fillMaxSize(),
        )
    }
}

private fun decodeYuv(proxy: ImageProxy, reader: MultiFormatReader): String? {
    val plane = proxy.planes.firstOrNull() ?: return null
    val buffer = plane.buffer
    val data = ByteArray(buffer.remaining()).also { buffer.get(it) }
    val width = proxy.width
    val height = proxy.height
    val rowStride = plane.rowStride
    val pixelStride = plane.pixelStride
    // 很多设备的 Y 平面 rowStride > width（行填充），若直接把 width 当行宽，每行都会错位、
    // 相机永远扫不出二维码。无填充时走原快路径，有填充/像素跨距时逐行紧凑拷贝出 width×height 的亮度
    val luminance = if (pixelStride == 1 && rowStride == width) {
        data
    } else {
        ByteArray(width * height).also { out ->
            var outPos = 0
            for (y in 0 until height) {
                var i = y * rowStride
                var x = 0
                while (x < width && i < data.size) {
                    out[outPos + x] = data[i]
                    x++
                    i += pixelStride
                }
                outPos += width
            }
        }
    }
    val source = PlanarYUVLuminanceSource(
        luminance,
        width,
        height,
        0,
        0,
        width,
        height,
        false,
    )
    val binary = BinaryBitmap(HybridBinarizer(source))
    return runCatching { reader.decodeWithState(binary).text }.getOrNull()
        ?: runCatching {
            val rotated = source.invert()
            reader.decodeWithState(BinaryBitmap(HybridBinarizer(rotated))).text
        }.getOrNull()
}
