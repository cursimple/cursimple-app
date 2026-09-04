package com.x500x.cursimple.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsTimeZoneChoiceTest {

    private fun choice(
        id: String,
        name: String,
        offsetSeconds: Int,
        offsetLabel: String = "GMT+00:00",
        cityName: String = "",
    ) = ZoneChoice(
        id = id,
        cityName = cityName,
        displayName = name,
        offsetLabel = offsetLabel,
        offsetSeconds = offsetSeconds,
    )

    @Test
    fun `zones are ordered by offset first`() {
        val sorted = sortZoneChoices(
            listOf(
                choice("Asia/Shanghai", "中国标准时间", 8 * 3600),
                choice("Europe/London", "格林尼治标准时间", 0),
            ),
        )

        assertEquals(listOf("Europe/London", "Asia/Shanghai"), sorted.map { it.id })
    }

    @Test
    fun `names inside one offset follow the given comparator`() {
        // 中文按字符码排出来的顺序读起来是乱的，实际使用 Collator 按拼音排
        val sorted = sortZoneChoices(
            listOf(
                choice("Asia/Shanghai", "中国标准时间", 8 * 3600),
                choice("Asia/Taipei", "台北标准时间", 8 * 3600),
            ),
            nameComparator = java.text.Collator.getInstance(java.util.Locale.SIMPLIFIED_CHINESE)
                .let { collator -> Comparator { a, b -> collator.compare(a, b) } },
        )

        assertEquals(listOf("Asia/Taipei", "Asia/Shanghai"), sorted.map { it.id })
    }

    @Test
    fun `the id breaks ties when names are equal`() {
        val sorted = sortZoneChoices(
            listOf(choice("B/b", "同名", 0), choice("A/a", "同名", 0)),
        )

        assertEquals(listOf("A/a", "B/b"), sorted.map { it.id })
    }

    @Test
    fun `negative offsets come first`() {
        val sorted = sortZoneChoices(
            listOf(
                choice("Asia/Shanghai", "中国标准时间", 8 * 3600),
                choice("America/New_York", "北美东部标准时间", -5 * 3600),
            ),
        )

        assertEquals("America/New_York", sorted.first().id)
    }

    @Test
    fun `the query matches the localized name`() {
        val target = choice("Asia/Shanghai", "中国标准时间", 8 * 3600)

        assertTrue(matchesZoneQuery(target, "中国"))
        assertFalse(matchesZoneQuery(target, "东京"))
    }

    @Test
    fun `the query also matches the raw id and the offset`() {
        val target = choice("Asia/Shanghai", "中国标准时间", 8 * 3600, offsetLabel = "GMT+08:00")

        assertTrue(matchesZoneQuery(target, "shanghai"))
        assertTrue(matchesZoneQuery(target, "Asia"))
        assertTrue(matchesZoneQuery(target, "+08"))
    }

    @Test
    fun `the query matches the localized city name`() {
        // 中文用户搜的是城市名，它既不等于时区标准名也不在 id 里
        val target = choice("Asia/Shanghai", "中国标准时间", 8 * 3600, cityName = "上海")

        assertTrue(matchesZoneQuery(target, "上海"))
    }

    @Test
    fun `the city name is preferred as the label`() {
        assertEquals("上海", choice("Asia/Shanghai", "中国标准时间", 0, cityName = "上海").label)
    }

    @Test
    fun `without a city name the zone name is used as the label`() {
        assertEquals("中国标准时间", choice("Asia/Shanghai", "中国标准时间", 0).label)
    }

    @Test
    fun `an empty query matches everything`() {
        val target = choice("Asia/Shanghai", "中国标准时间", 8 * 3600)

        assertTrue(matchesZoneQuery(target, ""))
        assertTrue(matchesZoneQuery(target, "   "))
    }

    @Test
    fun `the id match ignores case`() {
        val target = choice("Asia/Shanghai", "中国标准时间", 8 * 3600)

        assertTrue(matchesZoneQuery(target, "SHANGHAI"))
    }
}
