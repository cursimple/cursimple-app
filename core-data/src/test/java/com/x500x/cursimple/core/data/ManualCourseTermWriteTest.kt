package com.x500x.cursimple.core.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** 合并学期要写「指定学期」的桶，空的学期 id 不能落回旧键。 */
class ManualCourseTermWriteTest {

    @Test
    fun `each term gets its own bucket key`() {
        assertEquals("manual_courses_json__term-a", coursesBucketKey("term-a"))
        assertEquals("manual_courses_json__term-b", coursesBucketKey("term-b"))
    }

    @Test
    fun `no term means the legacy bucket`() {
        assertEquals("manual_courses_json", coursesBucketKey(""))
    }

    @Test
    fun `writing a named term is refused for a blank id`() {
        assertNull(writableCoursesKey(""))
        assertEquals("manual_courses_json__term-a", writableCoursesKey("term-a"))
    }
}
