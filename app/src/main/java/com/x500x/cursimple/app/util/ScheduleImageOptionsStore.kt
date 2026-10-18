package com.x500x.cursimple.app.util

import android.content.Context

/** Remembers the last image element choices across exports. */
object ScheduleImageOptionsStore {

    private const val FILE = "schedule_image_options"

    fun load(context: Context): ScheduleImageOptions {
        val prefs = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        val defaults = ScheduleImageOptions()
        return ScheduleImageOptions(
            showCourses = prefs.getBoolean("courses", defaults.showCourses),
            showExams = prefs.getBoolean("exams", defaults.showExams),
            showEvents = prefs.getBoolean("events", defaults.showEvents),
            showWeeks = prefs.getBoolean("weeks", defaults.showWeeks),
            showLocation = prefs.getBoolean("location", defaults.showLocation),
            showTeacher = prefs.getBoolean("teacher", defaults.showTeacher),
            showPeriodTimes = prefs.getBoolean("period_times", defaults.showPeriodTimes),
            showDates = prefs.getBoolean("dates", defaults.showDates),
            days = prefs.getString("days", null)
                ?.split(',')?.mapNotNull { it.trim().toIntOrNull()?.takeIf { day -> day in 1..7 } }?.toSet()
                ?.takeIf { it.isNotEmpty() }
                // Earlier builds only had a weekend switch.
                ?: if (prefs.getBoolean("weekend", true)) defaults.days else (1..5).toSet(),
        )
    }

    fun save(context: Context, options: ScheduleImageOptions) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit()
            .putBoolean("courses", options.showCourses)
            .putBoolean("exams", options.showExams)
            .putBoolean("events", options.showEvents)
            .putBoolean("weeks", options.showWeeks)
            .putBoolean("location", options.showLocation)
            .putBoolean("teacher", options.showTeacher)
            .putBoolean("period_times", options.showPeriodTimes)
            .putBoolean("dates", options.showDates)
            .putString("days", options.days.sorted().joinToString(","))
            .remove("weekend")
            .apply()
    }
}
