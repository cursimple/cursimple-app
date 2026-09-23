package com.x500x.cursimple.feature.plugin

import com.x500x.cursimple.core.plugin.install.InstalledPluginRecord
import com.x500x.cursimple.core.plugin.install.PluginInstallSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PluginMarketInstallStateTest {

    private fun record(
        pluginId: String = "p1",
        version: String = "1.0.33",
        sourceRepo: String? = "cursimple/YangtzU_course_plugin",
    ) = InstalledPluginRecord(
        pluginId = pluginId,
        name = pluginId,
        version = version,
        versionCode = 1,
        storagePath = "/tmp/$pluginId",
        installedAt = "2026-09-03T00:00:00Z",
        source = PluginInstallSource.Remote,
        sourceRepo = sourceRepo,
    )

    @Test
    fun `a repo with no matching install is not installed`() {
        val state = resolveRepoInstallState("cursimple/other", "v1.0.33", listOf(record()))

        assertEquals(PluginRepoInstallState.NotInstalled, state)
        assertNull(state.installedRecord)
    }

    @Test
    fun `the same version counts as installed`() {
        val state = resolveRepoInstallState(
            "cursimple/YangtzU_course_plugin",
            "v1.0.33",
            listOf(record(version = "1.0.33")),
        )

        assertTrue(state is PluginRepoInstallState.Installed)
        assertEquals("1.0.33", state.installedRecord?.version)
    }

    @Test
    fun `a newer market version offers an update`() {
        val state = resolveRepoInstallState(
            "cursimple/YangtzU_course_plugin",
            "v1.0.34",
            listOf(record(version = "1.0.33")),
        )

        assertEquals(PluginRepoInstallState.Updatable(record(version = "1.0.33"), "v1.0.34"), state)
    }

    @Test
    fun `the v prefix does not make an identical version look newer`() {
        // 市场标签带 v，清单里不带，直接比字符串会一直显示可更新
        listOf("v1.0.33" to "1.0.33", "1.0.33" to "1.0.33", "V1.0.33" to "1.0.33").forEach { (tag, installed) ->
            val state = resolveRepoInstallState("a/b", tag, listOf(record(version = installed, sourceRepo = "a/b")))
            assertTrue("$tag vs $installed", state is PluginRepoInstallState.Installed)
        }
    }

    @Test
    fun `the repo slug is matched case insensitively`() {
        val state = resolveRepoInstallState(
            "CurSimple/yangtzu_course_plugin",
            "v1.0.33",
            listOf(record(sourceRepo = "cursimple/YangtzU_course_plugin")),
        )

        assertTrue(state is PluginRepoInstallState.Installed)
    }

    @Test
    fun `an install without a recorded repo is not claimed by any repo`() {
        val state = resolveRepoInstallState("a/b", "v1", listOf(record(sourceRepo = null)))

        assertEquals(PluginRepoInstallState.NotInstalled, state)
    }

    @Test
    fun `a repo with no release still reports installed`() {
        val state = resolveRepoInstallState("a/b", null, listOf(record(sourceRepo = "a/b")))

        assertTrue(state is PluginRepoInstallState.Installed)
    }

    @Test
    fun `版本号按数字逐段比，1点0点10 比 1点0点9 新`() {
        assertTrue(isNewerVersion("v1.0.10", "1.0.9"))
        assertTrue(isNewerVersion("1.0.35", "1.0.34"))
        assertTrue(isNewerVersion("2.0", "1.9.9"))
    }

    @Test
    fun `市场版本不比本机新时不提示更新，免得一点就降级`() {
        assertEquals(false, isNewerVersion("v1.0.33", "1.0.34"))
        assertEquals(false, isNewerVersion("v1.0.34", "1.0.34"))
        val state = resolveRepoInstallState("a/b", "v1.0.33", listOf(record(version = "1.0.34", sourceRepo = "a/b")))
        assertTrue(state is PluginRepoInstallState.Installed)
    }

    @Test
    fun `市场版本更新时标成可更新`() {
        val state = resolveRepoInstallState("a/b", "v1.0.35", listOf(record(version = "1.0.34", sourceRepo = "a/b")))
        assertTrue(state is PluginRepoInstallState.Updatable)
    }
}
