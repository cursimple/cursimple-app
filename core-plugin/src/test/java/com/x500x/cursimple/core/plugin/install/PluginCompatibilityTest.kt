package com.x500x.cursimple.core.plugin.install

import com.x500x.cursimple.core.plugin.PluginApiVersion
import com.x500x.cursimple.core.plugin.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PluginCompatibilityTest {

    @Test
    fun `an undeclared api version cannot be called compatible`() {
        val result = resolvePluginCompatibility(null)

        assertEquals(PluginCompatibilityStatus.Incompatible, result.status)
        assertEquals(R.string.plugin_error_compatibility_api_undeclared, result.messageRes)
    }

    @Test
    fun `a non positive api version is rejected`() {
        assertEquals(PluginCompatibilityStatus.Incompatible, resolvePluginCompatibility(0).status)
        assertEquals(PluginCompatibilityStatus.Incompatible, resolvePluginCompatibility(-1).status)
        assertEquals(
            R.string.plugin_error_compatibility_api_invalid,
            resolvePluginCompatibility(0).messageRes,
        )
    }

    @Test
    fun `an api version newer than the platform is rejected`() {
        val result = resolvePluginCompatibility(PluginApiVersion.CURRENT + 1)

        assertEquals(PluginCompatibilityStatus.Incompatible, result.status)
        assertEquals(R.string.plugin_error_compatibility_api_too_new, result.messageRes)
        assertEquals(
            listOf(PluginApiVersion.CURRENT + 1, PluginApiVersion.CURRENT),
            result.messageArgs,
        )
    }

    @Test
    fun `the current api version is compatible`() {
        val result = resolvePluginCompatibility(PluginApiVersion.CURRENT)

        assertEquals(PluginCompatibilityStatus.Compatible, result.status)
        assertNull(result.messageRes)
    }

    @Test
    fun `an api version below the current one stays supported`() {
        val older = PluginApiVersion.CURRENT - 1
        if (older >= 1) {
            assertEquals(PluginCompatibilityStatus.Compatible, resolvePluginCompatibility(older).status)
        }
    }

    @Test fun `a newer minimum host version is blocked with the actual APK version`() {
        val result = resolveHostVersionCompatibility("0.7.6", "0.7.5-ci")
        assertEquals(PluginCompatibilityStatus.Incompatible, result.status)
        assertEquals(R.string.plugin_error_compatibility_host_too_old, result.messageRes)
        assertEquals(listOf("0.7.6", "0.7.5-ci"), result.messageArgs)
    }

    @Test fun `host versions compare numerically and preserve prerelease ordering`() {
        for ((minimum, current) in listOf("0.7.5" to "0.7.5-ci", "0.9.0" to "0.10.0",
            "0.7.5-beta.9" to "v0.7.5-beta.10+build.2", "0.7.5-beta.10" to "0.7.5", "0.7" to "0.7.0")) {
            assertEquals("$current must satisfy $minimum", PluginCompatibilityStatus.Compatible,
                resolveHostVersionCompatibility(minimum, current).status)
        }
        for ((minimum, current) in listOf("0.10.0" to "0.9.0", "0.7.5" to "0.7.5-beta.10",
            "0.7.5-beta.10" to "0.7.5-beta.9", "0.7.5-beta" to "0.7.5-alpha")) {
            assertEquals("$current must not satisfy $minimum", PluginCompatibilityStatus.Incompatible,
                resolveHostVersionCompatibility(minimum, current).status)
        }
    }

    @Test fun `malformed requirements and unverified APK versions fail closed`() {
        for (minimum in listOf("", "latest", "0.7.invalid", "999999999999999999999999.0.0")) {
            assertEquals(R.string.plugin_error_compatibility_host_invalid,
                resolveHostVersionCompatibility(minimum, "0.7.5").messageRes)
        }
        assertEquals(R.string.plugin_error_compatibility_host_unknown,
            resolveHostVersionCompatibility("0.1.0", "").messageRes)
    }
}
