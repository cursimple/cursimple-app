package com.x500x.cursimple.app.download

import org.junit.Assert.assertEquals
import org.junit.Test

class MirrorRaceRoundsTest {

    private fun candidates(vararg urls: String) = urls.map { DownloadCandidate(it, it) }

    @Test
    fun `no candidates means no rounds`() {
        assertEquals(emptyList<List<DownloadCandidate>>(), raceRounds(emptyList(), null, 4))
    }

    @Test
    fun `without a preferred mirror candidates are chunked in order`() {
        val rounds = raceRounds(candidates("a", "b", "c"), preferredUrl = null, roundSize = 2)

        assertEquals(listOf(listOf("a", "b"), listOf("c")), rounds.map { r -> r.map { it.url } })
    }

    @Test
    fun `the preferred mirror gets a round of its own`() {
        val rounds = raceRounds(candidates("a", "b", "c"), preferredUrl = "c", roundSize = 2)

        // 上次成功的先单独试，命中时整次只发一个请求
        assertEquals(listOf(listOf("c"), listOf("a", "b")), rounds.map { r -> r.map { it.url } })
    }

    @Test
    fun `an unknown preferred mirror is ignored`() {
        val rounds = raceRounds(candidates("a", "b"), preferredUrl = "gone", roundSize = 4)

        assertEquals(listOf(listOf("a", "b")), rounds.map { r -> r.map { it.url } })
    }

    @Test
    fun `every candidate appears exactly once`() {
        val all = candidates("a", "b", "c", "d", "e")

        listOf(null, "a", "e", "missing").forEach { preferred ->
            val flat = raceRounds(all, preferred, roundSize = 2).flatten().map { it.url }
            assertEquals("preferred=$preferred", all.map { it.url }.sorted(), flat.sorted())
            assertEquals("preferred=$preferred", flat.distinct(), flat)
        }
    }

    @Test
    fun `a non positive round size still produces usable rounds`() {
        val rounds = raceRounds(candidates("a", "b"), preferredUrl = null, roundSize = 0)

        assertEquals(listOf(listOf("a"), listOf("b")), rounds.map { r -> r.map { it.url } })
    }
}
