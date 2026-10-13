package com.x500x.cursimple.app.update

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Relative image targets must reach the renderer as absolute raw URLs, because the mirror pool
 * only routes `raw.githubusercontent.com` (see `releaseImageRequest`). Notes on already-published
 * tags are immutable, so the fetch path rewrites them instead of relying on the authored form.
 */
class ResolveReleaseImageUrlsTest {

    private fun resolve(markdown: String, path: String = "docs/release-notes/v0.7.10.md") =
        resolveReleaseImageUrls(markdown, "cursimple/cursimple-app", "v0.7.10", path)

    @Test
    fun `relative target resolves against the note directory`() {
        assertEquals(
            "![图](https://raw.githubusercontent.com/cursimple/cursimple-app/v0.7.10/docs/release-notes/desktop-todo.png)",
            resolve("![图](desktop-todo.png)"),
        )
    }

    @Test
    fun `absolute target is left untouched`() {
        val line = "![图](https://raw.githubusercontent.com/cursimple/cursimple-app/v0.7.5/docs/release-notes/images/v0.7.5/agenda.png)"
        assertEquals(line, resolve(line))
    }

    @Test
    fun `scheme relative target is left untouched`() {
        assertEquals("![图](//cdn.example.com/a.png)", resolve("![图](//cdn.example.com/a.png)"))
    }

    @Test
    fun `parent and current directory segments collapse`() {
        assertEquals(
            "![图](https://raw.githubusercontent.com/cursimple/cursimple-app/v0.7.10/docs/release-notes/a.png)",
            resolve("![图](images/../a.png)"),
        )
        assertEquals(
            "![图](https://raw.githubusercontent.com/cursimple/cursimple-app/v0.7.10/docs/release-notes/a.png)",
            resolve("![图](./a.png)"),
        )
    }

    @Test
    fun `optional title and indentation survive`() {
        assertEquals(
            "  ![图](https://raw.githubusercontent.com/cursimple/cursimple-app/v0.7.10/docs/release-notes/a.png \"标题\")",
            resolve("  ![图](a.png \"标题\")"),
        )
    }

    @Test
    fun `base path without a directory resolves from the repository root`() {
        assertEquals(
            "![图](https://raw.githubusercontent.com/cursimple/cursimple-app/v0.7.10/a.png)",
            resolve("![图](a.png)", path = "v0.7.10.md"),
        )
    }

    @Test
    fun `inline image markers and non image lines are untouched`() {
        val markdown = "见下图 ![x](a.png) 这里\n\n## 标题"
        assertEquals(markdown, resolve(markdown))
    }

    @Test
    fun `trailing text keeps the line a non image, matching the renderer`() {
        // `parseReleaseNotes` only treats a line as an image when the target closes the line, so
        // rewriting this one would invent a gallery the renderer never produces.
        val line = "![图](a.png) 尾巴"
        assertEquals(line, resolve(line))
    }

    @Test
    fun `parsed images from a relative note end up on the raw host`() {
        val note = """
            # 0.7.10 · 测试版

            ## 桌面待办直接点

            正文。

            ![桌面待办](desktop-todo.png)
            ![另一张](images/extra.png)

            说明
            ![独立](https://example.com/keep.png)
        """.trimIndent()

        val urls = releaseImageUrls(resolve(note))
        assertEquals(
            listOf(
                "https://raw.githubusercontent.com/cursimple/cursimple-app/v0.7.10/docs/release-notes/desktop-todo.png",
                "https://raw.githubusercontent.com/cursimple/cursimple-app/v0.7.10/docs/release-notes/images/extra.png",
                "https://example.com/keep.png",
            ),
            urls,
        )
    }
}
