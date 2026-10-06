package com.x500x.cursimple.app.util

import android.Manifest
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract
import androidx.annotation.StringRes
import androidx.core.content.ContextCompat
import com.x500x.cursimple.R
import com.x500x.cursimple.core.kernel.model.CourseCategory
import com.x500x.cursimple.core.kernel.model.CourseItem
import java.time.ZoneId

data class SystemCalendarAccount(
    val id: Long,
    val displayName: String,
    val accountName: String,
    val color: Int?,
)

sealed interface SystemCalendarWriteResult {
    /** [eventCount] counts stored events; [occurrenceCount] counts expanded class instances. */
    data class Success(
        val eventCount: Int,
        val occurrenceCount: Int,
        val skipped: List<IcsSkippedCourse>,
    ) : SystemCalendarWriteResult

    data object PermissionDenied : SystemCalendarWriteResult

    data class MissingConfig(@StringRes val reason: Int) : SystemCalendarWriteResult

    data class Failed(val message: String?) : SystemCalendarWriteResult
}

sealed interface SystemCalendarUndoResult {
    data class Success(val removedCount: Int) : SystemCalendarUndoResult
    data object PermissionDenied : SystemCalendarUndoResult
    data object NothingToUndo : SystemCalendarUndoResult
    data class Failed(val message: String?) : SystemCalendarUndoResult
}

/** Record created event IDs for exact undo; already-deleted events are treated as removed. */
object SystemCalendarExporter {

    fun hasWritePermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) ==
            PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_CALENDAR) ==
            PackageManager.PERMISSION_GRANTED

    /** Empty without permission or after query failure. */
    fun writableAccounts(context: Context): List<SystemCalendarAccount> {
        if (!hasWritePermission(context)) return emptyList()
        val projection = arrayOf(
            CalendarContract.Calendars._ID,
            CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
            CalendarContract.Calendars.ACCOUNT_NAME,
            CalendarContract.Calendars.CALENDAR_COLOR,
        )
        val selection = "${CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL} >= ?"
        val args = arrayOf(CalendarContract.Calendars.CAL_ACCESS_CONTRIBUTOR.toString())
        return runCatching {
            context.contentResolver.query(
                CalendarContract.Calendars.CONTENT_URI,
                projection,
                selection,
                args,
                "${CalendarContract.Calendars.CALENDAR_DISPLAY_NAME} ASC",
            )?.use { cursor ->
                buildList {
                    while (cursor.moveToNext()) {
                        add(
                            SystemCalendarAccount(
                                id = cursor.getLong(0),
                                displayName = cursor.getString(1).orEmpty(),
                                accountName = cursor.getString(2).orEmpty(),
                                color = if (cursor.isNull(3)) null else cursor.getInt(3),
                            ),
                        )
                    }
                }
            }.orEmpty()
        }.getOrDefault(emptyList())
    }

    fun write(
        context: Context,
        calendarId: Long,
        plan: SchedulePlan,
        zone: ZoneId,
    ): SystemCalendarWriteResult {
        if (!hasWritePermission(context)) return SystemCalendarWriteResult.PermissionDenied
        plan.failureReason?.let { return SystemCalendarWriteResult.MissingConfig(it) }

        val resolver = context.contentResolver
        val insertedIds = mutableListOf<Long>()
        var occurrenceCount = 0
        try {
            for (planned in plan.courses) {
                val description = context.eventDescription(planned.course, planned.slotLabel)
                for (draft in planned.toCalendarDrafts(zone, description)) {
                    val values = ContentValues().apply {
                        put(CalendarContract.Events.CALENDAR_ID, calendarId)
                        put(CalendarContract.Events.TITLE, draft.title)
                        put(CalendarContract.Events.EVENT_TIMEZONE, zone.id)
                        put(
                            CalendarContract.Events.DTSTART,
                            draft.start.atZone(zone).toInstant().toEpochMilli(),
                        )
                        if (draft.description.isNotBlank()) {
                            put(CalendarContract.Events.DESCRIPTION, draft.description)
                        }
                        if (draft.location.isNotBlank()) {
                            put(CalendarContract.Events.EVENT_LOCATION, draft.location)
                        }
                        if (draft.rrule == null) {
                            put(
                                CalendarContract.Events.DTEND,
                                draft.start.plusMinutes(draft.durationMinutes)
                                    .atZone(zone).toInstant().toEpochMilli(),
                            )
                        } else {
                            // Recurring events require DURATION instead of DTEND.
                            put(CalendarContract.Events.DURATION, "PT${draft.durationMinutes}M")
                            put(CalendarContract.Events.RRULE, draft.rrule)
                            if (draft.exdatesUtc.isNotEmpty()) {
                                put(CalendarContract.Events.EXDATE, draft.exdatesUtc.joinToString(","))
                            }
                        }
                    }
                    val uri = resolver.insert(CalendarContract.Events.CONTENT_URI, values)
                    val id = uri?.let(ContentUris::parseId) ?: continue
                    insertedIds.add(id)
                }
                occurrenceCount += planned.occurrenceCount()
            }
        } catch (e: SecurityException) {
            removeEvents(context, insertedIds)
            return SystemCalendarWriteResult.PermissionDenied
        } catch (e: Exception) {
            // Rollback created events on partial failure.
            removeEvents(context, insertedIds)
            return SystemCalendarWriteResult.Failed(e.message)
        }

        SystemCalendarExportStore.record(context, calendarId, insertedIds)
        return SystemCalendarWriteResult.Success(
            eventCount = insertedIds.size,
            occurrenceCount = occurrenceCount,
            skipped = plan.skipped,
        )
    }

    fun undo(context: Context): SystemCalendarUndoResult {
        val record = SystemCalendarExportStore.read(context) ?: return SystemCalendarUndoResult.NothingToUndo
        if (record.eventIds.isEmpty()) {
            SystemCalendarExportStore.clear(context)
            return SystemCalendarUndoResult.NothingToUndo
        }
        if (!hasWritePermission(context)) return SystemCalendarUndoResult.PermissionDenied
        return try {
            val removed = removeEvents(context, record.eventIds)
            SystemCalendarExportStore.clear(context)
            SystemCalendarUndoResult.Success(removed)
        } catch (e: SecurityException) {
            SystemCalendarUndoResult.PermissionDenied
        } catch (e: Exception) {
            SystemCalendarUndoResult.Failed(e.message)
        }
    }

    private fun removeEvents(context: Context, ids: List<Long>): Int {
        var removed = 0
        for (id in ids) {
            val uri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, id)
            removed += runCatching { context.contentResolver.delete(uri, null, null) }.getOrDefault(0)
        }
        return removed
    }

    private fun Context.eventDescription(course: CourseItem, slotLabel: String?): String {
        val parts = mutableListOf<String>()
        if (course.category == CourseCategory.Exam) {
            parts.add(getString(R.string.calendar_event_category_exam))
        }
        if (course.teacher.isNotBlank()) {
            parts.add(getString(R.string.calendar_event_teacher, course.teacher))
        }
        parts.add(
            getString(R.string.calendar_event_nodes, course.time.startNode, course.time.endNode),
        )
        slotLabel?.let { parts.add(getString(R.string.calendar_event_slot, it)) }
        return parts.joinToString("\n")
    }
}
