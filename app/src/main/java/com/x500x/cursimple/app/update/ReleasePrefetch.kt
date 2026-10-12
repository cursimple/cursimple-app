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
    fun read(tag: String, language: String = "zh-CN"): String? =
        fileFor(tag, language)?.takeIf { it.isFile && it.length() > 0L }?.let { runCatching { it.readText() }.getOrNull() }

    fun write(tag: String, notes: String, language: String = "zh-CN") {
        val target = fileFor(tag, language) ?: return
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
        val files = dir.listFiles { file -> file.name.endsWith(".md") }.orEmpty()
        fun version(file: File) = file.name.removeSuffix(".md").removeSuffix(".en").removeSuffix(".zh-TW")
        val keep = files.groupBy(::version).entries.sortedByDescending { (_, entries) -> entries.maxOf { it.lastModified() } }
            .take(KEEP_VERSIONS).mapTo(mutableSetOf()) { it.key }
        files.filter { version(it) !in keep }.forEach { it.delete() }
    }

    private fun fileFor(tag: String, language: String): File? {
        val name = tag.trim()
        // Validate remote tag characters before deriving cache paths.
        if (!SAFE_TAG.matches(name) || name.startsWith(".")) return null
        val suffix = when (releaseNotesLanguage(language)) { "en" -> ".en"; "zh-TW" -> ".zh-TW"; else -> "" }
        return File(dir, "$name$suffix.md")
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
    private val language: String = "zh-CN",
) {

    suspend fun notes(tag: String): String? =
        cache.read(tag, language) ?: fetchNotes(tag)?.also { cache.write(tag, it, language) }

    /** Reuse [knownNotes] from the update check to avoid another request. */
    suspend fun prefetch(tag: String, knownNotes: String?) {
        try {
            val text = knownNotes?.takeIf { it.isNotBlank() }
                ?.also { if (cache.read(tag, language) != it) cache.write(tag, it, language) }
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
        fetchNotes = { tag -> checker.releaseNotes(tag, announcementLocale(context)) },
        imagesAllowed = { !app.isActiveNetworkMetered() },
        language = announcementLocale(context),
    )
}

private fun Context.isActiveNetworkMetered(): Boolean =
    runCatching { getSystemService(ConnectivityManager::class.java)?.isActiveNetworkMetered == true }
        .getOrDefault(false)
