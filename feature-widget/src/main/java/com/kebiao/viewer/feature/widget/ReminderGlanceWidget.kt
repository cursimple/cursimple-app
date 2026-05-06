@file:OptIn(androidx.glance.ExperimentalGlanceApi::class)

package com.kebiao.viewer.feature.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.os.Bundle
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.LocalSize
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.kebiao.viewer.core.data.DataStoreScheduleRepository
import com.kebiao.viewer.core.data.DataStoreUserPreferencesRepository
import com.kebiao.viewer.core.data.reminder.DataStoreReminderRepository
import com.kebiao.viewer.core.data.term.DataStoreTermProfileRepository
import com.kebiao.viewer.core.data.widget.DataStoreWidgetPreferencesRepository
import com.kebiao.viewer.core.kernel.time.BeijingTime
import com.kebiao.viewer.core.reminder.ReminderPlanner
import com.kebiao.viewer.core.reminder.model.AppAlarmOperationMode
import com.kebiao.viewer.core.reminder.model.ReminderAlarmBackend
import com.kebiao.viewer.core.reminder.model.ReminderPlan
import com.kebiao.viewer.core.reminder.model.SystemAlarmRecord
import kotlinx.coroutines.flow.first
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

class ReminderGlanceWidget : GlanceAppWidget() {

    override val sizeMode: SizeMode = SizeMode.Responsive(
        setOf(
            DpSize(160.dp, 110.dp),
            DpSize(220.dp, 160.dp),
            DpSize(300.dp, 240.dp),
        ),
    )

    override suspend fun provideGlance(context: Context, id: GlanceId) {
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
        val userPrefs = userPreferencesRepository.preferencesFlow.first()
        val zone = BeijingTime.resolveZone(userPrefs.timeZoneId)
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

        provideContent {
            val sizeClass = WidgetSizeClass.fromDp(
                widthDp = LocalSize.current.width.value.roundToInt(),
                heightDp = LocalSize.current.height.value.roundToInt(),
            )
            val visibleEntries = entries.take(sizeClass.reminderRows())

            GlanceTheme {
                WidgetCard(sizeClass = sizeClass) {
                    Row(
                        modifier = GlanceModifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "🔔",
                            style = TextStyle(
                                color = GlanceTheme.colors.primary,
                                fontSize = 13.sp,
                            ),
                        )
                        Spacer(GlanceModifier.width(6.dp))
                        Text(
                            text = "课程提醒",
                            style = TextStyle(
                                color = GlanceTheme.colors.onBackground,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                            ),
                        )
                        Box(modifier = GlanceModifier.defaultWeight()) { Spacer(GlanceModifier.height(0.dp)) }
                        if (entries.isNotEmpty()) {
                            PillBadge(
                                text = "${entries.size} 条",
                                container = GlanceTheme.colors.primaryContainer,
                                onContainer = GlanceTheme.colors.onPrimaryContainer,
                            )
                        }
                    }
                    Spacer(GlanceModifier.height(if (sizeClass == WidgetSizeClass.Compact) 6.dp else 8.dp))
                    if (visibleEntries.isEmpty()) {
                        EmptyReminders(sizeClass = sizeClass, hasRules = rules.isNotEmpty())
                    } else {
                        visibleEntries.forEachIndexed { index, entry ->
                            val ts = Instant.ofEpochMilli(entry.triggerAtMillis).atZone(zone).toLocalDateTime()
                            val isToday = ts.toLocalDate() == today
                            val isSoon = (entry.triggerAtMillis - now) <= 60 * 60 * 1000L
                            ReminderEntry(
                                dateLabel = formatDateLabel(ts.toLocalDate(), today),
                                timeLabel = ts.toLocalTime().withSecond(0).withNano(0).toString().substring(0, 5),
                                title = entry.title,
                                message = entry.message,
                                countdown = formatCountdown(entry.triggerAtMillis - now),
                                accentPrimary = isToday || isSoon,
                            )
                            if (index < visibleEntries.lastIndex) {
                                Spacer(GlanceModifier.height(6.dp))
                            }
                        }
                    }
                }
            }
        }
    }

    suspend fun refreshAll(context: Context) {
        updateAll(context)
    }
}

internal data class ReminderWidgetEntry(
    val id: String,
    val triggerAtMillis: Long,
    val title: String,
    val message: String,
)

internal fun buildReminderWidgetEntries(
    plans: List<ReminderPlan>,
    records: List<SystemAlarmRecord>,
    nowMillis: Long,
): List<ReminderWidgetEntry> {
    val planEntries = plans
        .asSequence()
        .filter { it.triggerAtMillis >= nowMillis }
        .map { plan ->
            ReminderWidgetEntry(
                id = "plan:${plan.planId}:${plan.triggerAtMillis}",
                triggerAtMillis = plan.triggerAtMillis,
                title = plan.title.ifBlank { "课程提醒" },
                message = plan.message.ifBlank { "课程即将开始" },
            )
        }
    val snoozeEntries = records
        .asSequence()
        .filter { it.backend == ReminderAlarmBackend.AppAlarmClock }
        .filter { it.operationMode == AppAlarmOperationMode.SnoozeForegroundService }
        .filter { it.triggerAtMillis >= nowMillis }
        .map { record ->
            ReminderWidgetEntry(
                id = "record:${record.backend.name}:${record.alarmKey}",
                triggerAtMillis = record.triggerAtMillis,
                title = firstNotBlank(record.displayTitle, record.alarmLabel, record.message)
                    ?: "课程提醒",
                message = firstNotBlank(record.displayMessage) ?: "已延后 5 分钟",
            )
        }
    return (planEntries + snoozeEntries)
        .distinctBy { it.id }
        .sortedWith(
            compareBy<ReminderWidgetEntry> { it.triggerAtMillis }
                .thenBy { it.title }
                .thenBy { it.id },
        )
        .toList()
}

private fun firstNotBlank(vararg values: String?): String? =
    values.firstOrNull { !it.isNullOrBlank() }

@Composable
private fun ReminderEntry(
    dateLabel: String,
    timeLabel: String,
    title: String,
    message: String,
    countdown: String,
    accentPrimary: Boolean,
) {
    val accent = if (accentPrimary) GlanceTheme.colors.primary else GlanceTheme.colors.tertiary
    AccentRow(accent = accent) {
        Column(modifier = GlanceModifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = dateLabel,
                    style = TextStyle(
                        color = GlanceTheme.colors.onSurfaceVariant,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium,
                    ),
                )
                Spacer(GlanceModifier.width(6.dp))
                Text(
                    text = timeLabel,
                    style = TextStyle(
                        color = GlanceTheme.colors.primary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                    ),
                )
                Box(modifier = GlanceModifier.defaultWeight()) { Spacer(GlanceModifier.height(0.dp)) }
                PillBadge(
                    text = countdown,
                    container = if (accentPrimary) GlanceTheme.colors.primaryContainer
                    else GlanceTheme.colors.surfaceVariant,
                    onContainer = if (accentPrimary) GlanceTheme.colors.onPrimaryContainer
                    else GlanceTheme.colors.onSurfaceVariant,
                )
            }
            Spacer(GlanceModifier.height(2.dp))
            Text(
                text = title,
                style = TextStyle(
                    color = GlanceTheme.colors.onSurface,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                ),
                maxLines = 1,
            )
            Text(
                text = message,
                style = TextStyle(
                    color = GlanceTheme.colors.onSurfaceVariant,
                    fontSize = 11.sp,
                ),
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun EmptyReminders(sizeClass: WidgetSizeClass, hasRules: Boolean) {
    Box(
        modifier = GlanceModifier
            .fillMaxWidth()
            .background(GlanceTheme.colors.surfaceVariant)
            .cornerRadius(WidgetStyle.rowCorner)
            .padding(
                horizontal = if (sizeClass == WidgetSizeClass.Compact) 10.dp else 12.dp,
                vertical = if (sizeClass == WidgetSizeClass.Compact) 12.dp else 16.dp,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = if (hasRules) "🌙" else "🔕",
                style = TextStyle(
                    color = GlanceTheme.colors.onSurfaceVariant,
                    fontSize = if (sizeClass == WidgetSizeClass.Compact) 18.sp else 20.sp,
                ),
            )
            Spacer(GlanceModifier.height(if (sizeClass == WidgetSizeClass.Compact) 2.dp else 4.dp))
            Text(
                text = if (hasRules) {
                    if (sizeClass == WidgetSizeClass.Compact) "暂无提醒" else "暂无即将到来的提醒"
                } else {
                    if (sizeClass == WidgetSizeClass.Compact) "尚未设置提醒" else "尚未设置提醒规则"
                },
                style = TextStyle(
                    color = GlanceTheme.colors.onSurface,
                    fontSize = if (sizeClass == WidgetSizeClass.Compact) 12.sp else 13.sp,
                    fontWeight = FontWeight.Bold,
                ),
            )
            if (sizeClass != WidgetSizeClass.Compact) {
                Text(
                    text = if (hasRules) "未来一段时间没有规划" else "在应用内为课程添加提醒",
                    style = TextStyle(
                        color = GlanceTheme.colors.onSurfaceVariant,
                        fontSize = 11.sp,
                    ),
                )
            }
        }
    }
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

open class ReminderGlanceWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = ReminderGlanceWidget()

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        reconcileSystemAlarmsFromWidget(context)
        super.onUpdate(context, appWidgetManager, appWidgetIds)
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle,
    ) {
        reconcileSystemAlarmsFromWidget(context)
        super.onAppWidgetOptionsChanged(context, appWidgetManager, appWidgetId, newOptions)
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        super.onDeleted(context, appWidgetIds)
        WidgetLifecycleRefresher.onWidgetSetChanged(context, reason = "reminder_widget_deleted")
    }
}

class ReminderGlanceWidgetReceiverMIUI : ReminderGlanceWidgetReceiver()
