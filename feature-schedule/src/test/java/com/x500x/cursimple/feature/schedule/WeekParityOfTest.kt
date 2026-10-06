package com.x500x.cursimple.feature.schedule

import org.junit.Assert.assertEquals
import org.junit.Test

class WeekParityOfTest {

    @Test
    fun `空周次按全部周处理`() {
        assertEquals(WeekParity.All, weekParityOf(null))
        assertEquals(WeekParity.All, weekParityOf(emptyList()))
    }

    @Test
    fun `连续周识别为全部周`() {
        assertEquals(WeekParity.All, weekParityOf(listOf(3, 4, 5, 6)))
    }

    @Test
    fun `单双周各自识别`() {
        assertEquals(WeekParity.Odd, weekParityOf(listOf(1, 3, 5, 7)))
        assertEquals(WeekParity.Even, weekParityOf(listOf(2, 4, 6)))
    }

    @Test
    fun `没有规律的周次归为自选`() {
        // Sparse weeks must remain custom rather than becoming a continuous preset range.
        assertEquals(WeekParity.Custom, weekParityOf(listOf(3, 5, 11)))
    }

    @Test
    fun `单周里缺一周也算自选`() {
        assertEquals(WeekParity.Custom, weekParityOf(listOf(1, 3, 7)))
    }

    @Test
    fun `只有一周时按全部周`() {
        assertEquals(WeekParity.All, weekParityOf(listOf(5)))
    }

    @Test
    fun `乱序与重复不影响判定`() {
        assertEquals(WeekParity.Odd, weekParityOf(listOf(5, 1, 3, 3)))
    }

    @Test
    fun `自选周次编码解码往返不变`() {
        val raw = encodeCustomWeeks(listOf(11, 3, 5, 3))
        assertEquals("3,5,11", raw)
        assertEquals(listOf(3, 5, 11), decodeCustomWeeks(raw, maxWeekCount = 20))
    }

    @Test
    fun `超出上限的周次解码时丢掉`() {
        assertEquals(listOf(3, 5), decodeCustomWeeks("3,5,30", maxWeekCount = 20))
    }

    @Test
    fun `矩阵上限取学期总周数与已有最大周次里大的那个`() {
        // Stored course coverage extends the selection matrix beyond configured term length.
        assertEquals(24, customWeekLimit(maxWeekCount = 20, weeks = listOf(3, 24)))
        assertEquals(20, customWeekLimit(maxWeekCount = 20, weeks = listOf(3, 5)))
        assertEquals(20, customWeekLimit(maxWeekCount = 20, weeks = null))
        assertEquals(1, customWeekLimit(maxWeekCount = 0, weeks = null))
    }

    @Test
    fun `坏字符不会让解码崩掉`() {
        assertEquals(listOf(2), decodeCustomWeeks("abc,2,", maxWeekCount = 20))
    }
}
