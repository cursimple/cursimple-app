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
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap

/**
 * Cache announcement images through the mirror pool. [localDir] and [localAssetBytes] restrict
 * preview loading to local content.
 */
class ReleaseImageLoader(
    private val cacheDir: File,
    private val downloader: MirrorDownloader,
    private val localDir: File? = null,
    private val localAssetBytes: ((String) -> ByteArray?)? = null,
) {
    private val memory = LruCache<String, ImageBitmap>(MEMORY_CACHE_SIZE)

    suspend fun load(url: String): ImageBitmap? = withContext(Dispatchers.IO) {
        memory.get(url)?.let { return@withContext it }
        val bytes = if (localDir != null || localAssetBytes != null) {
            // Local previews never fetch missing images remotely.
            localBytes(url) ?: localAssetBytes?.invoke(imageFileName(url))
        } else {
            cachedBytes(url) ?: downloadOnce(url)
        } ?: return@withContext null
        decode(bytes)?.also { memory.put(url, it) }
    }

    /**
     * Prefetch encoded bytes into disk cache without decoding; cached entries return
     * immediately.
     */
    suspend fun prefetch(url: String): Boolean = withContext(Dispatchers.IO) {
        if (localDir != null || localAssetBytes != null) return@withContext false
        cachedBytes(url) != null || downloadOnce(url) != null
    }

    /** Share in-flight downloads between prefetch and visible image loaders. */
    private suspend fun downloadOnce(url: String): ByteArray? {
        val mine = CompletableDeferred<ByteArray?>()
        inFlight.putIfAbsent(url, mine)?.let { return it.await() }
        return try {
            downloadBytes(url).also { mine.complete(it) }
        } catch (error: Throwable) {
            mine.complete(null)
            throw error
        } finally {
            inFlight.remove(url, mine)
        }
    }

    private fun localBytes(url: String): ByteArray? {
        val dir = localDir ?: return null
        val name = imageFileName(url)
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
            // Treat undecodable mirror responses as failures and try another source.
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
        val inFlight = ConcurrentHashMap<String, CompletableDeferred<ByteArray?>>()
        const val MEMORY_CACHE_SIZE = 8
        const val MAX_DECODE_WIDTH = 1080
    }
}

/** Convert repository raw URLs into mirror requests; other URLs remain direct. */
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

@Composable
fun rememberReleaseImageLoader(localDir: File? = null, localAssetDir: String? = null): ReleaseImageLoader {
    val context = LocalContext.current.applicationContext
    return remember(localDir, localAssetDir) { releaseImageLoader(context, localDir, localAssetDir) }
}

internal fun releaseImageLoader(context: Context, localDir: File? = null, localAssetDir: String? = null) = ReleaseImageLoader(
    cacheDir = File(context.cacheDir, "release-images"),
    downloader = MirrorDownloader(
        labels = context.mirrorDownloaderLabels(),
        userAgent = "CurSimple/${BuildConfig.VERSION_NAME}",
        preferenceStore = SharedPrefsMirrorPreferenceStore(context),
    ),
    localDir = localDir,
    localAssetBytes = localAssetDir?.let { dir ->
        { name ->
            try {
                context.assets.open("$dir/$name").use { it.readBytes() }
            } catch (_: java.io.IOException) {
                null
            }
        }
    },
)

private fun imageFileName(url: String): String =
    url.substringAfterLast('/').substringBefore('?').substringBefore('#')
