package com.x500x.cursimple.app.util

import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class ScheduleWakeUpImporterTest {
    @Test
    fun `official five-line export joins names and keeps term weeks locations and clocks`() {
        val payload = ScheduleWakeUpImporter.parse(officialPackage().byteInputStream())
        assertEquals("2026秋季", payload.termName)
        assertEquals("2026-09-07", payload.termStartDate)
        val courses = payload.schedule!!.dailySchedules.flatMap { it.courses }
        assertEquals(2, courses.size)
        val math = courses.single { it.title == "高数" }
        assertEquals("Alice", math.teacher)
        assertEquals("A101", math.location)
        assertEquals(1, math.time.dayOfWeek)
        assertEquals(1, math.time.startNode)
        assertEquals(2, math.time.endNode)
        assertEquals(listOf(1, 3), math.weeks)
        assertEquals("08:00", math.reminderStartTime)
        assertEquals("09:40", math.reminderEndTime)
        val physics = courses.single { it.title == "物理" }
        assertEquals("Bob", physics.teacher)
        assertEquals("B202", physics.location)
        assertEquals(listOf(2, 4), physics.weeks)
        assertEquals("10:00", physics.reminderStartTime)
        assertEquals("11:40", physics.reminderEndTime)
    }

    @Test
    fun `official exports accept UTF8 BOM CRLF and a final newline`() {
        val text = "\uFEFF" + officialPackage().replace("\n", "\r\n") + "\r\n"
        val payload = ScheduleWakeUpImporter.parseJson(text)
        assertEquals("2026秋季", payload.termName)
        assertEquals(2, payload.schedule!!.dailySchedules.flatMap { it.courses }.size)
    }

    @Test
    fun `multiple details for one course retain separate days and rooms`() {
        val detail = """{"id":1,"tableId":1,"day":5,"startNode":3,"step":2,"startWeek":1,"endWeek":4,"type":0,"room":"C303","teacher":"Carol"}"""
        val text = officialPackage(details = "[$MATH_DETAIL,$detail]")
        val courses = ScheduleWakeUpImporter.parseJson(text).schedule!!.dailySchedules.flatMap { it.courses }
        assertEquals(2, courses.size)
        assertTrue(courses.all { it.title == "高数" })
        assertEquals(setOf("A101", "C303"), courses.map { it.location }.toSet())
        assertEquals(listOf(1, 2, 3, 4), courses.single { it.location == "C303" }.weeks)
    }

    @Test
    fun `course references include table id and missing references fail`() {
        val wrongTable = MATH_DETAIL.replace("\"tableId\":1", "\"tableId\":2")
        assertFailure("课程时间缺少对应课程", officialPackage(details = "[$wrongTable]"))
    }

    @Test
    fun `flat and wrapped JSON exports remain supported`() {
        val flat = """[{"name":"Math","day":2,"startNode":3,"step":2,"weeks":[2,4]}]"""
        val wrapped = """{
            "courses": $flat,
            "setting": {"tableName":"Autumn","startDate":"2026-09-07"}
        }""".trimIndent()
        for (text in listOf(flat, wrapped)) {
            val course = ScheduleWakeUpImporter.parseJson(text).schedule!!.dailySchedules.flatMap { it.courses }.single()
            assertEquals("Math", course.title)
            assertEquals(2, course.time.dayOfWeek)
            assertEquals(4, course.time.endNode)
            assertEquals(listOf(2, 4), course.weeks)
            assertNull(course.reminderStartTime)
        }
    }

    @Test
    fun `official text and legacy JSON can be read from zip packages`() {
        for ((name, text) in listOf(
            "course.wakeup_schedule" to officialPackage(),
            "schedule.json" to """[{"name":"Math"}]""",
        )) {
            val payload = ScheduleWakeUpImporter.parse(zip(name, text).inputStream())
            assertTrue(payload.schedule!!.dailySchedules.flatMap { it.courses }.isNotEmpty())
        }
    }

    @Test
    fun `official package enforces bytes depth and both course array limits`() {
        assertFailure("导入文件过大", officialPackage() + " ".repeat(ScheduleImportLimits.MAX_BYTES))
        val deep = "[".repeat(65) + "0" + "]".repeat(65)
        assertFailure("JSON 嵌套过深", officialPackage(times = deep))
        val manyBases = List(ScheduleImportLimits.MAX_COURSES + 1) { MATH_BASE }.joinToString(",", "[", "]")
        assertFailure("课程数量过多", officialPackage(bases = manyBases))
        val manyDetails = List(ScheduleImportLimits.MAX_COURSES + 1) { MATH_DETAIL }.joinToString(",", "[", "]")
        assertFailure("课程数量过多", officialPackage(details = manyDetails))
    }

    @Test
    fun `shared course names cannot expand beyond the import byte limit`() {
        val longBase = MATH_BASE.replace("高数", "M".repeat(ScheduleImportLimits.MAX_BYTES / 2 + 1))
        val text = officialPackage(bases = "[$longBase]", details = "[$MATH_DETAIL,$MATH_DETAIL]")
        assertTrue(text.toByteArray().size < ScheduleImportLimits.MAX_BYTES)
        assertFailure("导入文件过大", text)
    }

    @Test
    fun `zip limits still cover decompressed bytes and skipped entries`() {
        val oversized = zip("course.wakeup_schedule", officialPackage() + " ".repeat(ScheduleImportLimits.MAX_BYTES))
        val sizeError = runCatching { ScheduleWakeUpImporter.parse(oversized.inputStream()) }.exceptionOrNull()
        assertEquals("导入文件过大", sizeError?.message)
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { zip ->
            repeat(128) { index ->
                zip.putNextEntry(ZipEntry("padding-$index"))
                zip.closeEntry()
            }
            zip.putNextEntry(ZipEntry("course.wakeup_schedule"))
            zip.write(officialPackage().toByteArray())
            zip.closeEntry()
        }
        val entryError = runCatching { ScheduleWakeUpImporter.parse(output.toByteArray().inputStream()) }.exceptionOrNull()
        assertEquals("课程包条目过多", entryError?.message)
    }

    @Test
    fun `truncated or extra official records fail`() {
        val lines = officialPackage().lines()
        assertFailure("无效的 WakeUp 多行课程包", lines.take(4).joinToString("\n"))
        assertFailure("无效的 WakeUp 多行课程包", officialPackage() + "\n{}")
    }

    private fun assertFailure(message: String, text: String) {
        val error = runCatching { ScheduleWakeUpImporter.parseJson(text) }.exceptionOrNull()
        assertEquals(message, error?.message)
    }

    private fun zip(name: String, text: String): ByteArray {
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use {
            it.putNextEntry(ZipEntry(name))
            it.write(text.toByteArray())
            it.closeEntry()
        }
        return output.toByteArray()
    }

    // WakeUp 3.612 ScheduleViewModel.exportData writes these five independent JSON values.
    // https://github.com/Matey-ace/wakeup-course-schedule/blob/main/app/src/main/java/com/suda/yzune/wakeupschedule/schedule/ScheduleViewModel.kt#L144
    private fun officialPackage(
        bases: String = "[$PHYSICS_BASE,$MATH_BASE]",
        details: String = "[$MATH_DETAIL,$PHYSICS_DETAIL]",
        times: String = """[{"node":1,"startTime":"08:00","endTime":"08:45","timeTable":1},{"node":2,"startTime":"08:55","endTime":"09:40","timeTable":1},{"node":3,"startTime":"10:00","endTime":"10:45","timeTable":1},{"node":4,"startTime":"10:55","endTime":"11:40","timeTable":1}]""",
    ): String = listOf(
        """{"id":1,"name":"Default","sameLen":true,"courseLen":45}""",
        times,
        """{"id":1,"tableName":"2026秋季","nodes":4,"timeTable":1,"startDate":"2026-09-07","maxWeek":20}""",
        bases,
        details,
    ).joinToString("\n")

    private companion object {
        const val MATH_BASE = """{"id":1,"tableId":1,"courseName":"高数","color":"#2196F3"}"""
        const val PHYSICS_BASE = """{"id":2,"tableId":1,"courseName":"物理","color":"#FF9800"}"""
        const val MATH_DETAIL = """{"id":1,"tableId":1,"day":1,"startNode":1,"step":2,"startWeek":1,"endWeek":4,"type":1,"room":"A101","teacher":"Alice"}"""
        const val PHYSICS_DETAIL = """{"id":2,"tableId":1,"day":3,"startNode":3,"step":2,"startWeek":1,"endWeek":4,"type":2,"room":"B202","teacher":"Bob"}"""
    }
}
