package com.x500x.cursimple.app.update

import android.content.Context
import android.net.ConnectivityManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.io.File

/**
 * Tag-keyed Markdown cache survives app upgrades; cache eviction falls back to network loading.
 */
class ReleaseNotesCache(private val dir: File) {
    fun read(tag: String): String? =
        fileFor(tag)?.takeIf { it.isFile && it.length() > 0L }?.let { runCatching { it.readText() }.getOrNull() }

    fun write(tag: String, notes: String) {
        val target = fileFor(tag) ?: return
        if (notes.isBlank()) return
        runCatching {
            dir.mkdirs()
            val temp = File(dir, "${target.name}.tmp")
            temp.writeText(notes)
            if (!temp.renameTo(target)) {
                temp.delete()
                return
            }
            prune()
        }
    }

    /** Retain only recent announcement versions. */
    private fun prune() {
        dir.listFiles { file -> file.name.endsWith(".md") }
            ?.sortedByDescending { it.lastModified() }
            ?.drop(KEEP_VERSIONS)
            ?.forEach { it.delete() }
    }

    private fun fileFor(tag: String): File? {
        val name = tag.trim()
        // Validate remote tag characters before deriving cache paths.
        if (!SAFE_TAG.matches(name) || name.startsWith(".")) return null
        return File(dir, "$name.md")
    }

    private companion object {
        val SAFE_TAG = Regex("[A-Za-z0-9._-]{1,64}")
        const val KEEP_VERSIONS = 4
    }
}

/** Unique image URLs in document order. */
fun releaseImageUrls(markdown: String): List<String> =
    parseReleaseNotes(markdown)
        .filterIsInstance<ReleaseNoteBlock.Gallery>()
        .flatMap { gallery -> gallery.images.map { it.url } }
        .distinct()

/** Best-effort notes and image prefetch; metered networks cache text only. */
class ReleasePrefetcher(
    private val cache: ReleaseNotesCache,
    private val prefetchImage: suspend (String) -> Boolean,
    private val fetchNotes: suspend (String) -> String?,
    private val imagesAllowed: () -> Boolean,
) {

    suspend fun notes(tag: String): String? =
        cache.read(tag) ?: fetchNotes(tag)?.also { cache.write(tag, it) }

    /** Reuse [knownNotes] from the update check to avoid another request. */
    suspend fun prefetch(tag: String, knownNotes: String?) {
        try {
            val text = knownNotes?.takeIf { it.isNotBlank() }
                ?.also { if (cache.read(tag) != it) cache.write(tag, it) }
                ?: notes(tag)
                ?: return
            if (imagesAllowed()) prefetchImages(text)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            // Prefetch failure leaves ordinary on-demand loading available.
        }
    }

    suspend fun prefetchImages(markdown: String) {
        val urls = releaseImageUrls(markdown)
        if (urls.isEmpty()) return
        val gate = Semaphore(IMAGE_CONCURRENCY)
        coroutineScope {
            urls.map { url -> async { gate.withPermit { prefetchImage(url) } } }.awaitAll()
        }
    }

    private companion object {
        const val IMAGE_CONCURRENCY = 3
    }
}

fun releasePrefetcher(context: Context, checker: AppUpdateChecker): ReleasePrefetcher {
    val app = context.applicationContext
    return ReleasePrefetcher(
        cache = ReleaseNotesCache(File(app.cacheDir, "release-notes")),
        prefetchImage = releaseImageLoader(app)::prefetch,
        fetchNotes = checker::releaseNotes,
        imagesAllowed = { !app.isActiveNetworkMetered() },
    )
}

private fun Context.isActiveNetworkMetered(): Boolean =
    runCatching { getSystemService(ConnectivityManager::class.java)?.isActiveNetworkMetered == true }
        .getOrDefault(false)
