package com.x500x.cursimple.core.data.note

import com.x500x.cursimple.core.kernel.model.CourseItem
import com.x500x.cursimple.core.kernel.model.CourseTimeSlot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CourseNoteTest {

    private fun course(
        id: String,
        title: String,
        dayOfWeek: Int = 1,
        startNode: Int = 1,
        endNode: Int = 2,
        weeks: List<Int> = listOf(1, 2, 3),
        teacher: String = "",
        location: String = "",
    ) = CourseItem(
        id = id,
        title = title,
        teacher = teacher,
        location = location,
        weeks = weeks,
        time = CourseTimeSlot(dayOfWeek = dayOfWeek, startNode = startNode, endNode = endNode),
    )

    private fun note(course: CourseItem, text: String, updatedAt: Long = 1L) =
        CourseNote(anchor = course.noteAnchor(), text = text, updatedAt = updatedAt)

    @Test
    fun `note binds to the course with the same id`() {
        val math = course("manual-1", "高等数学")
        val english = course("manual-2", "大学英语", dayOfWeek = 2)
        val notes = listOf(note(math, "带教材第二册"))

        val index = resolveCourseNotes(listOf(math, english), notes)

        assertEquals("带教材第二册", index.textOf("manual-1"))
        assertEquals("", index.textOf("manual-2"))
        assertTrue(index.orphans.isEmpty())
    }

    @Test
    fun `note survives a sync that changes the plugin course id`() {
        val before = course("plugin-aaaa", "高等数学", location = "A101")
        val notes = listOf(note(before, "老师说下周小测"))
        // 插件重新下发同一门课，自动生成的 id 变了
        val after = before.copy(id = "plugin-bbbb")

        val index = resolveCourseNotes(listOf(after), notes)

        assertEquals("老师说下周小测", index.textOf("plugin-bbbb"))
        assertTrue(index.orphans.isEmpty())
    }

    @Test
    fun `note follows the course when teacher and location change`() {
        val before = course("plugin-aaaa", "高等数学", teacher = "张老师", location = "A101")
        val notes = listOf(note(before, "教室临时换到 B302"))
        val after = before.copy(id = "plugin-bbbb", teacher = "李老师", location = "B302")

        val index = resolveCourseNotes(listOf(after), notes)

        assertEquals("教室临时换到 B302", index.textOf("plugin-bbbb"))
    }

    @Test
    fun `same title at a different time does not steal the note`() {
        val monday = course("plugin-aaaa", "高等数学", dayOfWeek = 1, startNode = 1, endNode = 2)
        val notes = listOf(note(monday, "周一这节要点名"))
        val wednesday = course("plugin-cccc", "高等数学", dayOfWeek = 3, startNode = 3, endNode = 4)

        val index = resolveCourseNotes(listOf(wednesday), notes)

        assertEquals("", index.textOf("plugin-cccc"))
        assertEquals(1, index.orphans.size)
    }

    @Test
    fun `two same titled courses in one slot are told apart by weeks`() {
        val odd = course("old-odd", "体育", dayOfWeek = 3, startNode = 3, endNode = 4, weeks = listOf(1, 3, 5))
        val even = course("old-even", "体育", dayOfWeek = 3, startNode = 3, endNode = 4, weeks = listOf(2, 4, 6))
        val notes = listOf(note(odd, "单周带球拍"), note(even, "双周游泳"))

        val index = resolveCourseNotes(
            listOf(odd.copy(id = "new-odd"), even.copy(id = "new-even")),
            notes,
        )

        assertEquals("单周带球拍", index.textOf("new-odd"))
        assertEquals("双周游泳", index.textOf("new-even"))
        assertTrue(index.orphans.isEmpty())
    }

    @Test
    fun `teacher breaks the tie when weeks are identical`() {
        val a = course("old-a", "思想道德", teacher = "张老师")
        val b = course("old-b", "思想道德", teacher = "李老师")
        val notes = listOf(note(a, "张老师爱点名"), note(b, "李老师不点名"))

        val index = resolveCourseNotes(listOf(b.copy(id = "new-b"), a.copy(id = "new-a")), notes)

        assertEquals("张老师爱点名", index.textOf("new-a"))
        assertEquals("李老师不点名", index.textOf("new-b"))
    }

    @Test
    fun `indistinguishable courses still keep the notes visible`() {
        val first = course("old-1", "体育")
        val second = course("old-2", "体育")
        val notes = listOf(note(first, "备注一"), note(second, "备注二"))

        val index = resolveCourseNotes(
            listOf(first.copy(id = "new-1"), second.copy(id = "new-2")),
            notes,
        )

        assertEquals(setOf("备注一", "备注二"), index.byCourseId.values.map { it.text }.toSet())
        assertTrue(index.orphans.isEmpty())
    }

    @Test
    fun `note of a deleted course becomes an orphan instead of being dropped`() {
        val dropped = course("manual-1", "选修课")
        val kept = course("manual-2", "高等数学", dayOfWeek = 2)
        val notes = listOf(note(dropped, "这门退掉了"), note(kept, "带教材"))

        val index = resolveCourseNotes(listOf(kept), notes)

        assertEquals("带教材", index.textOf("manual-2"))
        assertEquals(listOf("这门退掉了"), index.orphans.map { it.text })
        // 孤儿备注留在存储里，不会被 reconcile 删掉
        assertEquals(2, reconcileCourseNotes(listOf(kept), notes).size)
    }

    @Test
    fun `orphan note is picked up again when the course comes back`() {
        val dropped = course("manual-1", "选修课")
        val notes = reconcileCourseNotes(emptyList(), listOf(note(dropped, "这门还会回来")))

        val index = resolveCourseNotes(listOf(dropped.copy(id = "manual-9")), notes)

        assertEquals("这门还会回来", index.textOf("manual-9"))
    }

    @Test
    fun `reconcile rewrites the anchor to the current course and is idempotent`() {
        val before = course("plugin-aaaa", "高等数学", location = "A101")
        val notes = listOf(note(before, "带教材"))
        val after = before.copy(id = "plugin-bbbb", location = "B302")

        val once = reconcileCourseNotes(listOf(after), notes)

        assertEquals("plugin-bbbb", once.single().anchor.courseId)
        assertEquals("B302", once.single().anchor.location)
        assertEquals(once, reconcileCourseNotes(listOf(after), once))
    }

    @Test
    fun `reconcile drops the oldest orphans once the cap is reached`() {
        val kept = course("manual-live", "高等数学")
        val orphans = (1..5).map {
            note(course("gone-$it", "已删除$it", dayOfWeek = 4, startNode = it, endNode = it), "旧备注$it", updatedAt = it.toLong())
        }
        val notes = orphans + note(kept, "保留", updatedAt = 0L)

        val result = reconcileCourseNotes(listOf(kept), notes, limit = 3)

        assertEquals(3, result.size)
        assertTrue(result.any { it.text == "保留" })
        assertEquals(listOf("旧备注4", "旧备注5"), result.filter { it.text != "保留" }.map { it.text })
    }

    @Test
    fun `upsert adds replaces and clears a note`() {
        val math = course("manual-1", "高等数学")
        val courses = listOf(math)

        val added = upsertCourseNote(courses, emptyList(), math, "带教材", updatedAt = 10L)
        assertEquals("带教材", resolveCourseNotes(courses, added).textOf("manual-1"))

        val replaced = upsertCourseNote(courses, added, math, "换成第三册", updatedAt = 20L)
        assertEquals(1, replaced.size)
        assertEquals("换成第三册", resolveCourseNotes(courses, replaced).textOf("manual-1"))

        val cleared = upsertCourseNote(courses, replaced, math, "   ", updatedAt = 30L)
        assertTrue(cleared.isEmpty())
    }

    @Test
    fun `upsert keeps other courses notes untouched`() {
        val math = course("manual-1", "高等数学")
        val english = course("manual-2", "大学英语", dayOfWeek = 2)
        val courses = listOf(math, english)
        val existing = listOf(note(math, "带教材"), note(english, "背单词"))

        val updated = upsertCourseNote(courses, existing, english, "改成听力", updatedAt = 20L)
        val index = resolveCourseNotes(courses, updated)

        assertEquals("带教材", index.textOf("manual-1"))
        assertEquals("改成听力", index.textOf("manual-2"))
    }

    @Test
    fun `hasNote ignores blank text`() {
        val math = course("manual-1", "高等数学")
        val index = resolveCourseNotes(listOf(math), listOf(note(math, "")))

        assertFalse(index.hasNote("manual-1"))
        assertFalse(index.hasNote("manual-2"))
        assertNotNull(index.byCourseId["manual-1"])
    }

    @Test
    fun `validation trims and normalizes line breaks`() {
        val result = validateCourseNote("  第一行\r\n第二行\r  ")

        assertTrue(result is CourseNoteInput.Accepted)
        assertEquals("第一行\n第二行", (result as CourseNoteInput.Accepted).text)
    }

    @Test
    fun `validation accepts text exactly at the limit`() {
        val result = validateCourseNote("字".repeat(COURSE_NOTE_MAX_LENGTH))

        assertTrue(result is CourseNoteInput.Accepted)
    }

    @Test
    fun `validation rejects text over the limit`() {
        val raw = "字".repeat(COURSE_NOTE_MAX_LENGTH + 7)

        val result = validateCourseNote(raw)

        assertTrue(result is CourseNoteInput.TooLong)
        result as CourseNoteInput.TooLong
        assertEquals(COURSE_NOTE_MAX_LENGTH + 7, result.length)
        assertEquals(COURSE_NOTE_MAX_LENGTH, result.limit)
    }

    @Test
    fun `validation counts a surrogate pair as one character`() {
        assertEquals(3, courseNoteLength("a😀b"))
        assertTrue(validateCourseNote("😀".repeat(COURSE_NOTE_MAX_LENGTH)) is CourseNoteInput.Accepted)
        assertTrue(validateCourseNote("😀".repeat(COURSE_NOTE_MAX_LENGTH + 1)) is CourseNoteInput.TooLong)
    }

    @Test
    fun `signature ignores whitespace and case in the title`() {
        assertEquals(
            courseNoteSignature("Advanced Math", 1, 1, 2),
            courseNoteSignature(" advancedmath ", 1, 1, 2),
        )
        assertTrue(
            courseNoteSignature("高等数学", 1, 1, 2) != courseNoteSignature("高等数学", 1, 3, 4),
        )
    }

    @Test
    fun `empty inputs are handled without losing notes`() {
        val math = course("manual-1", "高等数学")
        val notes = listOf(note(math, "带教材"))

        assertEquals(notes, resolveCourseNotes(emptyList(), notes).orphans)
        assertTrue(resolveCourseNotes(listOf(math), emptyList()).byCourseId.isEmpty())
        assertEquals(notes, reconcileCourseNotes(emptyList(), notes))
    }
}
