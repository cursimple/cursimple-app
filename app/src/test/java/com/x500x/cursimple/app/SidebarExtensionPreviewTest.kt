package com.x500x.cursimple.app

import org.junit.Assert.assertEquals
import org.junit.Test

class SidebarExtensionPreviewTest {
    private val entries = (1..5).map { "k$it" to "组件$it" }

    @Test fun `三个以内全部显示`() {
        val preview = sidebarExtensionPreview(entries.take(3), current = null)

        assertEquals(entries.take(3), preview)
    }

    @Test fun `超过三个只显示前三个`() {
        val preview = sidebarExtensionPreview(entries, current = "k2")

        assertEquals(listOf("k1", "k2", "k3"), preview.map { it.first })
    }

    @Test fun `正在打开的组件排在后面时替换第三个保持可见`() {
        val preview = sidebarExtensionPreview(entries, current = "k5")

        assertEquals(listOf("k1", "k2", "k5"), preview.map { it.first })
    }
}
