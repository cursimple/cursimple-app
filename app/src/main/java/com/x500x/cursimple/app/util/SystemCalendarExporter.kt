package com.x500x.cursimple.app.util

import android.Manifest
import android.content.ContentUris
import android.content.ContentValues
import android.content.ContentProviderOperation
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
        check(hasWritePermission(context)) { "Calendar permission denied" }
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
        return context.contentResolver.query(
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
                            else -> desc.lineSequence().firstOrNull {
                                it.startsWith(UID_TAG_START + CURSIMPLE_UID_PREFIX) && it.endsWith(UID_TAG_END)
                            }?.removePrefix(UID_TAG_START)?.removeSuffix(UID_TAG_END)
                                ?: if (id in storedLegacyIds) "cursimple:legacy:$id" else null
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
            } ?: error("Calendar query failed")
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

        return try {
            val record = SystemCalendarExportStore.read(context)
            val legacyIds = record?.takeIf { it.calendarId == calendarId }?.eventIds.orEmpty().toSet()
            val existing = queryCurSimpleEvents(context, calendarId, legacyIds)
            val existingByUid = existing.sortedBy { it.id }.associateBy { it.uid }
            val drafts = plan.courses.flatMap { planned ->
                planned.toCalendarDrafts(zone, context.eventDescription(planned.course, planned.slotLabel))
            }
            require(drafts.map { it.uid }.distinct().size == drafts.size) { "Duplicate course identifiers" }
            val targetUids = drafts.map { it.uid }.toSet()
            val operations = arrayListOf<ContentProviderOperation>()
            val activeIds = mutableListOf<Long>()
            val insertIndexes = mutableListOf<Int>()
            var deleted = 0
            var updated = 0
            // Drop obsolete rows and reconcile duplicate UIDs from older or interrupted exports.
            existing.filter { it.uid !in targetUids || existingByUid[it.uid]?.id != it.id }.forEach { event ->
                operations += ContentProviderOperation.newDelete(ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, event.id))
                    .withExpectedCount(1).build()
                deleted++
            }
            for (draft in drafts) {
                val description = listOf(draft.description.takeIf { it.isNotBlank() }, "$UID_TAG_START${draft.uid}$UID_TAG_END")
                    .filterNotNull().joinToString("\n")
                val start = draft.start.atZone(zone).toInstant().toEpochMilli()
                val end = if (draft.rrule == null) draft.start.plusMinutes(draft.durationMinutes).atZone(zone).toInstant().toEpochMilli() else null
                val duration = if (draft.rrule != null) "PT${draft.durationMinutes}M" else null
                val exdate = draft.exdatesUtc.takeIf { it.isNotEmpty() }?.joinToString(",")
                // SYNC_DATA1 is read for old exports only: normal apps may not write sync-adapter columns.
                val values = ContentValues().apply {
                    put(CalendarContract.Events.TITLE, draft.title)
                    put(CalendarContract.Events.EVENT_TIMEZONE, zone.id)
                    put(CalendarContract.Events.DTSTART, start)
                    put(CalendarContract.Events.DESCRIPTION, description)
                    put(CalendarContract.Events.EVENT_LOCATION, draft.location)
                    put(CalendarContract.Events.DTEND, end)
                    put(CalendarContract.Events.DURATION, duration)
                    put(CalendarContract.Events.RRULE, draft.rrule)
                    put(CalendarContract.Events.EXDATE, exdate)
                }
                val old = existingByUid[draft.uid]
                if (old == null) {
                    values.put(CalendarContract.Events.CALENDAR_ID, calendarId)
                    insertIndexes += operations.size
                    operations += ContentProviderOperation.newInsert(CalendarContract.Events.CONTENT_URI).withValues(values).build()
                } else {
                    activeIds += old.id
                    if (old.title != draft.title || old.location != draft.location || old.description != description ||
                        old.dtstart != start || old.dtend != end || old.duration != duration || old.rrule != draft.rrule ||
                        old.exdate != exdate || old.timezone != zone.id) {
                        operations += ContentProviderOperation.newUpdate(ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, old.id))
                            .withValues(values).withExpectedCount(1).build()
                        updated++
                    }
                }
            }
            // CalendarProvider applies a batch transactionally; failures leave the prior export intact.
            if (operations.isNotEmpty()) {
                val results = context.contentResolver.applyBatch(CalendarContract.AUTHORITY, operations)
                insertIndexes.forEach { index -> activeIds += ContentUris.parseId(checkNotNull(results[index].uri)) }
            }
            SystemCalendarExportStore.record(context, calendarId, activeIds)
            SystemCalendarWriteResult.Success(activeIds.size, plan.courses.sumOf { it.occurrenceCount() }, plan.skipped,
                inserted = insertIndexes.size, updated = updated, deleted = deleted)
        } catch (e: SecurityException) {
            SystemCalendarWriteResult.PermissionDenied
        } catch (e: Exception) {
            SystemCalendarWriteResult.Failed(e.message)
        }
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
        val targetCalendarId = calendarId ?: storedRecord?.calendarId ?: return SystemCalendarUndoResult.NothingToUndo
        return try {
            val legacyIds = storedRecord?.takeIf { it.calendarId == targetCalendarId }?.eventIds.orEmpty().toSet()
            val events = queryCurSimpleEvents(context, targetCalendarId, legacyIds)
            val operations = ArrayList(events.map { event ->
                ContentProviderOperation.newDelete(ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, event.id))
                    .withExpectedCount(1).build()
            })
            if (operations.isNotEmpty()) context.contentResolver.applyBatch(CalendarContract.AUTHORITY, operations)
            if (storedRecord?.calendarId == targetCalendarId) SystemCalendarExportStore.clear(context)
            if (events.isEmpty()) SystemCalendarUndoResult.NothingToUndo else SystemCalendarUndoResult.Success(events.size)
        } catch (e: SecurityException) {
            SystemCalendarUndoResult.PermissionDenied
        } catch (e: Exception) {
            SystemCalendarUndoResult.Failed(e.message)
        }
    }

    fun undo(context: Context): SystemCalendarUndoResult = clearAll(context, null)

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
