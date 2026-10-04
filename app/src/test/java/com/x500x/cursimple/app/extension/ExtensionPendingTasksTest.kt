package com.x500x.cursimple.app.extension

import com.x500x.cursimple.core.plugin.manifest.PluginFeedTypeSpec
import com.x500x.cursimple.feature.plugin.extension.ExtensionData
import com.x500x.cursimple.feature.plugin.extension.ExtensionFeedItem
import com.x500x.cursimple.feature.plugin.extension.ExtensionLoginState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExtensionPendingTasksTest {
    private val types = listOf(
        PluginFeedTypeSpec("zuoye", "作业", "#2563EB", kind = "task"),
        PluginFeedTypeSpec("tongzhi", "通知", "#D97706", kind = "notice"),
    )

    private fun data(vararg items: ExtensionFeedItem, state: ExtensionLoginState = ExtensionLoginState.LoggedIn) =
        ExtensionData("c", loginState = state, items = items.toList())

    @Test fun `only unfinished current tasks are handed to the widget`() {
        val tasks = pendingTasksOf(
            "c", "某组件",
            data(
                ExtensionFeedItem("1", type = "zuoye", title = " 第三章 ", course = "数据结构", dueAt = 100L, kind = "task"),
                ExtensionFeedItem("2", type = "zuoye", title = "已交", dueAt = 100L, done = true, kind = "task"),
                ExtensionFeedItem("3", type = "zuoye", title = "往期", dueAt = 100L, historical = true, kind = "task"),
                ExtensionFeedItem("4", type = "tongzhi", title = "调课", publishAt = 50L, kind = "notice"),
            ),
            types,
        )
        val task = tasks.single()
        assertEquals("c/1", task.id)
        assertEquals("第三章", task.title)
        assertEquals("作业", task.typeLabel)
        assertEquals("数据结构", task.group)
        assertEquals(0xFF2563EBL, task.colorArgb)
        assertEquals("某组件", task.sourceTitle)
    }

    @Test fun `role comes from the component declaration, not the type name`() {
        val tasks = pendingTasksOf(
            "c", "某组件",
            data(ExtensionFeedItem("1", type = "homework", title = "声明成通知", dueAt = 100L, kind = "notice")),
            emptyList(),
        )
        assertTrue(tasks.isEmpty())
    }

    @Test fun `signed out components contribute nothing while expired sessions keep last results`() {
        val item = ExtensionFeedItem("1", type = "zuoye", title = "作业", dueAt = 100L, kind = "task")
        assertTrue(pendingTasksOf("c", "某组件", data(item, state = ExtensionLoginState.LoggedOut), types).isEmpty())
        assertTrue(pendingTasksOf("c", "某组件", data(item, state = ExtensionLoginState.Never), types).isEmpty())
        assertEquals(1, pendingTasksOf("c", "某组件", data(item, state = ExtensionLoginState.Expired), types).size)
    }
}
