package com.x500x.cursimple.core.plugin.install

import com.x500x.cursimple.core.plugin.PluginApiVersion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class PluginCompatibilityTest {

    @Test
    fun `an undeclared api version cannot be called compatible`() {
        val result = resolvePluginCompatibility(null)

        assertEquals(PluginCompatibilityStatus.Incompatible, result.status)
        assertNotNull(result.message)
    }

    @Test
    fun `a non positive api version is rejected`() {
        assertEquals(PluginCompatibilityStatus.Incompatible, resolvePluginCompatibility(0).status)
        assertEquals(PluginCompatibilityStatus.Incompatible, resolvePluginCompatibility(-1).status)
    }

    @Test
    fun `an api version newer than the platform is rejected`() {
        val result = resolvePluginCompatibility(PluginApiVersion.CURRENT + 1)

        assertEquals(PluginCompatibilityStatus.Incompatible, result.status)
        assertNotNull(result.message)
    }

    @Test
    fun `the current api version is compatible`() {
        val result = resolvePluginCompatibility(PluginApiVersion.CURRENT)

        assertEquals(PluginCompatibilityStatus.Compatible, result.status)
        assertNull(result.message)
    }

    @Test
    fun `an api version below the current one stays supported`() {
        val older = PluginApiVersion.CURRENT - 1
        if (older >= 1) {
            assertEquals(PluginCompatibilityStatus.Compatible, resolvePluginCompatibility(older).status)
        }
    }
}
