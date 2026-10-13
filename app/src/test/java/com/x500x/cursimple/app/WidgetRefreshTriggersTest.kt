package com.x500x.cursimple.app

import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class WidgetRefreshTriggersTest {
    @Test
    fun `starting the process does not redraw widgets that already show the current state`() = runBlocking {
        assertEquals(emptyList<String?>(), flowOf<String?>(null).widgetRefreshes { it != null }.toList())
        assertEquals(emptyList<List<Int>>(), flowOf(listOf(1, 2)).widgetRefreshes().toList())
    }

    @Test
    fun `only real changes after start redraw widgets`() = runBlocking {
        assertEquals(listOf("Asia/Tokyo", null), flowOf(null, null, "Asia/Tokyo", "Asia/Tokyo", null).widgetRefreshes { it != null }.toList())
        assertEquals(listOf(listOf(3)), flowOf(listOf(1), listOf(1), listOf(3)).widgetRefreshes().toList())
    }

    @Test
    fun `a non-default starting value still redraws once in case a render raced ahead of it`() = runBlocking {
        assertEquals(listOf("Asia/Tokyo"), flowOf("Asia/Tokyo").widgetRefreshes { it != null }.toList())
    }
}
