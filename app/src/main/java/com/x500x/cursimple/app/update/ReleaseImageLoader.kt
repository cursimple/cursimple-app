package com.x500x.cursimple.app.update

import android.content.Context
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import com.x500x.cursimple.app.download.DownloadPurpose
import com.x500x.cursimple.app.download.DownloadRequest
import com.x500x.cursimple.app.download.MirrorDownloadResult
import com.x500x.cursimple.app.download.MirrorDownloader
import com.x500x.cursimple.app.download.SharedPrefsMirrorPreferenceStore
import com.x500x.cursimple.app.download.mirrorDownloaderLabels
import com.x500x.cursimple.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest

/**
 * 更新公告里的图。
 *
 * 图放在仓库里、按 raw.githubusercontent 地址引用，GitHub 网页上能直接看；
 * App 里走和检查更新同一套镜像池，国内直连 GitHub 取不到时换镜像。下过的存进缓存目录，
 * 同一份公告再打开不重下。[localDir] 非空时是本地预览：图按文件名先从这个目录里找。
 */
class ReleaseImageLoader(
    private val cacheDir: File,
    private val downloader: MirrorDownloader,
    private val localDir: File? = null,
) {
    private val memory = LruCache<String, ImageBitmap>(MEMORY_CACHE_SIZE)

    suspend fun load(url: String): ImageBitmap? = withContext(Dispatchers.IO) {
        memory.get(url)?.let { return@withContext it }
        val bytes = localBytes(url) ?: cachedBytes(url) ?: downloadBytes(url) ?: return@withContext null
        decode(bytes)?.also { memory.put(url, it) }
    }

    private fun localBytes(url: String): ByteArray? {
        val dir = localDir ?: return null
        val name = url.substringAfterLast('/').substringBefore('?')
        return File(dir, name).takeIf { it.isFile }?.readBytes()
    }

    private fun cacheFile(url: String): File {
        val digest = MessageDigest.getInstance("SHA-1").digest(url.toByteArray())
        return File(cacheDir, digest.joinToString("") { "%02x".format(it) })
    }

    private fun cachedBytes(url: String): ByteArray? =
        cacheFile(url).takeIf { it.isFile && it.length() > 0 }?.readBytes()

    private suspend fun downloadBytes(url: String): ByteArray? {
        val result = downloader.downloadBytes(releaseImageRequest(url)) { bytes ->
            // 镜像偶尔回一张错误页，解不出图就当这个源失败，换下一个
            require(boundsOf(bytes) != null) { "not an image" }
        }
        val bytes = (result as? MirrorDownloadResult.Success)?.value ?: return null
        runCatching {
            cacheDir.mkdirs()
            cacheFile(url).writeBytes(bytes)
        }
        return bytes
    }

    private fun decode(bytes: ByteArray): ImageBitmap? {
        val (width, _) = boundsOf(bytes) ?: return null
        // 公告里最宽也就占满一个弹窗，按 1080 宽取样，省得整张原图进内存
        var sample = 1
        while (width / (sample * 2) >= MAX_DECODE_WIDTH) sample *= 2
        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)?.asImageBitmap()
    }

    private fun boundsOnly() = BitmapFactory.Options().apply { inJustDecodeBounds = true }

    private fun boundsOf(bytes: ByteArray): Pair<Int, Int>? {
        val options = boundsOnly()
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
        return (options.outWidth to options.outHeight).takeIf { it.first > 0 && it.second > 0 }
    }

    private companion object {
        const val MEMORY_CACHE_SIZE = 8
        const val MAX_DECODE_WIDTH = 1080
    }
}

/**
 * 图片地址对应的下载请求。
 *
 * 仓库文件（raw.githubusercontent.com/<owner>/<repo>/<ref>/<path>）拆出仓库、分支与路径交给镜像池；
 * 其他地址原样直连。
 */
internal fun releaseImageRequest(url: String): DownloadRequest {
    val match = RAW_GITHUB_FILE.matchEntire(url)
    return if (match != null) {
        val (owner, repo, ref, path) = match.destructured
        DownloadRequest(
            purpose = DownloadPurpose.GithubRepoFile,
            url = url,
            repository = "$owner/$repo",
            ref = ref,
            path = path,
        )
    } else {
        DownloadRequest(purpose = DownloadPurpose.DirectUrl, url = url)
    }
}

private val RAW_GITHUB_FILE = Regex("^https://raw\\.githubusercontent\\.com/([^/]+)/([^/]+)/([^/]+)/(.+)$")

/** 界面里用的加载器；[localDir] 见 [ReleaseImageLoader]。 */
@Composable
fun rememberReleaseImageLoader(localDir: File? = null): ReleaseImageLoader {
    val context = LocalContext.current.applicationContext
    return remember(localDir) { releaseImageLoader(context, localDir) }
}

private fun releaseImageLoader(context: Context, localDir: File?) = ReleaseImageLoader(
    cacheDir = File(context.cacheDir, "release-images"),
    downloader = MirrorDownloader(
        labels = context.mirrorDownloaderLabels(),
        userAgent = "CurSimple/${BuildConfig.VERSION_NAME}",
        preferenceStore = SharedPrefsMirrorPreferenceStore(context),
    ),
    localDir = localDir,
)
