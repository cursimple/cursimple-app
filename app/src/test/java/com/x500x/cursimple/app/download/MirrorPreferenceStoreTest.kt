package com.x500x.cursimple.app.download

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MirrorPreferenceStoreTest {

    @Test
    fun `cache key separates purposes sharing one host`() {
        val release = DownloadRequest(
            purpose = DownloadPurpose.GithubRelease,
            url = "https://api.github.com/repos/a/b/releases/latest",
        )
        val raw = DownloadRequest(
            purpose = DownloadPurpose.GithubRaw,
            url = "https://api.github.com/repos/a/b/releases/latest",
        )

        assertEquals("GithubRelease:api.github.com", MirrorPreferenceStore.cacheKeyOf(release))
        assertEquals("GithubRaw:api.github.com", MirrorPreferenceStore.cacheKeyOf(raw))
    }

    @Test
    fun `cache key keeps different hosts apart within one purpose`() {
        val api = DownloadRequest(
            purpose = DownloadPurpose.GithubRelease,
            url = "https://api.github.com/repos/a/b/releases/latest",
        )
        val asset = DownloadRequest(
            purpose = DownloadPurpose.GithubRelease,
            url = "https://github.com/a/b/releases/download/v1/app.apk",
        )

        assertEquals("GithubRelease:api.github.com", MirrorPreferenceStore.cacheKeyOf(api))
        assertEquals("GithubRelease:github.com", MirrorPreferenceStore.cacheKeyOf(asset))
    }

    @Test
    fun `probe cache is fresh only within ttl`() {
        val now = 10_000_000L
        assertTrue(
            MirrorPreferenceStore.isProbeFresh(
                now - MirrorPreferenceStore.PROBE_CACHE_TTL_MILLIS + 1,
                now,
            ),
        )
        assertFalse(
            MirrorPreferenceStore.isProbeFresh(
                now - MirrorPreferenceStore.PROBE_CACHE_TTL_MILLIS,
                now,
            ),
        )
        // 时钟回拨时记录不算新鲜，避免永久命中陈旧缓存
        assertFalse(MirrorPreferenceStore.isProbeFresh(now + 1_000, now))
    }

    @Test
    fun `race rounds put preferred first and keep the rest chunked`() {
        val candidates = ('a'..'f').map { DownloadCandidate("s$it", "u$it") }

        val rounds = raceRounds(candidates, preferredUrl = "ud", roundSize = 2)

        // 记住的镜像领头，和下一个一起竞速；它卡住时不用等满超时
        assertEquals(listOf("ud", "ua"), rounds.first().map { it.url })
        assertEquals(
            listOf(listOf("ub", "uc"), listOf("ue", "uf")),
            rounds.drop(1).map { round -> round.map { it.url } },
        )
    }

    @Test
    fun `race rounds without preferred just chunk`() {
        val candidates = ('a'..'e').map { DownloadCandidate("s$it", "u$it") }

        val rounds = raceRounds(candidates, preferredUrl = null, roundSize = 2)

        assertEquals(3, rounds.size)
        assertEquals(listOf("ua", "ub"), rounds[0].map { it.url })
        assertEquals(listOf("ue"), rounds[2].map { it.url })
    }

    @Test
    fun `race rounds fall back to chunking when preferred url is unknown`() {
        val candidates = ('a'..'c').map { DownloadCandidate("s$it", "u$it") }

        val rounds = raceRounds(candidates, preferredUrl = "not-a-candidate", roundSize = 2)

        assertEquals(2, rounds.size)
        assertEquals(listOf("ua", "ub"), rounds[0].map { it.url })
    }
}
