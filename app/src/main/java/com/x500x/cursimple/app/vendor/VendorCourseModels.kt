package com.x500x.cursimple.app.vendor

import com.x500x.cursimple.core.kernel.model.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import java.security.MessageDigest
import java.time.LocalDate
import java.time.ZoneId

@Serializable
data class VendorCourse(
    val id: String, val date: String, val title: String, val location: String, val teacher: String,
    val startAt: Long, val endAt: Long, val startSection: Int, val endSection: Int, val week: Int,
)

@Serializable
data class VendorCourseSnapshot(val generatedAt: Long = 0, val zone: String = "Asia/Shanghai", val advanceMinutes: Int = 20, val accentColor: String = "#3FA277", val courses: List<VendorCourse> = emptyList())

internal fun vendorCourses(
    date: LocalDate, courses: List<CourseItem>, timing: TermTimingProfile?, termStart: LocalDate?,
    overrides: List<TemporaryScheduleOverride>, holidays: HolidayCalendarSettings, zone: ZoneId,
): List<VendorCourse> = (0L..1L).flatMap { offset ->
    scheduledCourseOccurrencesOn(date.plusDays(offset), courses, timing, termStart, overrides, holidays).mapNotNull { occurrence ->
        val start = occurrence.start ?: return@mapNotNull null
        val end = occurrence.end ?: return@mapNotNull null
        val course = occurrence.course
        VendorCourse(course.id.take(160), occurrence.date.toString(), course.title.take(120), occurrence.location.take(160), occurrence.teacher.take(80),
            occurrence.date.atTime(start).atZone(zone).toInstant().toEpochMilli(),
            occurrence.date.atTime(end).atZone(zone).toInstant().toEpochMilli(), course.time.startNode, course.time.endNode,
            occurrence.sourceWeek ?: 0)
    }
}.filter { it.title.isNotBlank() }.take(80)

internal fun vendorCourseLink(packageName: String, course: VendorCourse? = null): String =
    "cursimple://$packageName/schedule" + (course?.let { "?date=${it.date}" } ?: "")

internal fun vivoCourseData(snapshot: VendorCourseSnapshot, packageName: String): JsonObject = buildJsonObject {
    put("loginStatus", 1); put("isImpCourse", 1); put("isSupEduSys", 1)
    put("schoolName", 0); put("semester", 0); put("lastDayOfWeek", 7)
    put("extras", buildJsonObject { put("guideDeeplink", vendorCourseLink(packageName)) })
    put("courseInfoList", buildJsonArray {
        snapshot.courses.groupBy { it.date }.toSortedMap().forEach { (date, courses) -> add(buildJsonObject {
            put("classSize", courses.size); put("dayOfWeek", LocalDate.parse(date).dayOfWeek.value)
            put("weekNo", courses.first().week); put("deeplink", vendorCourseLink(packageName, courses.first()))
            put("reminderTime", snapshot.advanceMinutes.toString())
            put("classBeanList", buildJsonArray { courses.forEach { c -> add(buildJsonObject {
                put("className", c.title); put("position", c.location); put("teacherName", c.teacher)
                put("classStartTime", c.startAt); put("classEndTime", c.endAt); put("date", c.date)
                put("deeplink", vendorCourseLink(packageName, c)); put("eachClassReminderTime", snapshot.advanceMinutes)
                put("isInferTime", 0); put("lineColor", snapshot.accentColor); put("stage", "${c.startSection}-${c.endSection}")
            }) } })
        }) }
    })
}

internal fun oppoCourseData(snapshot: VendorCourseSnapshot, date: LocalDate, packageName: String, enabled: Boolean): JsonObject = buildJsonObject {
    put("code", if (enabled) 0 else -1)
    if (enabled) put("data", buildJsonObject {
        val courses = snapshot.courses.filter { it.date == date.toString() }
        put("week", courses.firstOrNull()?.week ?: 0); put("dp", vendorCourseLink(packageName, courses.firstOrNull()))
        put("courseData", buildJsonArray { courses.forEach { c -> add(buildJsonObject {
            put("courseName", c.title); put("room", c.location); put("teacher", c.teacher)
            put("startTime", c.startAt.toString()); put("endTime", c.endAt.toString())
            put("startSection", c.startSection); put("endSection", c.endSection)
            put("dp", vendorCourseLink(packageName, c)); put("color", snapshot.accentColor); put("extra", "")
        }) } })
    })
}

internal fun courseFingerprint(snapshot: VendorCourseSnapshot, config: VendorCourseConfig): String {
    val data = vendorJson.encodeToString(VendorCourseSnapshot.serializer(), snapshot.copy(generatedAt = 0)) +
        vendorJson.encodeToString(VendorCourseConfig.serializer(), config)
    return MessageDigest.getInstance("SHA-256").digest(data.toByteArray()).joinToString("") { "%02x".format(it) }
}

internal val vendorJson = Json { ignoreUnknownKeys = true; encodeDefaults = true }

internal fun vendorScheduleDate(raw: String?, packageName: String): String? = runCatching {
    val uri = java.net.URI(raw ?: return null)
    if (uri.scheme != "cursimple" || uri.host != packageName || uri.path != "/schedule" || uri.userInfo != null) return null
    val value = uri.rawQuery?.split('&')?.firstOrNull { it.startsWith("date=") }?.substringAfter('=')
        ?: return java.time.LocalDate.now(com.x500x.cursimple.core.kernel.time.BeijingTime.zone).toString()
    if (!value.matches(Regex("[0-9]{4}-[0-9]{2}-[0-9]{2}"))) return null
    LocalDate.parse(value).takeIf { it.year in 1900..2200 }?.toString()
}.getOrNull()
