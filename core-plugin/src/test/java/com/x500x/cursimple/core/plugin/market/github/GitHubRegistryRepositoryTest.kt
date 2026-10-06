package com.x500x.cursimple.core.plugin.market.github

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GitHubRegistryRepositoryTest {
    @Test
    fun `fetch all loads plugins stars registry and latest release manifest`() = runBlocking {
        val repository = GitHubRegistryRepository(
            apiClient = publicRepoApi(),
            fetchText = { url ->
                when {
                    url.startsWith(
                        "https://raw.githubusercontent.com/cursimple/cursimple-plugins/plugin-stars-data/plugins-stars.json",
                    ) ->
                        """
                        {
                          "repositories": [
                            {
                              "name": "cursimple/example_school_plugin",
                              "repo": "example_school_plugin",
                              "owner": "cursimple",
                              "avatar": "https://avatars.githubusercontent.com/u/283925439?s=80&v=4",
                              "description": "ExampleU course plugin for cursimple.",
                              "star": 7,
                              "language": "JavaScript",
                              "url": "https://github.com/cursimple/example_school_plugin"
                            }
                          ]
                        }
                        """.trimIndent()

                    url.startsWith("https://github.com/cursimple/example_school_plugin/releases/latest/download/manifest.json?ts=") ->
                        """{"filename":"exampleu-eams-v1.0.32.zip","version":"v1.0.32"}"""

                    else -> error("unexpected url: $url")
                }
            },
        )

        val repos = repository.fetchAll("cursimple/cursimple-plugins")

        assertEquals(1, repos.size)
        val summary = repos.single()
        assertEquals("cursimple/example_school_plugin", summary.fullName)
        assertEquals("cursimple", summary.owner)
        assertEquals("example_school_plugin", summary.name)
        assertEquals(7, summary.stars)
        assertEquals("JavaScript", summary.language)
        assertNotNull(summary.latestRelease)
        assertEquals("v1.0.32", summary.latestRelease?.tagName)
        assertEquals("exampleu-eams-v1.0.32.zip", summary.latestRelease?.assetName)
        assertEquals(
            "https://github.com/cursimple/example_school_plugin/releases/latest/download/exampleu-eams-v1.0.32.zip",
            summary.latestRelease?.downloadUrl,
        )
    }

    @Test
    fun `release manifest request always carries a cache buster so stale mirror copies cannot win`() = runBlocking {
        val requested = mutableListOf<String>()
        val repository = GitHubRegistryRepository(
            apiClient = publicRepoApi(),
            fetchText = { url ->
                requested += url
                """{"filename":"demo.zip","version":"v1.3.0"}"""
            },
        )

        repository.fetchLatestReleaseAsset("owner/repo", fresh = false)
        repository.fetchLatestReleaseAsset("owner/other", fresh = true)

        assertEquals(2, requested.size)
        requested.forEach { url ->
            assertTrue(url, Regex("^https://github\\.com/owner/(repo|other)/releases/latest/download/manifest\\.json\\?ts=\\d+$").matches(url))
        }
    }

    @Test
    fun `latest release manifest can use name as package filename`() = runBlocking {
        val repository = GitHubRegistryRepository(
            apiClient = publicRepoApi(),
            fetchText = { url ->
                assertTrue(url, url.startsWith("https://github.com/owner/repo/releases/latest/download/manifest.json?ts="))
                """{"name":"demo plugin.zip","version":"1.2.3"}"""
            },
        )

        val asset = repository.fetchLatestReleaseAsset("owner/repo")

        assertNotNull(asset)
        assertEquals("1.2.3", asset?.tagName)
        assertEquals("demo plugin.zip", asset?.assetName)
        assertEquals(
            "https://github.com/owner/repo/releases/latest/download/demo%20plugin.zip",
            asset?.downloadUrl,
        )
    }

    @Test
    fun `fetchAll uses embedded release info and skips per-repo fetch`() = runBlocking {
        var fetchCount = 0
        val repository = GitHubRegistryRepository(
            apiClient = publicRepoApi(),
            fetchText = { url ->
                fetchCount++
                when {
                    url.startsWith(
                        "https://raw.githubusercontent.com/test/repo/plugin-stars-data/plugins-stars.json",
                    ) ->
                        """
                        {
                          "repositories": [
                            {
                              "name": "test/plugin-a",
                              "repo": "plugin-a",
                              "owner": "test",
                              "description": "Plugin A",
                              "star": 5,
                              "url": "https://github.com/test/plugin-a",
                              "release": {
                                "tag": "v2.0.0",
                                "filename": "plugin-a-v2.0.0.zip"
                              }
                            }
                          ]
                        }
                        """.trimIndent()
                    else -> error("unexpected url: $url")
                }
            },
        )

        val repos = repository.fetchAll("test/repo")

        assertEquals(1, repos.size)
        assertEquals(1, fetchCount)
        val summary = repos.single()
        assertNotNull(summary.latestRelease)
        assertEquals("v2.0.0", summary.latestRelease?.tagName)
        assertEquals("plugin-a-v2.0.0.zip", summary.latestRelease?.assetName)
    }
}
