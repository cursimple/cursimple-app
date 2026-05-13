package com.x500x.cursimple.feature.widget

import android.content.Context
import com.x500x.cursimple.core.data.DataStoreScheduleRepository
import com.x500x.cursimple.core.data.DataStoreUserPreferencesRepository
import com.x500x.cursimple.core.data.ThemeAccent
import com.x500x.cursimple.core.data.reminder.DataStoreReminderRepository
import com.x500x.cursimple.core.data.term.DataStoreTermProfileRepository
import com.x500x.cursimple.core.data.widget.DataStoreWidgetPreferencesRepository
import com.x500x.cursimple.core.data.widget.WidgetThemePreferences
import com.x500x.cursimple.core.kernel.time.BeijingTime
import com.x500x.cursimple.core.reminder.ReminderPlanner
import kotlinx.coroutines.flow.first
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter

internal data class ReminderRowData(
    val id: String,
    val dateLabel: String,
    val timeLabel: String,
    val title: String,
    val message: String,
    val countdown: String,
    val accentPrimary: Boolean,
) {
    val stableId: Long = id.hashCode().toLong()
}

internal data class ReminderWidgetData(
    val widgetTheme: WidgetThemePreferences,
    val totalCount: Int,
    val emptyTitle: String,
    val emptySubtitle: String?,
    val rows: List<ReminderRowData>,
) {
    val themeAccent: ThemeAccent = widgetTheme.themeAccent
}

internal object ReminderDataSource {
    suspend fun load(context: Context): ReminderWidgetData {
        val appContext = context.applicationContext
        val termProfileRepository = DataStoreTermProfileRepository(appContext)
        val scheduleRepository = DataStoreScheduleRepository(appContext, termProfileRepository)
        val reminderRepository = DataStoreReminderRepository(appContext)
        val widgetPreferencesRepository = DataStoreWidgetPreferencesRepository(appContext)
        val userPreferencesRepository = DataStoreUserPreferencesRepository(appContext)

        val schedule = scheduleRepository.scheduleFlow.first()
        val rules = reminderRepository.reminderRulesFlow.first().filter { it.enabled }
        val alarmRecords = reminderRepository.systemAlarmRecordsFlow.first()
        val timingProfile = widgetPreferencesRepository.timingProfileFlow.first()
        val widgetTheme = widgetPreferencesRepository.themePreferencesFlow.first()
        val userPrefs = userPreferencesRepository.preferencesFlow.first()
        val zone = BeijingTime.zone
        BeijingTime.setForcedNow(userPrefs.debugForcedDateTime)

        val now = BeijingTime.nowMillis(zone)
        val today = BeijingTime.todayIn(zone)
        val planner = ReminderPlanner()
        val plans = if (schedule != null && timingProfile != null) {
            rules.flatMap { rule ->
                runCatching {
                    planner.expandRule(
                        rule = rule,
                        schedule = schedule,
                        timingProfile = timingProfile,
                        fromDate = today,
                        temporaryScheduleOverrides = userPrefs.temporaryScheduleOverrides,
                    )
                }.getOrDefault(emptyList())
            }.filter { it.triggerAtMillis >= now }
                .sortedBy { it.triggerAtMillis }
        } else emptyList()

        val entries = buildReminderWidgetEntries(
            plans = plans,
            records = alarmRecords,
            nowMillis = now,
        )

        val rows = entries.map { entry ->
            val ts = Instant.ofEpochMilli(entry.triggerAtMillis).atZone(zone).toLocalDateTime()
            val isToday = ts.toLocalDate() == today
            val isSoon = (entry.triggerAtMillis - now) <= 60 * 60 * 1000L
            ReminderRowData(
                id = entry.id,
                dateLabel = formatDateLabel(ts.toLocalDate(), today),
                timeLabel = ts.toLocalTime().withSecond(0).withNano(0).toString().substring(0, 5),
                title = entry.title,
                message = entry.message,
                countdown = formatCountdown(entry.triggerAtMillis - now),
                accentPrimary = isToday || isSoon,
            )
        }

        val emptyTitle = if (rules.isEmpty()) "尚未设置提醒规则" else "暂无即将到来的提醒"
        val emptySubtitle = if (rules.isEmpty()) "在应用内为课程添加提醒" else "未来一段时间没有规划"

        return ReminderWidgetData(
            widgetTheme = widgetTheme,
            totalCount = entries.size,
            emptyTitle = emptyTitle,
            emptySubtitle = emptySubtitle,
            rows = rows,
        )
    }

    private fun formatDateLabel(date: LocalDate, today: LocalDate): String {
        val days = java.time.temporal.ChronoUnit.DAYS.between(today, date).toInt()
        return when (days) {
            0 -> "今天"
            1 -> "明天"
            2 -> "后天"
            else -> DateTimeFormatter.ofPattern("M/d").format(date)
        }
    }

    private fun formatCountdown(diffMillis: Long): String {
        if (diffMillis <= 0) return "即将"
        val totalMinutes = Duration.ofMillis(diffMillis).toMinutes()
        return when {
            totalMinutes < 60 -> "${totalMinutes}分钟"
            totalMinutes < 24 * 60 -> {
                val h = totalMinutes / 60
                val m = totalMinutes % 60
                if (m == 0L) "${h}小时" else "${h}小时${m}分"
            }
            else -> {
                val days = totalMinutes / (24 * 60)
                "${days}天后"
            }
        }
    }
}
