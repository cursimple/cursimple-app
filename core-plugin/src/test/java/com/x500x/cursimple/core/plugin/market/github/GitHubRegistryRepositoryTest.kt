package com.x500x.cursimple.core.plugin.market.github

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class GitHubRegistryRepositoryTest {
    @Test
    fun `fetch all loads plugins stars registry and latest release manifest`() = runBlocking {
        val repository = GitHubRegistryRepository(
            fetchText = { url ->
                when (url) {
                    "https://raw.githubusercontent.com/cursimple/cursimple-plugins/plugin-stars-data/plugins-stars.json" ->
                        """
                        {
                          "repositories": [
                            {
                              "name": "cursimple/YangtzU_course_plugin",
                              "repo": "YangtzU_course_plugin",
                              "owner": "cursimple",
                              "avatar": "https://avatars.githubusercontent.com/u/283925439?s=80&v=4",
                              "description": "YangtzU course plugin for cursimple.",
                              "star": 7,
                              "language": "JavaScript",
                              "url": "https://github.com/cursimple/YangtzU_course_plugin"
                            }
                          ]
                        }
                        """.trimIndent()

                    "https://github.com/cursimple/YangtzU_course_plugin/releases/latest/download/manifest.json" ->
                        """{"filename":"yangtzeu-eams-v1.0.32.zip","version":"v1.0.32"}"""

                    else -> error("unexpected url: $url")
                }
            },
        )

        val repos = repository.fetchAll("cursimple/cursimple-plugins")

        assertEquals(1, repos.size)
        val summary = repos.single()
        assertEquals("cursimple/YangtzU_course_plugin", summary.fullName)
        assertEquals("cursimple", summary.owner)
        assertEquals("YangtzU_course_plugin", summary.name)
        assertEquals(7, summary.stars)
        assertEquals("JavaScript", summary.language)
        assertNotNull(summary.latestRelease)
        assertEquals("v1.0.32", summary.latestRelease?.tagName)
        assertEquals("yangtzeu-eams-v1.0.32.zip", summary.latestRelease?.assetName)
        assertEquals(
            "https://github.com/cursimple/YangtzU_course_plugin/releases/latest/download/yangtzeu-eams-v1.0.32.zip",
            summary.latestRelease?.downloadUrl,
        )
    }

    @Test
    fun `latest release manifest can use name as package filename`() = runBlocking {
        val repository = GitHubRegistryRepository(
            fetchText = { url ->
                assertEquals("https://github.com/owner/repo/releases/latest/download/manifest.json", url)
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
}
