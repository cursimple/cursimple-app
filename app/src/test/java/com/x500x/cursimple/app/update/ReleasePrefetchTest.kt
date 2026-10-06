package com.x500x.cursimple.app.update

import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.util.concurrent.atomic.AtomicInteger

class ReleasePrefetchTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val notes = """
        # v1.0.0 更新内容

        ## 亮点一
        说明
        ![一](https://raw.githubusercontent.com/o/r/main/a.png)

        ## 亮点二
        ![二](https://raw.githubusercontent.com/o/r/main/b.png)
        ![重复](https://raw.githubusercontent.com/o/r/main/a.png)
    """.trimIndent()

    private fun prefetcher(
        cache: ReleaseNotesCache = ReleaseNotesCache(folder.newFolder()),
        metered: Boolean = false,
        fetch: suspend (String) -> String? = { null },
        image: suspend (String) -> Boolean = { true },
    ) = ReleasePrefetcher(cache, image, fetch, imagesAllowed = { !metered })

    @Test
    fun `image urls come in document order without duplicates`() {
        assertEquals(
            listOf(
                "https://raw.githubusercontent.com/o/r/main/a.png",
                "https://raw.githubusercontent.com/o/r/main/b.png",
            ),
            releaseImageUrls(notes),
        )
        assertTrue(releaseImageUrls("# 没有图\n\n- 只有文字").isEmpty())
    }

    @Test
    fun `cache round trips and rejects unsafe tags`() {
        val cache = ReleaseNotesCache(folder.newFolder())
        cache.write("v1.0.0", "# 正文")
        assertEquals("# 正文", cache.read("v1.0.0"))
        assertNull(cache.read("v9.9.9"))

        cache.write("../escape", "x")
        cache.write("..", "x")
        cache.write("a/b", "x")
        assertNull(cache.read("../escape"))
        assertNull(cache.read(".."))
        assertNull(cache.read("a/b"))
        cache.write("v1.0.1", "   ")
        assertNull(cache.read("v1.0.1"))
    }

    @Test
    fun `cache keeps only the most recent versions`() {
        val dir = folder.newFolder()
        val cache = ReleaseNotesCache(dir)
        (1..6).forEach { index ->
            cache.write("v1.0.$index", "# $index")
            dir.resolve("v1.0.$index.md").setLastModified(1_000_000L + index * 1000L)
        }
        cache.write("v1.0.7", "# 7")
        val kept = dir.listFiles { file -> file.name.endsWith(".md") }!!.map { it.name }.sorted()
        assertEquals(4, kept.size)
        assertTrue("v1.0.7.md" in kept)
        assertFalse("v1.0.1.md" in kept)
    }

    @Test
    fun `known notes are stored without any network and images are prefetched`() = runBlocking {
        val fetched = AtomicInteger()
        val images = mutableListOf<String>()
        val cache = ReleaseNotesCache(folder.newFolder())
        prefetcher(cache, fetch = { fetched.incrementAndGet(); null }, image = { images += it; true })
            .prefetch("v1.0.0", notes)

        assertEquals(0, fetched.get())
        assertEquals(notes, cache.read("v1.0.0"))
        assertEquals(releaseImageUrls(notes), images.sorted())
    }

    @Test
    fun `missing notes are fetched once and then served from cache`() = runBlocking {
        val fetched = AtomicInteger()
        val p = prefetcher(fetch = { fetched.incrementAndGet(); notes })

        assertEquals(notes, p.notes("v1.0.0"))
        assertEquals(notes, p.notes("v1.0.0"))
        assertEquals(1, fetched.get())
    }

    @Test
    fun `metered network stores the text but skips images`() = runBlocking {
        val images = AtomicInteger()
        val cache = ReleaseNotesCache(folder.newFolder())
        prefetcher(cache, metered = true, image = { images.incrementAndGet(); true }).prefetch("v1.0.0", notes)

        assertEquals(notes, cache.read("v1.0.0"))
        assertEquals(0, images.get())
    }

    @Test
    fun `a failing image or fetch never throws out of prefetch`() = runBlocking {
        prefetcher(fetch = { error("offline") }).prefetch("v1.0.0", null)
        prefetcher(image = { error("mirror down") }).prefetch("v1.0.0", notes)
    }

    @Test
    fun `images are downloaded at most three at a time`() = runBlocking {
        val many = (1..9).joinToString("\n\n") { "## 第 $it 页\n![图](https://raw.githubusercontent.com/o/r/main/$it.png)" }
        val running = AtomicInteger()
        val peak = AtomicInteger()
        prefetcher(image = {
            peak.updateAndGet { maxOf(it, running.incrementAndGet()) }
            delay(20)
            running.decrementAndGet()
            true
        }).prefetchImages(many)

        assertEquals(3, peak.get())
    }
}
