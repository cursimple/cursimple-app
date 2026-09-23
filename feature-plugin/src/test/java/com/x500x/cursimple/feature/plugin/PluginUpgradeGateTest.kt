package com.x500x.cursimple.feature.plugin

import com.x500x.cursimple.core.plugin.install.InstalledPluginRecord
import com.x500x.cursimple.core.plugin.install.PluginInstallSource
import com.x500x.cursimple.core.plugin.market.github.GitHubReleaseAsset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** 导课前「有没有新版」：现查到的和市场缓存里的取较新的那个。 */
class PluginUpgradeGateTest {

    private val slug = "cursimple/YangtzU_course_plugin"

    private val record = InstalledPluginRecord(
        pluginId = "yangtzeu",
        name = "长江大学教务插件",
        version = "1.0.33",
        versionCode = 1033,
        storagePath = "/tmp/yangtzeu",
        installedAt = "2026-09-03T00:00:00Z",
        source = PluginInstallSource.Remote,
        sourceRepo = slug,
    )

    private fun asset(tag: String) = GitHubReleaseAsset(
        tagName = tag,
        assetName = "yangtzeu-eams-$tag.zip",
        downloadUrl = "https://github.com/$slug/releases/latest/download/yangtzeu-eams-$tag.zip",
        sizeBytes = 0,
    )

    @Test
    fun `市场缓存还是旧版时，现查到的新版照样算`() {
        val state = PluginMarketUiState(
            marketRepos = listOf(minimalRepo(slug).copy(latestRelease = asset("v1.0.33"))),
            installedPlugins = listOf(record),
            latestReleases = mapOf(slug.lowercase() to asset("v1.0.34")),
        )

        val upgrade = availableUpgrade(record, state)

        assertEquals("v1.0.34", upgrade?.latestRelease?.tagName)
    }

    @Test
    fun `市场里不在列的已装插件也能升级`() {
        val state = PluginMarketUiState(latestReleases = mapOf(slug.lowercase() to asset("v1.0.35")))

        assertEquals(slug, availableUpgrade(record, state)?.fullName)
    }

    @Test
    fun `已经是最新或者更新时不提示`() {
        val state = PluginMarketUiState(latestReleases = mapOf(slug.lowercase() to asset("v1.0.33")))
        assertNull(availableUpgrade(record, state))

        val newer = record.copy(version = "1.0.40")
        assertNull(availableUpgrade(newer, PluginMarketUiState(latestReleases = mapOf(slug.lowercase() to asset("v1.0.35")))))
    }

    @Test
    fun `本地装的没记来源仓库时查不了，不提示`() {
        assertNull(availableUpgrade(record.copy(sourceRepo = null), PluginMarketUiState()))
    }

    @Test
    fun `版本号统一加 v 前缀`() {
        assertEquals("v1.0.34", displayVersion("1.0.34"))
        assertEquals("v1.0.34", displayVersion("v1.0.34"))
    }
}
