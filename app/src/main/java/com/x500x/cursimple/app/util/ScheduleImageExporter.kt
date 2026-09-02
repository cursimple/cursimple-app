package com.x500x.cursimple.app.util

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import androidx.core.content.FileProvider
import com.x500x.cursimple.R
import com.x500x.cursimple.core.kernel.model.CourseItem
import com.x500x.cursimple.core.kernel.model.HolidayCalendarSettings
import com.x500x.cursimple.core.kernel.model.weekdayName
import com.x500x.cursimple.core.kernel.model.TemporaryScheduleOverride
import com.x500x.cursimple.core.kernel.model.TermSchedule
import com.x500x.cursimple.core.kernel.model.TermTimingProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.util.Date
import java.util.Locale

/** 课表图片导出结果：分享意图（成功时非空）与需要提示用户的信息。 */
data class ScheduleImageExportOutcome(
    val intent: Intent?,
    val weekNumber: Int,
    val courseCount: Int,
    val failureReason: String?,
)

/** 把某一教学周的课表画成 PNG 并封装为系统分享意图；排版委托给纯函数 [ScheduleImageLayout]。 */
object ScheduleImageExporter {

    private const val PNG_QUALITY = 100

    suspend fun export(
        context: Context,
        termName: String?,
        termStartDate: LocalDate?,
        weekNumber: Int,
        schedule: TermSchedule?,
        manualCourses: List<CourseItem>,
        timingProfile: TermTimingProfile?,
        overrides: List<TemporaryScheduleOverride>,
        holidayCalendar: HolidayCalendarSettings,
    ): ScheduleImageExportOutcome = withContext(Dispatchers.IO) {
        if (termStartDate == null) {
            return@withContext failure(weekNumber, context.getString(R.string.image_failure_no_term_start))
        }
        if (timingProfile == null || timingProfile.slotTimes.isEmpty()) {
            return@withContext failure(weekNumber, context.getString(R.string.image_failure_no_timing_layout))
        }

        val layout = ScheduleImageLayout.compute(
            termName = termName,
            termStartDate = termStartDate,
            weekNumber = weekNumber,
            schedule = schedule,
            manualCourses = manualCourses,
            timingProfile = timingProfile,
            overrides = overrides,
            holidayCalendar = holidayCalendar,
            measurer = ScheduleImageRenderer.textMeasurer(),
            labels = context.scheduleImageLabels(),
        )
        layout.failureReason?.let { return@withContext failure(layout.weekNumber, it) }

        val bitmap = runCatching { ScheduleImageRenderer.render(layout) }.getOrNull()
            ?: return@withContext failure(layout.weekNumber, context.getString(R.string.image_failure_too_large))

        val file = try {
            writePng(context, termName, layout.weekNumber, bitmap)
        } finally {
            bitmap.recycle()
        }
        if (file == null) {
            return@withContext failure(layout.weekNumber, context.getString(R.string.image_write_failed))
        }

        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(
                Intent.EXTRA_SUBJECT,
                context.getString(R.string.image_share_subject, layout.title, layout.weekNumber),
            )
            clipData = ClipData.newUri(context.contentResolver, file.name, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        ScheduleImageExportOutcome(
            intent = intent,
            weekNumber = layout.weekNumber,
            courseCount = layout.courseCount,
            failureReason = null,
        )
    }

    private fun failure(weekNumber: Int, reason: String) =
        ScheduleImageExportOutcome(
            intent = null,
            weekNumber = weekNumber.coerceAtLeast(1),
            courseCount = 0,
            failureReason = reason,
        )

    private fun writePng(context: Context, termName: String?, weekNumber: Int, bitmap: Bitmap): File? = runCatching {
        val dir = File(context.cacheDir, "images").apply { mkdirs() }
        val timestamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
        val slug = termName?.let { sanitizeFileName(it) }?.takeIf { it.isNotBlank() }
        val prefix = if (slug != null) "cursimple-$slug" else "cursimple-schedule"
        val target = File(dir, "$prefix-week$weekNumber-$timestamp.png")
        target.outputStream().use { stream ->
            if (!bitmap.compress(Bitmap.CompressFormat.PNG, PNG_QUALITY, stream)) {
                throw IllegalStateException("compress failed")
            }
        }
        target
    }.getOrNull()

    private fun sanitizeFileName(raw: String): String =
        raw.trim().map { ch ->
            if (ch.isLetterOrDigit()) ch else '-'
        }.joinToString("").trim('-').take(40)
}

/** 用当前语言的资源填充课表图片里的所有文字。 */
fun Context.scheduleImageLabels(): ScheduleImageLabels = ScheduleImageLabels(
    defaultTitle = getString(R.string.image_default_title),
    holidayFallbackName = getString(R.string.image_holiday_fallback),
    holidayNameOfRes = { getString(it) },
    holidayAllDayOff = getString(R.string.image_holiday_all_day_off),
    overflowMoreDetail = getString(R.string.image_overflow_detail),
    noTimingFailure = getString(R.string.image_failure_no_timing),
    weekdayName = { day -> weekdayName(day) },
    dateLabel = { date -> getString(R.string.image_date_md, date.monthValue, date.dayOfMonth) },
    weekLabel = { week -> getString(R.string.image_week_label, week) },
    makeUpNote = { source -> getString(R.string.image_makeup_note, source) },
    overflowTitle = { hidden -> getString(R.string.image_overflow_title, hidden) },
    conflictFootnote = { weekday, nodeLabel, titles ->
        getString(
            R.string.image_conflict_footnote,
            weekday,
            nodeLabel,
            titles.size,
            titles.joinToString(getString(R.string.image_conflict_title_separator)),
        )
    },
    emptyWeekFailure = { week -> getString(R.string.image_failure_empty_week, week) },
)
