package com.x500x.cursimple.app.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UpdateReleaseSelectionTest {

    private fun entry(
        index: Int,
        tag: String,
        prerelease: Boolean = false,
        draft: Boolean = false,
        publishedAt: String = "2026-09-0${index + 1}T00:00:00Z",
    ) = ReleaseEntry(index, tag, draft, prerelease, publishedAt)

    @Test
    fun `an empty list yields nothing`() {
        assertNull(pickUpdateRelease(emptyList(), includePrerelease = true))
    }

    @Test
    fun `prereleases are skipped unless opted in`() {
        val entries = listOf(
            entry(0, "v0.7.0-beta.2", prerelease = true, publishedAt = "2026-09-04T00:00:00Z"),
            entry(1, "v0.6.0", publishedAt = "2026-09-01T00:00:00Z"),
        )

        assertEquals("v0.6.0", pickUpdateRelease(entries, includePrerelease = false)?.tagName)
        assertEquals("v0.7.0-beta.2", pickUpdateRelease(entries, includePrerelease = true)?.tagName)
    }

    @Test
    fun `only prereleases exist and the user has not opted in`() {
        val entries = listOf(entry(0, "v0.7.0-beta.1", prerelease = true))

        assertNull(pickUpdateRelease(entries, includePrerelease = false))
    }

    @Test
    fun `drafts are never picked`() {
        val entries = listOf(
            entry(0, "v0.8.0", draft = true, publishedAt = "2026-09-09T00:00:00Z"),
            entry(1, "v0.7.0", publishedAt = "2026-09-02T00:00:00Z"),
        )

        assertEquals("v0.7.0", pickUpdateRelease(entries, includePrerelease = true)?.tagName)
    }

    @Test
    fun `the newest publish time wins regardless of list order`() {
        val entries = listOf(
            entry(0, "v0.5.0", publishedAt = "2026-01-01T00:00:00Z"),
            entry(1, "v0.9.0", publishedAt = "2026-09-09T00:00:00Z"),
            entry(2, "v0.7.0", publishedAt = "2026-05-05T00:00:00Z"),
        )

        assertEquals("v0.9.0", pickUpdateRelease(entries, includePrerelease = true)?.tagName)
    }

    @Test
    fun `entries without a publish time rank last`() {
        val entries = listOf(
            entry(0, "v9.9.9", publishedAt = ""),
            entry(1, "v0.7.0", publishedAt = "2026-09-02T00:00:00Z"),
        )

        assertEquals("v0.7.0", pickUpdateRelease(entries, includePrerelease = true)?.tagName)
    }

    @Test
    fun `entries without a tag are unusable`() {
        val entries = listOf(entry(0, "", publishedAt = "2026-09-09T00:00:00Z"), entry(1, "v0.7.0"))

        assertEquals("v0.7.0", pickUpdateRelease(entries, includePrerelease = true)?.tagName)
    }

    @Test
    fun `equal publish times fall back to list order`() {
        val entries = listOf(
            entry(0, "first", publishedAt = "2026-09-04T00:00:00Z"),
            entry(1, "second", publishedAt = "2026-09-04T00:00:00Z"),
        )

        assertEquals("first", pickUpdateRelease(entries, includePrerelease = true)?.tagName)
    }
}
