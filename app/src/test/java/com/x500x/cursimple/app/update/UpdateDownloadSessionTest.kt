package com.x500x.cursimple.app.update

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.util.concurrent.atomic.AtomicInteger

class UpdateDownloadSessionTest {
    @get:Rule val folder = TemporaryFolder()

    private fun info(code: Int) = AppUpdateInfo(
        versionCode = code, versionName = "0.0.$code", tagName = "v0.0.$code", releaseUrl = "", releaseNotes = "",
        asset = AppUpdateAsset("arm64-v8a", "app.apk", "00", "https://example.com/app.apk"), candidates = emptyList(),
    )

    private class Fixture(val folderFile: () -> java.io.File) {
        val calls = AtomicInteger()
        var gate = CompletableDeferred<AppUpdateDownloadResult>()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val session = UpdateDownloadSession(scope) { _, onProgress ->
            calls.incrementAndGet()
            onProgress(512L, 1024L)
            gate.await()
        }
    }

    @Test
    fun `the download outlives the screen that started it`() = runBlocking {
        val f = Fixture { folder.newFile() }
        val screen = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        screen.launch { f.session.start(info(40)) }.join()
        screen.cancel()
        val file = folder.newFile("app.apk")
        f.gate.complete(AppUpdateDownloadResult.Success(file, "mirror"))

        val done = withTimeout(2_000) { f.session.state.first { it is UpdateDownloadState.Done } }
        assertEquals(UpdateDownloadState.Done(info(40), file, "mirror"), done)
        f.scope.cancel()
    }

    @Test
    fun `asking again for the same version joins the running download with its progress`() = runBlocking {
        val f = Fixture { folder.newFile() }
        assertTrue(f.session.start(info(40)))
        val running = withTimeout(2_000) {
            f.session.state.first { it is UpdateDownloadState.Downloading && it.progress.downloadedBytes == 512L }
        }
        assertFalse("同一版本不应重新下载", f.session.start(info(40)))
        assertEquals(1, f.calls.get())
        assertEquals(AppUpdateDownloadProgress(512L, 1024L), (running as UpdateDownloadState.Downloading).progress)
        f.scope.cancel()
    }

    @Test
    fun `a finished file is reused and a failure can be retried`() = runBlocking {
        val f = Fixture { folder.newFile() }
        val file = folder.newFile("app.apk")
        f.session.start(info(40))
        f.gate.complete(AppUpdateDownloadResult.Success(file, "mirror"))
        withTimeout(2_000) { f.session.state.first { it is UpdateDownloadState.Done } }
        assertFalse("已下载完成的同版本不重复下载", f.session.start(info(40)))

        f.gate = CompletableDeferred()
        assertTrue("新版本重新下载", f.session.start(info(41)))
        f.gate.complete(AppUpdateDownloadResult.Failure(UpdateStatusReason.CheckRetry))
        withTimeout(2_000) { f.session.state.first { it is UpdateDownloadState.Failed } }
        f.gate = CompletableDeferred()
        assertTrue("失败后可以重试", f.session.start(info(41)))
        withTimeout(2_000) { f.session.state.first { it is UpdateDownloadState.Downloading && it.progress.downloadedBytes == 512L } }
        assertEquals(3, f.calls.get())
        f.scope.cancel()
    }

    @Test
    fun `only one waiting screen gets to open the installer`() = runBlocking {
        val f = Fixture { folder.newFile() }
        val file = folder.newFile("app.apk")
        assertFalse("还没下载完不能安装", f.session.claimInstall())
        f.session.start(info(40))
        f.gate.complete(AppUpdateDownloadResult.Success(file, "mirror"))
        withTimeout(2_000) { f.session.state.first { it is UpdateDownloadState.Done } }
        assertTrue(f.session.claimInstall())
        assertFalse("关于页和更新弹窗不能各弹一次安装", f.session.claimInstall())

        f.gate = CompletableDeferred()
        f.session.start(info(41))
        f.gate.complete(AppUpdateDownloadResult.Success(file, "mirror"))
        withTimeout(2_000) { f.session.state.first { it is UpdateDownloadState.Done && it.info.versionCode == 41 } }
        assertTrue("新的下载重新允许安装一次", f.session.claimInstall())
        f.scope.cancel()
    }
}
