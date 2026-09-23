package com.x500x.cursimple.core.kernel.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 课程节号落在作息表的哪几个时段：小组件和通知据此显示「第一节」「午间课」。 */
class SlotsCoveringTest {

    private val profile = TermTimingProfile(
        termStartDate = "2026-09-07",
        // 故意打乱顺序：序号要按节次先后算，和课表左侧的节次栏一致
        slotTimes = listOf(
            ClassSlotTime(4, 5, "10:05", "11:40", label = "第二节"),
            ClassSlotTime(1, 2, "08:00", "09:35", label = "第一节"),
            ClassSlotTime(3, 3, "12:00", "13:35", label = "午间课"),
        ),
    )

    @Test
    fun `课程正好占一个时段`() {
        val covering = profile.slotsCovering(1, 2)

        assertEquals(listOf("第一节"), covering.map { it.value.label })
        assertEquals(0, covering.single().index)
    }

    @Test
    fun `课程跨几个时段时按节次先后全部给出`() {
        val covering = profile.slotsCovering(2, 4)

        assertEquals(listOf("第一节", "午间课", "第二节"), covering.map { it.value.label })
        assertEquals(listOf(0, 1, 2), covering.map { it.index })
    }

    @Test
    fun `作息表里没有对应时段时为空`() {
        assertTrue(profile.slotsCovering(9, 10).isEmpty())
        assertTrue(profile.slotsCovering(3, 1).isEmpty())
    }
}
