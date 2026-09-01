package com.x500x.cursimple.app.util

import android.content.ClipData
import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.x500x.cursimple.core.kernel.model.CourseItem
import com.x500x.cursimple.core.kernel.model.TemporaryScheduleOverride
import com.x500x.cursimple.core.kernel.model.TermSchedule
import com.x500x.cursimple.core.kernel.model.TermTimingProfile
import com.x500x.cursimple.core.kernel.time.BeijingTime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.LocalDate
import java.util.Date
import java.util.Locale
import com.x500x.cursimple.core.kernel.model.HolidayCalendarSettings

/** ICS 导出结果：分享意图（成功时非空）与需要提示用户的信息。 */
data class ScheduleIcsExportOutcome(
    val intent: Intent?,
    val eventCount: Int,
    val occurrenceCount: Int,
    val skipped: List<IcsSkippedCourse>,
    val failureReason: String?,
)

/** 把课表渲染成 .ics 文件并封装为系统分享意图；生成逻辑委托给纯函数 [ScheduleIcsBuilder]。 */
object ScheduleIcsExporter {

    suspend fun export(
        context: Context,
        termName: String?,
        termStartDate: LocalDate?,
        schedule: TermSchedule?,
        manualCourses: List<CourseItem>,
        timingProfile: TermTimingProfile?,
        overrides: List<TemporaryScheduleOverride>,
        holidayCalendar: HolidayCalendarSettings,
    ): ScheduleIcsExportOutcome = withContext(Dispatchers.IO) {
        val result = ScheduleIcsBuilder.build(
            termName = termName,
            termStartDate = termStartDate,
            schedule = schedule,
            manualCourses = manualCourses,
            timingProfile = timingProfile,
            overrides = overrides,
            zone = BeijingTime.zone,
            generatedAt = Instant.now(),
            holidayCalendar = holidayCalendar,
        )
        if (result.eventCount == 0) {
            return@withContext ScheduleIcsExportOutcome(
                intent = null,
                eventCount = 0,
                occurrenceCount = 0,
                skipped = result.skipped,
                failureReason = result.failureReason ?: "当前学期没有可导出的课程",
            )
        }
        val file = writeIcsFile(context, termName, result.content)
            ?: return@withContext ScheduleIcsExportOutcome(
                intent = null,
                eventCount = result.eventCount,
                occurrenceCount = result.occurrenceCount,
                skipped = result.skipped,
                failureReason = "写入日历文件失败",
            )
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/calendar"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, file.name)
            clipData = ClipData.newUri(context.contentResolver, file.name, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        ScheduleIcsExportOutcome(
            intent = intent,
            eventCount = result.eventCount,
            occurrenceCount = result.occurrenceCount,
            skipped = result.skipped,
            failureReason = null,
        )
    }

    private fun writeIcsFile(context: Context, termName: String?, content: String): File? = runCatching {
        val dir = File(context.cacheDir, "ics").apply { mkdirs() }
        val timestamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
        val slug = termName?.let { sanitizeFileName(it) }?.takeIf { it.isNotBlank() }
        val name = if (slug != null) "cursimple-$slug-$timestamp.ics" else "cursimple-schedule-$timestamp.ics"
        val target = File(dir, name)
        target.writeText(content, Charsets.UTF_8)
        target
    }.getOrNull()

    private fun sanitizeFileName(raw: String): String =
        raw.trim().map { ch ->
            if (ch.isLetterOrDigit()) ch else '-'
        }.joinToString("").trim('-').take(40)
}
