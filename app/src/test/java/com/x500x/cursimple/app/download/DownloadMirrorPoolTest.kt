package com.x500x.cursimple.app.download

import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadMirrorPoolTest {
    private val pool = DownloadMirrorPool()

    @Test fun `verified new release mirrors replace broken candidates`() {
        val candidates = pool.candidates(DownloadRequest(DownloadPurpose.GithubRelease, "https://github.com/example/plugin/releases/latest/download/manifest.json"))
        assertTrue(candidates.any { it.sourceName == "gh.dpik.top" })
        assertTrue(candidates.any { it.sourceName == "ghfile.geekertao.top" })
        assertFalse(candidates.any { it.sourceName == "ghproxy.monkeyray.net" })
        assertFalse(candidates.any { it.sourceName == "gh.jasonzeng.dev" })
        assertTrue(fastTextCandidates(candidates).take(4).any { it.sourceName == DownloadSourceIds.GITHUB_ORIGIN })
    }

    @Test
    fun `github release candidates do not include jsdelivr`() {
        val candidates = pool.candidates(
            DownloadRequest(
                purpose = DownloadPurpose.GithubRelease,
                url = "https://github.com/cursimple/cursimple-app/releases/download/v1/app.apk",
            ),
        )

        assertTrue(candidates.any { it.url.contains("down.npee.cn/?https://github.com") })
        assertTrue(candidates.any { it.url.contains("cors.isteed.cc/github.com") })
        assertTrue(candidates.any { it.url.contains("hk.gh-proxy.com/https://github.com") })
        assertTrue(candidates.any { it.url.contains("edgeone.gh-proxy.com/https://github.com") })
        assertTrue(candidates.any { it.url.contains("gh.llkk.cc/https://github.com") })
        assertFalse(candidates.any { it.url.contains("jsdelivr.net") })
    }

    @Test
    fun `api urls only use mirrors that pass through real status codes`() {
        val candidates = pool.candidates(
            DownloadRequest(
                purpose = DownloadPurpose.GithubRelease,
                url = "https://api.github.com/repos/cursimple/cursimple-app/releases/latest",
            ),
        )

        // Exclude API proxies that suppress upstream status or share exhausted rate limits.
        assertFalse(candidates.any { it.url.contains("ghfast.top") })
        assertFalse(candidates.any { it.url.contains("ghproxy.net") })
        assertFalse(candidates.any { it.url.contains("gh.llkk.cc") })
        assertTrue(candidates.any { it.url.contains("gh-proxy.com") })
        assertTrue(candidates.any { it.url.contains("edgeone.gh-proxy.com") })
        assertTrue(candidates.any { it.url == "https://api.github.com/repos/cursimple/cursimple-app/releases/latest" })
    }

    @Test
    fun `github raw candidates prefer china cdn and drop dead mirrors`() {
        val candidates = pool.candidates(
            DownloadRequest(
                purpose = DownloadPurpose.GithubRaw,
                url = "https://raw.githubusercontent.com/cursimple/cursimple-plugins/main/manifest.json",
            ),
        )

        // Exclude unavailable raw proxy routes.
        assertFalse(candidates.any { it.url.contains("raw.ihtw.moe") })
        assertEquals(
            "https://cdn.jsdmirror.com/gh/cursimple/cursimple-plugins@main/manifest.json",
            candidates.first().url,
        )
        assertTrue(candidates.any { it.url == "https://cdn.jsdelivr.net.cn/gh/cursimple/cursimple-plugins@main/manifest.json" })
        assertTrue(candidates.any { it.url == "https://cdn.jsdelivr.net/gh/cursimple/cursimple-plugins@main/manifest.json" })
        assertTrue(candidates.any { it.url == "https://fastly.jsdelivr.net/gh/cursimple/cursimple-plugins@main/manifest.json" })
    }

    @Test
    fun `github raw candidates prefer jsdelivr before mirror pool and source`() {
        val candidates = pool.candidates(
            DownloadRequest(
                purpose = DownloadPurpose.GithubRaw,
                url = "https://raw.githubusercontent.com/cursimple/cursimple-plugins/plugin-stars-data/plugins-stars.json",
            ),
        )

        assertTrue(
            candidates.first().url.contains("jsdmirror") || candidates.first().url.contains("jsdelivr"),
        )
        assertTrue(candidates.indexOfFirst { it.sourceName == "ghfast.top" } < candidates.indexOfFirst { it.sourceName == DownloadSourceIds.GITHUB_ORIGIN })
    }

    @Test
    fun `github repo file candidates include xget raw path and jsdelivr gh paths`() {
        val candidates = pool.candidates(
            DownloadRequest(
                purpose = DownloadPurpose.GithubRepoFile,
                url = "https://raw.githubusercontent.com/cursimple/cursimple-plugins/main/manifest.json",
                repository = "cursimple/cursimple-plugins",
                ref = "main",
                path = "manifest.json",
            ),
        )

        // The xget raw route requires its raw path segment.
        assertTrue(candidates.any { it.url == "https://xget.xi-xu.me/gh/cursimple/cursimple-plugins/raw/main/manifest.json" })
        assertTrue(candidates.any { it.url == "https://cdn.jsdelivr.net/gh/cursimple/cursimple-plugins@main/manifest.json" })
    }
}
