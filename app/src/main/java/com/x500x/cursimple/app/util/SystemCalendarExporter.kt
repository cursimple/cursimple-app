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
        val inserted: Int = eventCount,
        val updated: Int = 0,
        val deleted: Int = 0,
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

    const val CURSIMPLE_UID_PREFIX = "cursimple:"
    const val UID_TAG_START = "[CurSimple:UID:"
    const val UID_TAG_END = "]"

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

    data class ExistingCalendarEvent(
        val id: Long,
        val uid: String,
        val title: String,
        val description: String,
        val location: String,
        val dtstart: Long,
        val dtend: Long?,
        val duration: String?,
        val rrule: String?,
        val exdate: String?,
        val timezone: String?,
    )

    fun queryCurSimpleEvents(
        context: Context,
        calendarId: Long,
        storedLegacyIds: Set<Long> = emptySet(),
    ): List<ExistingCalendarEvent> {
        if (!hasWritePermission(context)) return emptyList()
        val projection = arrayOf(
            CalendarContract.Events._ID,
            CalendarContract.Events.SYNC_DATA1,
            CalendarContract.Events.DESCRIPTION,
            CalendarContract.Events.TITLE,
            CalendarContract.Events.EVENT_LOCATION,
            CalendarContract.Events.DTSTART,
            CalendarContract.Events.DTEND,
            CalendarContract.Events.DURATION,
            CalendarContract.Events.RRULE,
            CalendarContract.Events.EXDATE,
            CalendarContract.Events.EVENT_TIMEZONE,
        )
        val selection = "${CalendarContract.Events.CALENDAR_ID} = ?"
        val selectionArgs = arrayOf(calendarId.toString())
        return runCatching {
            context.contentResolver.query(
                CalendarContract.Events.CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                null,
            )?.use { cursor ->
                buildList {
                    while (cursor.moveToNext()) {
                        val id = cursor.getLong(0)
                        val syncData1 = cursor.getString(1).orEmpty()
                        val desc = cursor.getString(2).orEmpty()
                        val uid = when {
                            syncData1.startsWith(CURSIMPLE_UID_PREFIX) -> syncData1
                            desc.contains(UID_TAG_START) -> {
                                desc.substringAfter(UID_TAG_START).substringBefore(UID_TAG_END).trim()
                            }
                            id in storedLegacyIds -> "cursimple:legacy:$id"
                            else -> null
                        }
                        if (uid != null) {
                            add(
                                ExistingCalendarEvent(
                                    id = id,
                                    uid = uid,
                                    title = cursor.getString(3).orEmpty(),
                                    description = desc,
                                    location = cursor.getString(4).orEmpty(),
                                    dtstart = cursor.getLong(5),
                                    dtend = if (cursor.isNull(6)) null else cursor.getLong(6),
                                    duration = cursor.getString(7),
                                    rrule = cursor.getString(8),
                                    exdate = cursor.getString(9),
                                    timezone = cursor.getString(10),
                                ),
                            )
                        }
                    }
                }
            }.orEmpty()
        }.getOrDefault(emptyList())
    }

    /**
     * Smart incremental sync:
     * - Deletes courses that no longer exist or changed UIDs.
     * - Updates modified courses in place to avoid notification flutter.
     * - Inserts newly scheduled courses with persistent UID tags.
     */
    fun sync(
        context: Context,
        calendarId: Long,
        plan: SchedulePlan,
        zone: ZoneId,
    ): SystemCalendarWriteResult {
        if (!hasWritePermission(context)) return SystemCalendarWriteResult.PermissionDenied
        plan.failureReason?.let { return SystemCalendarWriteResult.MissingConfig(it) }

        val resolver = context.contentResolver
        val storedRecord = SystemCalendarExportStore.read(context)
        val storedIds = if (storedRecord != null && storedRecord.calendarId == calendarId) {
            storedRecord.eventIds.toSet()
        } else {
            emptySet()
        }

        val existingEvents = queryCurSimpleEvents(context, calendarId, storedIds)
        val existingByUid = existingEvents.filterNot { it.uid.startsWith("cursimple:legacy:") }.associateBy { it.uid }

        val drafts = mutableListOf<CalendarEventDraft>()
        var occurrenceCount = 0
        for (planned in plan.courses) {
            val description = context.eventDescription(planned.course, planned.slotLabel)
            drafts.addAll(planned.toCalendarDrafts(zone, description))
            occurrenceCount += planned.occurrenceCount()
        }

        val targetUids = drafts.map { it.uid }.toSet()
        val activeIds = mutableListOf<Long>()
        var insertedCount = 0
        var updatedCount = 0
        var deletedCount = 0

        try {
            // Delete removed or obsolete events
            for (existing in existingEvents) {
                if (existing.uid !in targetUids) {
                    val uri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, existing.id)
                    val removed = runCatching { resolver.delete(uri, null, null) }.getOrDefault(0)
                    if (removed > 0) deletedCount++
                }
            }

            // Insert or update target drafts
            for (draft in drafts) {
                val fullDescription = if (draft.description.isNotBlank()) {
                    "${draft.description}\n$UID_TAG_START${draft.uid}$UID_TAG_END"
                } else {
                    "$UID_TAG_START${draft.uid}$UID_TAG_END"
                }
                val startMillis = draft.start.atZone(zone).toInstant().toEpochMilli()
                val durationStr = if (draft.rrule != null) "PT${draft.durationMinutes}M" else null
                val endMillis = if (draft.rrule == null) {
                    draft.start.plusMinutes(draft.durationMinutes).atZone(zone).toInstant().toEpochMilli()
                } else null
                val exdateStr = if (draft.exdatesUtc.isNotEmpty()) draft.exdatesUtc.joinToString(",") else null

                val existing = existingByUid[draft.uid]
                if (existing != null) {
                    // Check if anything changed
                    val changed = existing.title != draft.title ||
                        existing.location != draft.location ||
                        existing.description != fullDescription ||
                        existing.dtstart != startMillis ||
                        existing.dtend != endMillis ||
                        existing.duration != durationStr ||
                        existing.rrule != draft.rrule ||
                        existing.exdate != exdateStr ||
                        existing.timezone != zone.id

                    if (changed) {
                        val values = ContentValues().apply {
                            put(CalendarContract.Events.TITLE, draft.title)
                            put(CalendarContract.Events.EVENT_TIMEZONE, zone.id)
                            put(CalendarContract.Events.DTSTART, startMillis)
                            put(CalendarContract.Events.DESCRIPTION, fullDescription)
                            put(CalendarContract.Events.EVENT_LOCATION, draft.location)
                            put(CalendarContract.Events.SYNC_DATA1, draft.uid)
                            if (draft.rrule == null) {
                                put(CalendarContract.Events.DTEND, endMillis)
                                putNull(CalendarContract.Events.DURATION)
                                putNull(CalendarContract.Events.RRULE)
                                putNull(CalendarContract.Events.EXDATE)
                            } else {
                                putNull(CalendarContract.Events.DTEND)
                                put(CalendarContract.Events.DURATION, durationStr)
                                put(CalendarContract.Events.RRULE, draft.rrule)
                                if (exdateStr != null) {
                                    put(CalendarContract.Events.EXDATE, exdateStr)
                                } else {
                                    putNull(CalendarContract.Events.EXDATE)
                                }
                            }
                        }
                        val uri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, existing.id)
                        resolver.update(uri, values, null, null)
                        updatedCount++
                    }
                    activeIds.add(existing.id)
                } else {
                    // Insert new event
                    val values = ContentValues().apply {
                        put(CalendarContract.Events.CALENDAR_ID, calendarId)
                        put(CalendarContract.Events.TITLE, draft.title)
                        put(CalendarContract.Events.EVENT_TIMEZONE, zone.id)
                        put(CalendarContract.Events.DTSTART, startMillis)
                        put(CalendarContract.Events.DESCRIPTION, fullDescription)
                        put(CalendarContract.Events.EVENT_LOCATION, draft.location)
                        put(CalendarContract.Events.SYNC_DATA1, draft.uid)
                        if (draft.rrule == null) {
                            put(CalendarContract.Events.DTEND, endMillis)
                        } else {
                            put(CalendarContract.Events.DURATION, durationStr)
                            put(CalendarContract.Events.RRULE, draft.rrule)
                            if (exdateStr != null) {
                                put(CalendarContract.Events.EXDATE, exdateStr)
                            }
                        }
                    }
                    val uri = resolver.insert(CalendarContract.Events.CONTENT_URI, values)
                    val id = uri?.let(ContentUris::parseId) ?: continue
                    activeIds.add(id)
                    insertedCount++
                }
            }
        } catch (e: SecurityException) {
            return SystemCalendarWriteResult.PermissionDenied
        } catch (e: Exception) {
            return SystemCalendarWriteResult.Failed(e.message)
        }

        SystemCalendarExportStore.record(context, calendarId, activeIds)
        return SystemCalendarWriteResult.Success(
            eventCount = activeIds.size,
            occurrenceCount = occurrenceCount,
            skipped = plan.skipped,
            inserted = insertedCount,
            updated = updatedCount,
            deleted = deletedCount,
        )
    }

    /** Compatibility write delegate that uses incremental sync. */
    fun write(
        context: Context,
        calendarId: Long,
        plan: SchedulePlan,
        zone: ZoneId,
    ): SystemCalendarWriteResult = sync(context, calendarId, plan, zone)

    /**
     * Completely clear all CurSimple-managed events from the calendar:
     * Removes all events identified by SYNC_DATA1, UID description tags, or recorded export store.
     */
    fun clearAll(context: Context, calendarId: Long? = null): SystemCalendarUndoResult {
        if (!hasWritePermission(context)) return SystemCalendarUndoResult.PermissionDenied
        val storedRecord = SystemCalendarExportStore.read(context)
        val targetCalendarId = calendarId ?: storedRecord?.calendarId

        val idsToDelete = mutableSetOf<Long>()
        storedRecord?.eventIds?.let { idsToDelete.addAll(it) }

        if (targetCalendarId != null) {
            val queried = queryCurSimpleEvents(context, targetCalendarId, idsToDelete)
            idsToDelete.addAll(queried.map { it.id })
        }

        if (idsToDelete.isEmpty()) {
            SystemCalendarExportStore.clear(context)
            return SystemCalendarUndoResult.NothingToUndo
        }

        return try {
            val removed = removeEvents(context, idsToDelete.toList())
            SystemCalendarExportStore.clear(context)
            SystemCalendarUndoResult.Success(removed)
        } catch (e: SecurityException) {
            SystemCalendarUndoResult.PermissionDenied
        } catch (e: Exception) {
            SystemCalendarUndoResult.Failed(e.message)
        }
    }

    fun undo(context: Context): SystemCalendarUndoResult = clearAll(context, null)

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
