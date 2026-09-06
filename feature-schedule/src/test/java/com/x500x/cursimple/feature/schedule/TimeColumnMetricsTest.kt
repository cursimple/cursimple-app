package com.x500x.cursimple.feature.schedule

import org.junit.Assert.assertEquals
import org.junit.Test

class LabelWidthInCharsTest {
    @Test
    fun `each Chinese character counts as one`() {
        assertEquals(3f, labelWidthInChars("第一节"), 0.001f)
        assertEquals(4f, labelWidthInChars("第一大节"), 0.001f)
    }

    @Test
    fun `ascii characters are narrower`() {
        assertEquals(4.8f, labelWidthInChars("Period 1"), 0.001f)
        assertEquals(0.6f, labelWidthInChars("1"), 0.001f)
    }

    @Test
    fun `a mixed label adds both up`() {
        assertEquals(2.6f, labelWidthInChars("第1节"), 0.001f)
    }

    @Test
    fun `an empty label takes no width`() {
        assertEquals(0f, labelWidthInChars(""), 0.001f)
    }
}

class TimeColumnLabelCharsTest {
    @Test
    fun `the longest label decides the width`() {
        val chars = timeColumnLabelChars(listOf("第一节", "第一大节", "午间课"))

        assertEquals(4f, chars, 0.001f)
    }

    @Test
    fun `short labels still keep a floor so the column stays readable`() {
        assertEquals(TIME_COLUMN_MIN_LABEL_CHARS, timeColumnLabelChars(listOf("1", "2")), 0.001f)
    }

    @Test
    fun `an overlong label is capped and left to the ellipsis`() {
        val chars = timeColumnLabelChars(listOf("上午第一大节课程"))

        assertEquals(TIME_COLUMN_MAX_LABEL_CHARS, chars, 0.001f)
    }

    @Test
    fun `no labels falls back to the floor`() {
        assertEquals(TIME_COLUMN_MIN_LABEL_CHARS, timeColumnLabelChars(emptyList()), 0.001f)
    }
}
