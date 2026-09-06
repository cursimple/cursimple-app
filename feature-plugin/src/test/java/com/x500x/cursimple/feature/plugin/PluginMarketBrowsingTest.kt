package com.x500x.cursimple.feature.plugin

import com.x500x.cursimple.core.plugin.market.github.GitHubRepoSummary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private fun repo(
    fullName: String,
    name: String = fullName.substringAfter('/'),
    owner: String = fullName.substringBefore('/'),
    description: String = "",
): GitHubRepoSummary = GitHubRepoSummary(
    fullName = fullName,
    owner = owner,
    name = name,
    description = description,
    stars = 0,
    avatarUrl = "",
    htmlUrl = "",
    ownerHtmlUrl = "",
    isFresh = true,
)

class MarketPreviewTest {
    @Test
    fun `a short list is shown in full with nothing hidden`() {
        val repos = List(4) { repo("owner/plugin-$it") }

        val preview = marketPreview(repos, limit = 6)

        assertEquals(4, preview.visible.size)
        assertEquals(0, preview.hiddenCount)
    }

    @Test
    fun `exactly the limit still hides nothing`() {
        val repos = List(6) { repo("owner/plugin-$it") }

        val preview = marketPreview(repos, limit = 6)

        assertEquals(6, preview.visible.size)
        assertEquals(0, preview.hiddenCount)
    }

    @Test
    fun `a long list is truncated and the remainder is counted`() {
        val repos = List(20) { repo("owner/plugin-$it") }

        val preview = marketPreview(repos, limit = 6)

        assertEquals(6, preview.visible.size)
        assertEquals(14, preview.hiddenCount)
        assertEquals("owner/plugin-0", preview.visible.first().fullName)
        assertEquals("owner/plugin-5", preview.visible.last().fullName)
    }

    @Test
    fun `an empty list hides nothing so no browse entry appears`() {
        val preview = marketPreview(emptyList(), limit = 6)

        assertTrue(preview.visible.isEmpty())
        assertEquals(0, preview.hiddenCount)
    }
}

class FilterMarketReposTest {
    private val repos = listOf(
        repo("cursimple/YangtzU_course_plugin", description = "长江大学教务系统"),
        repo("someone/tsinghua-timetable", description = "Tsinghua University schedule"),
        repo("other/zju-plugin", description = "浙江大学"),
    )

    @Test
    fun `a blank query keeps every plugin`() {
        assertEquals(repos, filterMarketRepos(repos, ""))
        assertEquals(repos, filterMarketRepos(repos, "   "))
    }

    @Test
    fun `matching is case insensitive on the repository name`() {
        val result = filterMarketRepos(repos, "yangtzu")

        assertEquals(1, result.size)
        assertEquals("cursimple/YangtzU_course_plugin", result.single().fullName)
    }

    @Test
    fun `the owner is searchable too`() {
        val result = filterMarketRepos(repos, "someone")

        assertEquals(1, result.size)
        assertEquals("someone/tsinghua-timetable", result.single().fullName)
    }

    @Test
    fun `the description is searchable, including Chinese`() {
        val result = filterMarketRepos(repos, "浙江")

        assertEquals(1, result.size)
        assertEquals("other/zju-plugin", result.single().fullName)
    }

    @Test
    fun `surrounding whitespace in the query is ignored`() {
        assertEquals(1, filterMarketRepos(repos, "  zju  ").size)
    }

    @Test
    fun `a query matching nothing yields an empty list`() {
        assertTrue(filterMarketRepos(repos, "no-such-school").isEmpty())
    }
}
