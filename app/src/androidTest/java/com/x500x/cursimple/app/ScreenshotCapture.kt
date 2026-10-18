package com.x500x.cursimple.app

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File

/**
 * Device screenshot for QA captures.
 *
 * `UiAutomation.takeScreenshot()` returns null on some emulator images even with drawing enabled,
 * so fall back to `screencap` through the shell and decode its PNG output.
 */
object ScreenshotCapture {
    fun take(): Bitmap {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.waitForIdleSync()
        Thread.sleep(300)
        instrumentation.uiAutomation.takeScreenshot()?.let { return it }
        return screencap()
    }

    fun save(bitmap: Bitmap, file: File) {
        file.parentFile?.mkdirs()
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }

    private fun screencap(): Bitmap {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val descriptor = instrumentation.uiAutomation.executeShellCommand("screencap -p")
        val bytes = descriptor.fileDescriptor.let { fd ->
            java.io.FileInputStream(fd).use { it.readBytes() }
        }
        descriptor.close()
        return checkNotNull(BitmapFactory.decodeByteArray(bytes, 0, bytes.size)) {
            "screencap returned ${bytes.size} bytes that could not be decoded"
        }
    }
}
