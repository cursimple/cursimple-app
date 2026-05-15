package com.x500x.cursimple.app.download

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadMirrorPoolTest {
    private val pool = DownloadMirrorPool()

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
        assertFalse(candidates.any { it.url.contains("jsdelivr.net") })
    }

    @Test
    fun `github raw candidates include raw proxies and jsdelivr`() {
        val candidates = pool.candidates(
            DownloadRequest(
                purpose = DownloadPurpose.GithubRaw,
                url = "https://raw.githubusercontent.com/cursimple/cursimple-plugins/main/manifest.json",
            ),
        )

        assertTrue(candidates.any { it.url.contains("raw.ihtw.moe/raw.githubusercontent.com") })
        assertTrue(candidates.any { it.url == "https://cdn.jsdelivr.net/gh/cursimple/cursimple-plugins@main/manifest.json" })
        assertTrue(candidates.any { it.url == "https://fastly.jsdelivr.net/gh/cursimple/cursimple-plugins@main/manifest.json" })
    }

    @Test
    fun `github repo file candidates include xget and jsdelivr gh paths`() {
        val candidates = pool.candidates(
            DownloadRequest(
                purpose = DownloadPurpose.GithubRepoFile,
                url = "https://raw.githubusercontent.com/cursimple/cursimple-plugins/main/manifest.json",
                repository = "cursimple/cursimple-plugins",
                ref = "main",
                path = "manifest.json",
            ),
        )

        assertTrue(candidates.any { it.url == "https://xget.xi-xu.me/gh/cursimple/cursimple-plugins/main/manifest.json" })
        assertTrue(candidates.any { it.url == "https://cdn.jsdelivr.net/gh/cursimple/cursimple-plugins@main/manifest.json" })
    }
}
