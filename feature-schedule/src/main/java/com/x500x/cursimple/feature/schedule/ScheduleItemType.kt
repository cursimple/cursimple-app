package com.x500x.cursimple.feature.schedule

import androidx.annotation.StringRes

/**
 * High-level item type selectable when adding a schedule entry (Course, Exam, or Event).
 */
enum class ScheduleItemType(@StringRes val labelRes: Int) {
    Course(R.string.schedule_category_course),
    Exam(R.string.schedule_category_exam),
    Event(R.string.schedule_category_event),
}
