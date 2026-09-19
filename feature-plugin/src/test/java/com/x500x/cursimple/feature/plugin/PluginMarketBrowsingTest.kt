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
    schoolAliases: List<String> = emptyList(),
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
    schoolAliases = schoolAliases,
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

class SchoolAliasSearchTest {

    private val bit = repo(
        fullName = "someone/bit-schedule",
        description = "Undergraduate timetable plugin",
        schoolAliases = listOf("北京理工大学", "北理工", "beijingligong", "BIT"),
    )
    private val zf = repo(
        fullName = "vendor/zf-plugin",
        description = "正方教务系统",
    )
    private val repos = listOf(bit, zf)

    @Test
    fun `the full chinese school name finds a repo named in english`() {
        assertEquals(listOf(bit), filterMarketRepos(repos, "北京理工大学"))
    }

    @Test
    fun `a short form of the school name also matches`() {
        assertEquals(listOf(bit), filterMarketRepos(repos, "北理工"))
    }

    @Test
    fun `an alias matches on a prefix so typing partway is enough`() {
        // 学生边打边看结果，打到「北京理工」时就该出来，不必打完「大学」
        assertEquals(listOf(bit), filterMarketRepos(repos, "北京理工"))
    }

    @Test
    fun `pinyin written into the aliases matches too`() {
        assertEquals(listOf(bit), filterMarketRepos(repos, "beijingligong"))
    }

    @Test
    fun `alias matching ignores case`() {
        assertEquals(listOf(bit), filterMarketRepos(repos, "bit"))
    }

    @Test
    fun `repos without aliases still match on name and description`() {
        assertEquals(listOf(zf), filterMarketRepos(repos, "正方"))
    }

    @Test
    fun `an unrelated school matches nothing`() {
        assertTrue(filterMarketRepos(repos, "清华").isEmpty())
    }

    @Test
    fun `a registry holding a single school matches only that school`() {
        // 目前注册表里就一个学校，搜别家不能把这一个顶上来充数
        val onlyOne = listOf(bit)

        assertEquals(listOf(bit), filterMarketRepos(onlyOne, "北理工"))
        assertTrue(filterMarketRepos(onlyOne, "清华大学").isEmpty())
        assertTrue(filterMarketRepos(onlyOne, "复旦").isEmpty())
        assertTrue(filterMarketRepos(onlyOne, "不存在的学校").isEmpty())
    }
}

class SchoolDisplayTitleTest {

    @Test
    fun `the declared school name is used instead of the repo name`() {
        // 这一页的用户在找自己的学校，不是在找 YangtzU_course_plugin 这种仓库名
        val summary = repo(
            fullName = "cursimple/YangtzU_course_plugin",
            schoolAliases = listOf("长江大学", "长大", "cjdx"),
        )

        assertEquals("长江大学", summary.schoolDisplayTitle())
    }

    @Test
    fun `a plugin without declared schools falls back to the repo name`() {
        val summary = repo(fullName = "someone/mystery_plugin")

        assertEquals("mystery_plugin", summary.schoolDisplayTitle())
    }

    @Test
    fun `blank alias entries are skipped`() {
        val summary = repo(fullName = "a/b", schoolAliases = listOf("  ", "长江大学"))

        assertEquals("长江大学", summary.schoolDisplayTitle())
    }
}
