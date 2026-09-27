package com.x500x.cursimple.app.greeting

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class FestivalCalendarTest {

    @Test
    fun solarTermsOf2026MatchTheCalendar() {
        val expected = mapOf(
            "2026-01-05" to SolarTerm.MinorCold,
            "2026-01-20" to SolarTerm.MajorCold,
            "2026-02-04" to SolarTerm.StartOfSpring,
            "2026-02-18" to SolarTerm.RainWater,
            "2026-03-05" to SolarTerm.AwakeningOfInsects,
            "2026-03-20" to SolarTerm.SpringEquinox,
            "2026-04-05" to SolarTerm.PureBrightness,
            "2026-04-20" to SolarTerm.GrainRain,
            "2026-05-05" to SolarTerm.StartOfSummer,
            "2026-05-21" to SolarTerm.GrainBuds,
            "2026-06-05" to SolarTerm.GrainInEar,
            "2026-06-21" to SolarTerm.SummerSolstice,
            "2026-07-07" to SolarTerm.MinorHeat,
            "2026-07-23" to SolarTerm.MajorHeat,
            "2026-08-07" to SolarTerm.StartOfAutumn,
            "2026-08-23" to SolarTerm.EndOfHeat,
            "2026-09-07" to SolarTerm.WhiteDew,
            "2026-09-23" to SolarTerm.AutumnEquinox,
            "2026-10-08" to SolarTerm.ColdDew,
            "2026-10-23" to SolarTerm.FrostDescent,
            "2026-11-07" to SolarTerm.StartOfWinter,
            "2026-11-22" to SolarTerm.MinorSnow,
            "2026-12-07" to SolarTerm.MajorSnow,
            "2026-12-22" to SolarTerm.WinterSolstice,
        ).mapKeys { LocalDate.parse(it.key) }

        val found = generateSequence(LocalDate.of(2026, 1, 1)) { it.plusDays(1) }
            .takeWhile { it.year == 2026 }
            .mapNotNull { date -> FestivalCalendar.solarTermOn(date)?.let { date to it } }
            .toMap()
        assertEquals(expected, found)
    }

    @Test
    fun nationalDayIsOnlyTheFirstOfOctober() {
        assertEquals(listOf(Festival.NationalDay), FestivalCalendar.festivalsOn(LocalDate.of(2026, 10, 1), null, null))
        assertTrue(FestivalCalendar.festivalsOn(LocalDate.of(2026, 10, 2), null, null).isEmpty())
    }

    @Test
    fun lunarFestivalsAndNewYearsEve() {
        val midAutumn = FestivalCalendar.festivalsOn(LocalDate.of(2026, 9, 25), LunarDay(8, 15), LunarDay(8, 16))
        assertEquals(listOf(Festival.MidAutumn), midAutumn)
        // 腊月只有 29 天的年份，除夕是二十九：看的是明天是不是正月初一
        val eve = FestivalCalendar.festivalsOn(LocalDate.of(2027, 2, 5), LunarDay(12, 29), LunarDay(1, 1))
        assertEquals(listOf(Festival.NewYearsEve), eve)
    }

    @Test
    fun leapMonthDoesNotRepeatFestivals() {
        assertTrue(FestivalCalendar.festivalsOn(LocalDate.of(2025, 7, 1), LunarDay(6, 7, leapMonth = true), null).isEmpty())
    }

    @Test
    fun mothersAndFathersDay() {
        assertEquals(listOf(Festival.MothersDay), FestivalCalendar.festivalsOn(LocalDate.of(2026, 5, 10), null, null))
        assertEquals(listOf(Festival.FathersDay), FestivalCalendar.festivalsOn(LocalDate.of(2026, 6, 21), null, null))
        assertTrue(FestivalCalendar.festivalsOn(LocalDate.of(2026, 5, 17), null, null).isEmpty())
    }

    @Test
    fun ordinaryDayHasNoSolarTerm() {
        assertNull(FestivalCalendar.solarTermOn(LocalDate.of(2026, 9, 27)))
    }
}
