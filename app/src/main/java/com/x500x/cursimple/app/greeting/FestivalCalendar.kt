package com.x500x.cursimple.app.greeting

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.temporal.TemporalAdjusters
import kotlin.math.floor
import kotlin.math.sin

/** Caller-supplied lunar date; calendar conversion remains outside festival matching. */
internal data class LunarDay(val month: Int, val day: Int, val leapMonth: Boolean = false)

/** Match festival dates themselves, not the entire holiday period. */
internal enum class Festival {
    NewYear,
    Valentine,
    WomensDay,
    ArborDay,
    AprilFools,
    LaborDay,
    YouthDay,
    ChildrensDay,
    TeachersDay,
    NationalDay,
    ChristmasEve,
    Christmas,
    MothersDay,
    FathersDay,
    NewYearsEve,
    SpringFestival,
    Lantern,
    DragonHead,
    DragonBoat,
    Qixi,
    MidAutumn,
    DoubleNinth,
    Laba,
}

/** Solar-term longitude is [ordinal] times 15 degrees, starting at the vernal equinox. */
internal enum class SolarTerm {
    SpringEquinox,
    PureBrightness,
    GrainRain,
    StartOfSummer,
    GrainBuds,
    GrainInEar,
    SummerSolstice,
    MinorHeat,
    MajorHeat,
    StartOfAutumn,
    EndOfHeat,
    WhiteDew,
    AutumnEquinox,
    ColdDew,
    FrostDescent,
    StartOfWinter,
    MinorSnow,
    MajorSnow,
    WinterSolstice,
    MinorCold,
    MajorCold,
    StartOfSpring,
    RainWater,
    AwakeningOfInsects,
}

internal object FestivalCalendar {
    private val BEIJING = ZoneOffset.ofHours(8)

    /**
     * Use [lunarTomorrow] to detect the lunar year's last day; null lunar values limit matching
     * to Gregorian dates.
     */
    fun festivalsOn(date: LocalDate, lunar: LunarDay?, lunarTomorrow: LunarDay?): List<Festival> = buildList {
        when (date.monthValue to date.dayOfMonth) {
            1 to 1 -> add(Festival.NewYear)
            2 to 14 -> add(Festival.Valentine)
            3 to 8 -> add(Festival.WomensDay)
            3 to 12 -> add(Festival.ArborDay)
            4 to 1 -> add(Festival.AprilFools)
            5 to 1 -> add(Festival.LaborDay)
            5 to 4 -> add(Festival.YouthDay)
            6 to 1 -> add(Festival.ChildrensDay)
            9 to 10 -> add(Festival.TeachersDay)
            10 to 1 -> add(Festival.NationalDay)
            12 to 24 -> add(Festival.ChristmasEve)
            12 to 25 -> add(Festival.Christmas)
        }
        if (date.monthValue == 5 && date == nthSunday(date, 2)) add(Festival.MothersDay)
        if (date.monthValue == 6 && date == nthSunday(date, 3)) add(Festival.FathersDay)
        if (lunar != null && !lunar.leapMonth) {
            when (lunar.month to lunar.day) {
                1 to 1 -> add(Festival.SpringFestival)
                1 to 15 -> add(Festival.Lantern)
                2 to 2 -> add(Festival.DragonHead)
                5 to 5 -> add(Festival.DragonBoat)
                7 to 7 -> add(Festival.Qixi)
                8 to 15 -> add(Festival.MidAutumn)
                9 to 9 -> add(Festival.DoubleNinth)
                12 to 8 -> add(Festival.Laba)
            }
        }
        if (lunarTomorrow != null && !lunarTomorrow.leapMonth && lunarTomorrow.month == 1 && lunarTomorrow.day == 1) {
            add(Festival.NewYearsEve)
        }
    }

    private fun nthSunday(date: LocalDate, n: Int): LocalDate =
        date.withDayOfMonth(1).with(TemporalAdjusters.dayOfWeekInMonth(n, DayOfWeek.SUNDAY))

    fun solarTermOn(date: LocalDate): SolarTerm? {
        val start = date.atStartOfDay().toInstant(BEIJING).toEpochMilli()
        val end = date.plusDays(1).atStartOfDay().toInstant(BEIJING).toEpochMilli()
        val from = floor(sunLongitude(start) / TERM_DEGREES).toInt()
        val to = floor(sunLongitude(end) / TERM_DEGREES).toInt()
        // Detect longitude crossings at 15-degree boundaries, including the 360-degree wrap.
        if (from == to) return null
        return SolarTerm.entries[to.mod(SolarTerm.entries.size)]
    }

    /**
     * Meeus chapter 25 approximation: about 0.01-degree longitude accuracy for day-level
     * matching.
     */
    internal fun sunLongitude(epochMillis: Long): Double {
        val julianDay = epochMillis / MILLIS_PER_DAY + UNIX_EPOCH_JULIAN_DAY + DELTA_T_SECONDS / SECONDS_PER_DAY
        val t = (julianDay - J2000) / DAYS_PER_CENTURY
        val meanLongitude = 280.46646 + 36000.76983 * t + 0.0003032 * t * t
        val meanAnomaly = Math.toRadians(357.52911 + 35999.05029 * t - 0.0001537 * t * t)
        val center = (1.914602 - 0.004817 * t - 0.000014 * t * t) * sin(meanAnomaly) +
            (0.019993 - 0.000101 * t) * sin(2 * meanAnomaly) +
            0.000289 * sin(3 * meanAnomaly)
        val omega = Math.toRadians(125.04 - 1934.136 * t)
        val apparent = meanLongitude + center - 0.00569 - 0.00478 * sin(omega)
        return apparent.mod(360.0)
    }

    private const val TERM_DEGREES = 15.0
    private const val MILLIS_PER_DAY = 86_400_000.0
    private const val SECONDS_PER_DAY = 86_400.0
    private const val UNIX_EPOCH_JULIAN_DAY = 2_440_587.5
    private const val J2000 = 2_451_545.0
    private const val DAYS_PER_CENTURY = 36_525.0
    private const val DELTA_T_SECONDS = 69.0
}
