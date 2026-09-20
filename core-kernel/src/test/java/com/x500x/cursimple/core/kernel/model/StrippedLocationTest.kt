package com.x500x.cursimple.core.kernel.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 课表格子里的地点该显示什么。
 *
 * 一份课表就一所学校，格子里写「长江大学」等于没写。之前剥完为空时会原样退回学校名，
 * 于是那几门只填了学校名的课，格子里就一直挂着「@长江大学」。
 */
class StrippedLocationTest {

    private val suffix = "长江大学"

    @Test
    fun `只写了学校名时不显示地点`() {
        assertNull(strippedLocationOrNull("长江大学", suffix))
    }

    @Test
    fun `带校区的纯学校名同样不显示`() {
        assertNull(strippedLocationOrNull("长江大学东校区", suffix))
    }

    @Test
    fun `学校名后面还有教室时只留教室`() {
        assertEquals("东13-C-315", strippedLocationOrNull("长江大学东13-C-315", suffix))
    }

    @Test
    fun `没有学校名的地点原样显示`() {
        assertEquals("实验东5教101", strippedLocationOrNull("实验东5教101", suffix))
    }

    @Test
    fun `地点为空时不显示`() {
        assertNull(strippedLocationOrNull("", suffix))
        assertNull(strippedLocationOrNull("   ", suffix))
    }

    @Test
    fun `没认出学校名时照常显示原文`() {
        // 整份课表只有一处地点时认不出共有的学校名，这时不该把地点吞掉
        assertEquals("长江大学", strippedLocationOrNull("长江大学", suffix = ""))
    }
}
