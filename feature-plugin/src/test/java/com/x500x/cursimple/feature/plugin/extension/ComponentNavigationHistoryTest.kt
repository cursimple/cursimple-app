package com.x500x.cursimple.feature.plugin.extension

import com.x500x.cursimple.core.plugin.manifest.PluginExtensionUiPage.*
import org.junit.Assert.assertEquals
import org.junit.Test

class ComponentNavigationHistoryTest {
    @Test fun `successful login replaces a root login page`() {
        assertEquals(listOf(Feed), componentNavigationHistory(listOf(Login), Feed, true))
    }
    @Test fun `successful login does not put login behind the feed`() {
        assertEquals(listOf(Settings, Feed), componentNavigationHistory(listOf(Settings, Login), Feed, true))
    }
    @Test fun `existing feed is reused after login`() {
        assertEquals(listOf(Feed), componentNavigationHistory(listOf(Feed, Settings, Login), Feed, true))
    }
    @Test fun `ordinary navigation keeps its previous page`() {
        assertEquals(listOf(Settings, Login), componentNavigationHistory(listOf(Settings), Login, false))
    }
}
