package com.x500x.cursimple.app.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The announcement renderer fetches images over the in-app mirror pool, which only understands
 * `raw.githubusercontent.com` URLs (see `releaseImageRequest`). Notes fetched by tag are now
 * rewritten by `resolveReleaseImageUrls`, so relative paths no longer break the announcement —
 * but absolute raw URLs stay the convention because these files are read outside the app too
 * (GitHub rendering, the website copies). This checks versions at or newer than the first
 * release with image-heavy announcements (v0.7.9).
 */
class ReleaseNotesImagePolicyTest {

    @Test
    fun `current english and chinese notes use absolute raw urls`() {
        val notes = File("../docs/release-notes/v0.7.10.md")
        assertTrue("v0.7.10.md missing: ${notes.absolutePath}", notes.isFile)
        val imageLines = notes.readLines().filter { it.startsWith("![") }
        assertEquals(5, imageLines.size)
        for (line in imageLines) {
            assertTrue("must be raw.githubusercontent.com: $line", line.contains("https://raw.githubusercontent.com/cursimple/cursimple-app/"))
            assertTrue("must use tag ref: $line", line.contains("/v0.7.10/"))
        }
    }

    @Test
    fun `recent notes do not use relative image paths`() {
        val dir = File("../docs/release-notes")
        assertTrue(dir.isDirectory)
        val bad = arrayListOf<String>()
        for (file in dir.listFiles() ?: emptyArray()) {
            if (!file.name.matches(Regex("v0\\.(7\\.\\d+|[89]\\.\\d+|\\d{2,}\\..*)\\.md"))) continue
            val version = file.name.removePrefix("v").removeSuffix(".md").split(".").mapNotNull { it.toIntOrNull() }
            if (version.size < 3) continue
            if (version[0] == 0 && version[1] == 7 && version[2] < 9) continue
            for (line in file.readLines()) {
                if (line.startsWith("![") && !line.contains("https://raw.githubusercontent.com/cursimple/cursimple-app/")) {
                    bad += "${file.name}: $line"
                }
            }
        }
        assertEquals("relative image URLs found", emptyList<String>(), bad)
    }
}
