package com.x500x.cursimple.core.data

import org.junit.Assert.assertEquals
import org.junit.Test

class MarketSourcesPreferencesTest {
    @Test fun `new users get both public defaults`() {
        assertEquals(listOf(DEFAULT_PLUGIN_REGISTRY_REPO), restoredPluginSources(null, null))
        assertEquals(listOf(DEFAULT_COMPONENT_REGISTRY_REPO), UserPreferences().componentSources)
    }

    @Test fun `custom source from old version is retained alongside public default`() {
        assertEquals(listOf(DEFAULT_PLUGIN_REGISTRY_REPO, "me/private"), restoredPluginSources(null, "https://github.com/me/private.git"))
        assertEquals(listOf(DEFAULT_PLUGIN_REGISTRY_REPO), restoredPluginSources(null, DEFAULT_PLUGIN_REGISTRY_REPO))
    }

    @Test fun `explicitly removing every source remains empty after reload`() {
        assertEquals(emptyList<String>(), restoredPluginSources("", "me/old"))
        assertEquals(emptyList<String>(), decodeMarketSources(encodeMarketSources(emptyList())))
    }

    @Test fun `source lists preserve order while normalizing links and removing duplicate addresses`() {
        val input = listOf(DEFAULT_PLUGIN_REGISTRY_REPO, "https://github.com/Me/private/tree/main", "me/PRIVATE.git", "https://other.example/me/plugin")
        val expected = listOf(DEFAULT_PLUGIN_REGISTRY_REPO, "Me/private")
        assertEquals(expected, decodeMarketSources(encodeMarketSources(input)))
    }
}
