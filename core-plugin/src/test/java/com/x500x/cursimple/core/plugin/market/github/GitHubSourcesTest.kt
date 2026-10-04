package com.x500x.cursimple.core.plugin.market.github

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GitHubSourcesTest {
    @Test
    fun `repository addresses normalize GitHub URLs and git remotes`() {
        listOf(
            " Owner/Repo.git ", "https://github.com/Owner/Repo/", "HTTPS://GITHUB.COM/Owner/Repo.git",
            "http://www.github.com/Owner/Repo/tree/main?tab=readme#top", "github.com/Owner/Repo/releases/latest",
            "www.github.com/Owner/Repo/blob/main/README.md", "git@github.com:Owner/Repo.git",
            "ssh://git@github.com/Owner/Repo.git", "https://github.com:443/Owner/Repo",
        ).forEach { assertEquals(it, "Owner/Repo", GitHubRepoAddress.parse(it)) }
    }

    @Test
    fun `malformed or non GitHub repository addresses are rejected`() {
        listOf(
            "", "owner", "owner/repo/extra", "owner/repo?query", "owner/repo#fragment", "/owner/repo",
            "owner//repo", "owner/.", "owner/..", "owner/.git", "owner/re po", "owner/répo",
            "https://example.org/github.com/owner/repo", "https://github.com.evil.test/owner/repo",
            "https://evilgithub.com/owner/repo", "https://api.github.com/owner/repo", "//github.com/owner/repo",
            "https://github.com@evil.test/owner/repo", "https://user:secret@github.com/owner/repo",
            "https://github.com:444/owner/repo", "ftp://github.com/owner/repo", "git@evil.test:owner/repo.git",
            "https://github.com//owner/repo", "https://github.com/owner/../repo",
            "https://github.com/owner/%2e%2e", "https://github.com/owner/repo/../other",
            "https://github.com/owner%2frepo/name", "https://github.com/owner?next=/repo",
            "https://github.com/owner/repo\\suffix", "owner/repo\nextra", "${"a".repeat(40)}/repo",
        ).forEach { assertNull(it, GitHubRepoAddress.parse(it)) }
    }

    @Test
    fun `plugin and component data branches stay distinct`() {
        assertEquals("plugin-stars-data", MarketSourceKind.Plugin.dataBranch)
        assertEquals("plugins-stars.json", MarketSourceKind.Plugin.dataFile)
        assertEquals("component-stars-data", MarketSourceKind.Component.dataBranch)
        assertEquals("components-stars.json", MarketSourceKind.Component.dataFile)
    }
}
