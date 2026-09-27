package com.x500x.cursimple.app.greeting

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.LocalDate

/** 仓库里的节日节气日期表：App 联网下的就是这一份。 */
class FestivalDatasetTest {

    private val dataset by lazy {
        // 单测在 app 模块目录下跑，数据文件在仓库根目录
        val file = listOf("../data/calendar/cn-festivals.json", "data/calendar/cn-festivals.json")
            .map(::File)
            .first { it.isFile }
        requireNotNull(FestivalDataset.parse(file.readText()))
    }

    @Test
    fun everyIdInTheDatasetHasAGreeting() {
        val unknown = dataset.days.values.flatten().toSet().filterNot(::isKnownDatasetId)
        assertTrue("数据表里有这一版不认识的 id：$unknown", unknown.isEmpty())
    }

    @Test
    fun coversManyYearsAhead() {
        assertTrue(dataset.firstYear <= 2026)
        assertTrue(dataset.lastYear >= 2050)
    }

    @Test
    fun festivalsOnlyOnTheDayItself() {
        assertEquals(listOf("national_day"), dataset.days["2026-10-01"])
        // 国庆假期里的其它几天不算
        assertNull(dataset.days["2026-10-02"])
        assertEquals(listOf("spring_festival"), dataset.days["2026-02-17"])
        assertEquals(listOf("new_years_eve"), dataset.days["2026-02-16"])
        assertEquals(listOf("mid_autumn"), dataset.days["2026-09-25"])
    }

    @Test
    fun agreesWithTheLocalFallbackForSolarFestivalsAndTerms() {
        var day = LocalDate.of(2025, 1, 1)
        val end = LocalDate.of(2030, 12, 31)
        while (!day.isAfter(end)) {
            val ids = dataset.days[day.toString()].orEmpty()
            // 公历节日（农历的交给系统 ICU，单测环境里没有）
            val local = FestivalCalendar.festivalsOn(day, lunar = null, lunarTomorrow = null)
                .map { datasetIdOf(it.name) }
            local.forEach { assertTrue("$day 缺 $it", it in ids) }
            FestivalCalendar.solarTermOn(day)?.let { term ->
                assertTrue("$day 节气对不上：表里 $ids，本机算的 $term", datasetIdOf(term.name) in ids)
            }
            day = day.plusDays(1)
        }
    }

    @Test
    fun enumNamesMapToSnakeCaseIds() {
        assertEquals("national_day", datasetIdOf("NationalDay"))
        assertEquals("new_years_eve", datasetIdOf("NewYearsEve"))
        assertEquals("awakening_of_insects", datasetIdOf("AwakeningOfInsects"))
        assertNotNull(greetingResForId("start_of_spring"))
        assertNull(greetingResForId("some_future_festival"))
    }

    @Test
    fun rejectsUnknownVersionsAndEmptyTables() {
        assertNull(FestivalDataset.parse("""{"version":2,"firstYear":2024,"lastYear":2060,"days":{"2026-10-01":["national_day"]}}"""))
        assertNull(FestivalDataset.parse("""{"version":1,"firstYear":2024,"lastYear":2060,"days":{}}"""))
        assertNull(FestivalDataset.parse("not json"))
        assertNotNull(FestivalDataset.parse("""{"version":1,"firstYear":2024,"lastYear":2060,"days":{"2026-10-01":["national_day"]}}"""))
    }
}
