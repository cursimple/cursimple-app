package com.x500x.cursimple.app.download

import org.junit.Assert.assertTrue
import org.junit.Test

/** 缓存击穿参数必须一路带到各家镜像，否则按路径改写的候选又会读回旧文件。 */
class DownloadMirrorPoolQueryTest {

    private fun candidatesFor(url: String): List<String> =
        DownloadMirrorPool().candidates(
            DownloadRequest(purpose = DownloadPurpose.GithubRaw, url = url),
        ).map { it.url }

    @Test
    fun `the cache busting query reaches path rewriting mirrors`() {
        val url = "https://raw.githubusercontent.com/o/r/branch/plugins-stars.json?ts=123"

        val jsdelivr = candidatesFor(url).filter { it.contains("jsdelivr") }

        assertTrue("应有 jsDelivr 候选", jsdelivr.isNotEmpty())
        assertTrue(
            "jsDelivr 候选丢了查询串：$jsdelivr",
            jsdelivr.all { it.endsWith("plugins-stars.json?ts=123") },
        )
    }

    @Test
    fun `a url without a query is unchanged`() {
        val url = "https://raw.githubusercontent.com/o/r/branch/plugins-stars.json"

        val jsdelivr = candidatesFor(url).filter { it.contains("jsdelivr") }

        assertTrue(jsdelivr.all { it.endsWith("plugins-stars.json") })
    }
}
