package com.x500x.cursimple.core.kernel.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Compact course cells omit institution-only locations while complete details remain available.
 */
class StrippedLocationTest {

    private val suffix = "示例大学"

    @Test
    fun `只写了学校名时不显示地点`() {
        assertNull(strippedLocationOrNull("示例大学", suffix))
    }

    @Test
    fun `带校区的纯学校名同样不显示`() {
        assertNull(strippedLocationOrNull("示例大学东校区", suffix))
    }

    @Test
    fun `学校名后面还有教室时只留教室`() {
        assertEquals("东13-C-315", strippedLocationOrNull("示例大学东13-C-315", suffix))
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
        // Preserve the location when too few entries establish a shared institution.
        assertEquals("示例大学", strippedLocationOrNull("示例大学", suffix = ""))
    }
}
