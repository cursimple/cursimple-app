package com.x500x.cursimple.feature.widget

import com.x500x.cursimple.core.kernel.mood.dayMoodLine
import com.x500x.cursimple.core.kernel.mood.DayMood
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import com.x500x.cursimple.core.data.DataStoreUserPreferencesRepository
import com.x500x.cursimple.core.data.widget.DataStoreWidgetPreferencesRepository
import com.x500x.cursimple.core.data.widget.PendingTask
import com.x500x.cursimple.core.data.widget.PendingTaskFeed
import com.x500x.cursimple.core.data.widget.PendingTaskMomentKind
import com.x500x.cursimple.core.data.widget.WidgetThemePreferences
import com.x500x.cursimple.core.data.widget.nextMoment
import com.x500x.cursimple.core.data.widget.resolveAccent
import com.x500x.cursimple.core.data.widget.sortedForWidget
import com.x500x.cursimple.core.kernel.time.BeijingTime
import com.x500x.cursimple.core.reminder.logging.ReminderLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

/** 一条任务离现在多近，决定右侧那枚标记的文字与颜色 */
internal sealed interface PendingTaskUrgency {
    /** 截止已过 */
    data object Overdue : PendingTaskUrgency
    /** 只有开始时间且已开始 */
    data object Started : PendingTaskUrgency
    /** 一小时内 */
    data class Minutes(val left: Int) : PendingTaskUrgency
    /** 一天内 */
    data class Hours(val left: Int) : PendingTaskUrgency
    /** 一天以后，按日历天数 */
    data class Days(val left: Int) : PendingTaskUrgency
    data object NoTime : PendingTaskUrgency
}

internal data class PendingTaskRow(
    val task: PendingTask,
    val momentKind: PendingTaskMomentKind?,
    val momentAt: Long?,
    val urgency: PendingTaskUrgency,
    /** 时刻那天离今天几天：0 今天、1 明天，用来写「今天 23:59」 */
    val dayDelta: Long?,
) {
    val stableId: Long = task.id.hashCode().toLong()

    /** 已过或一天内的标红 */
    val urgent: Boolean
        get() = urgency is PendingTaskUrgency.Overdue || urgency is PendingTaskUrgency.Started ||
            urgency is PendingTaskUrgency.Minutes || urgency is PendingTaskUrgency.Hours
}

internal fun pendingTaskRows(tasks: List<PendingTask>, nowMillis: Long, zone: ZoneId): List<PendingTaskRow> {
    val today = Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate()
    return tasks.sortedForWidget(nowMillis).map { task ->
        val moment = task.nextMoment(nowMillis)
        val urgency = when {
            moment == null -> PendingTaskUrgency.NoTime
            moment.atMillis < nowMillis ->
                if (moment.kind == PendingTaskMomentKind.Due) PendingTaskUrgency.Overdue else PendingTaskUrgency.Started
            moment.atMillis - nowMillis < HOUR_MS ->
                PendingTaskUrgency.Minutes((((moment.atMillis - nowMillis) + MINUTE_MS - 1) / MINUTE_MS).toInt().coerceAtLeast(1))
            moment.atMillis - nowMillis < DAY_MS ->
                PendingTaskUrgency.Hours((((moment.atMillis - nowMillis) + HOUR_MS - 1) / HOUR_MS).toInt().coerceIn(1, 24))
            else -> PendingTaskUrgency.Days(
                ChronoUnit.DAYS.between(today, Instant.ofEpochMilli(moment.atMillis).atZone(zone).toLocalDate()).toInt().coerceAtLeast(1),
            )
        }
        PendingTaskRow(
            task = task,
            momentKind = moment?.kind,
            momentAt = moment?.atMillis,
            urgency = urgency,
            dayDelta = moment?.let { ChronoUnit.DAYS.between(today, Instant.ofEpochMilli(it.atMillis).atZone(zone).toLocalDate()) },
        )
    }
}

private const val MINUTE_MS = 60_000L
private const val HOUR_MS = 60 * MINUTE_MS
private const val DAY_MS = 24 * HOUR_MS

internal data class PendingTaskWidgetData(
    val rows: List<PendingTaskRow>,
    val zone: ZoneId,
    val widgetTheme: WidgetThemePreferences,
)

internal object PendingTaskDataSource {
    suspend fun load(context: Context): PendingTaskWidgetData {
        val app = context.applicationContext
        val userPrefs = DataStoreUserPreferencesRepository(app).preferencesFlow.first()
        val theme = DataStoreWidgetPreferencesRepository(app).themePreferencesFlow.first()
            .resolveAccent(userPrefs.themeAccent, userPrefs.themeCustomColorArgb)
        BeijingTime.setForcedNow(userPrefs.debugForcedDateTime)
        val zone = BeijingTime.zone
        val now = BeijingTime.nowDateTimeIn(zone).atZone(zone).toInstant().toEpochMilli()
        return PendingTaskWidgetData(pendingTaskRows(PendingTaskFeed.read(app), now, zone), zone, theme)
    }
}

/**
 * 按小组件实际高度能完整放下几行：最后一行被裁掉一半很难看，总数已由角标给出。
 * 竖屏下桌面按最大高度摆放；读不到尺寸时按两行。
 */
internal fun taskRowsFor(manager: AppWidgetManager, appWidgetId: Int): Int {
    val height = runCatching { manager.getAppWidgetOptions(appWidgetId) }.getOrNull()
        ?.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 0)?.takeIf { it > 0 }
        ?: return 2
    return taskRowsForHeight(height)
}

/**
 * 卡片上下内边距、标题和列表上边距约 64dp（桌面还会再加一圈小组件内边距），每行约 52dp（含行间距）。
 * 宁可少放一行，也不让最后一行被裁一半。
 */
internal fun taskRowsForHeight(heightDp: Int): Int = ((heightDp - 64) / 52).coerceIn(1, 8)

/** 桌面「待完成」：组件交来的未完成任务，按时间排好，点一条打开它所在组件的页面 */
open class PendingTaskWidgetReceiver : AppWidgetProvider() {
    override fun onReceive(context: Context, intent: Intent) {
        if (handleVendorWidgetUpdate(context, intent) { updateWidgets(it) }) return
        super.onReceive(context, intent)
    }

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        launchAsync { updateWidgets(context.applicationContext) }
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        reconcileSystemAlarmsFromWidget(context)
        launchAsync { updateWidgets(context.applicationContext, appWidgetIds) }
    }

    override fun onAppWidgetOptionsChanged(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int, newOptions: Bundle) {
        super.onAppWidgetOptionsChanged(context, appWidgetManager, appWidgetId, newOptions)
        launchAsync { updateWidgets(context.applicationContext, intArrayOf(appWidgetId)) }
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        super.onDeleted(context, appWidgetIds)
        WidgetLifecycleRefresher.onWidgetSetChanged(context.applicationContext, reason = "tasks_widget_deleted")
    }

    private fun launchAsync(block: suspend () -> Unit) {
        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            try {
                block()
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        internal const val CATALOG_ID = "tasks"

        @Suppress("DEPRECATION")
        suspend fun updateWidgets(context: Context, appWidgetIds: IntArray? = null) {
            val appContext = context.widgetLocaleContext()
            val manager = AppWidgetManager.getInstance(appContext)
            val ids = appWidgetIds ?: catalogWidgetIds(appContext, manager, CATALOG_ID)
            if (ids.isEmpty()) return
            val data = runCatching { PendingTaskDataSource.load(appContext) }
                .onFailure { ReminderLogger.warn("widget.tasks.load.failure", emptyMap(), it) }
                .getOrNull() ?: return
            ids.forEach { appWidgetId ->
                manager.updateAppWidget(appWidgetId, buildViews(appContext, manager, appWidgetId, data))
                manager.notifyAppWidgetViewDataChanged(intArrayOf(appWidgetId), R.id.tasks_list)
            }
        }

        @Suppress("DEPRECATION")
        private fun buildViews(context: Context, manager: AppWidgetManager, appWidgetId: Int, data: PendingTaskWidgetData): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.widget_tasks)
            val theme = data.widgetTheme
            views.applyWidgetBackground(context, R.id.tasks_root, theme)
            views.applyOpenAppClick(context, R.id.tasks_root, appWidgetId, theme)
            // 布局里的字跟着系统语言走，标题按应用语言在这里重设
            views.setTextViewText(R.id.tasks_title, context.getString(R.string.widget_label_tasks))
            val total = data.rows.size
            views.setViewVisibility(R.id.tasks_badge, if (total > 0) View.VISIBLE else View.GONE)
            views.setTextViewText(R.id.tasks_badge, context.getString(R.string.widget_tasks_badge, total))
            val visible = data.rows.take(taskRowsFor(manager, appWidgetId))
            views.setWidgetRows(
                listId = R.id.tasks_list,
                rows = visible,
                stableId = { it.stableId },
                buildRow = { buildPendingTaskRow(context, it, data.zone, theme) },
                fallbackAdapter = {
                    views.setRemoteAdapter(
                        R.id.tasks_list,
                        Intent(context, PendingTaskRemoteViewsService::class.java).apply {
                            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                            putExtra(EXTRA_WIDGET_LIST_REVISION, widgetListRevision(total, visible.map { it.task }))
                            this.data = Uri.parse(toUri(Intent.URI_INTENT_SCHEME))
                        },
                    )
                },
            )
            views.applyOpenAppListTemplate(context, R.id.tasks_list, appWidgetId, theme)
            views.setEmptyView(R.id.tasks_list, R.id.tasks_empty)
            val hasRows = visible.isNotEmpty()
            views.setViewVisibility(R.id.tasks_list, if (hasRows) View.VISIBLE else View.GONE)
            views.setViewVisibility(R.id.tasks_empty, if (hasRows) View.GONE else View.VISIBLE)
            views.applyOpenAppClick(context, R.id.tasks_empty, appWidgetId, theme)
            views.applyAccentBackground(R.id.tasks_empty, theme, WidgetSurfaceTone.RowVariant)
            // 第一行每天换一句，第二行照旧说明任务从哪儿来
            val clear = context.dayMoodLine(BeijingTime.today(), DayMood.TasksClear)
            views.setTextViewText(
                R.id.tasks_empty,
                clear?.let { "$it\n${context.getString(R.string.widget_tasks_empty_hint)}" }
                    ?: context.getString(R.string.widget_tasks_empty),
            )
            return views
        }
    }
}

class PendingTaskWidgetReceiverMIUI : PendingTaskWidgetReceiver()

/** 一行任务，列表服务与内联行共用 */
internal fun buildPendingTaskRow(
    context: Context,
    row: PendingTaskRow,
    zone: ZoneId,
    theme: WidgetThemePreferences,
): RemoteViews {
    val views = RemoteViews(context.packageName, R.layout.widget_task_row)
    views.applyAccentBackground(R.id.task_row_root, theme, WidgetSurfaceTone.Row)
    if (theme.openAppOnDoubleClickEnabled) {
        // 和列表上的打开应用模板合并：带上组件 id，进 App 后直接打开那个组件的页面
        views.setOnClickFillInIntent(
            R.id.task_row_root,
            Intent().putExtra(WidgetDeepLinks.EXTRA_OPEN_COMPONENT_PAGE, row.task.sourceId),
        )
    }
    val accent = widgetAccentTextColor(theme)
    views.setInt(R.id.task_bar, "setColorFilter", row.task.colorArgb?.toInt() ?: accent)
    views.setTextViewText(R.id.task_title, row.task.title)
    val meta = listOf(row.task.typeLabel, row.task.group).filter(String::isNotBlank)
        .ifEmpty { listOf(row.task.sourceTitle) }
        .joinToString(" · ")
    views.setTextViewText(R.id.task_meta, meta)
    views.setTextViewText(R.id.task_when, pendingTaskWhenText(context, row, zone))
    views.setTextColor(R.id.task_when, if (row.urgent) URGENT_TEXT else accent)
    views.setTextViewText(R.id.task_countdown, pendingTaskUrgencyText(context, row.urgency))
    views.setInt(R.id.task_countdown, "setBackgroundResource", if (row.urgent) R.drawable.widget_bg_badge_alert else R.drawable.widget_bg_badge)
    views.setTextColor(R.id.task_countdown, if (row.urgent) URGENT_TEXT else NORMAL_BADGE_TEXT)
    return views
}

private const val URGENT_TEXT = 0xFFB3261E.toInt()
private const val NORMAL_BADGE_TEXT = 0xFF244E72.toInt()
private val CLOCK: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

private fun pendingTaskWhenText(context: Context, row: PendingTaskRow, zone: ZoneId): String {
    val at = row.momentAt ?: return context.getString(R.string.widget_tasks_no_time)
    val time = Instant.ofEpochMilli(at).atZone(zone)
    val clock = time.format(CLOCK)
    val day = when (row.dayDelta) {
        0L -> context.getString(R.string.widget_tasks_today_time, clock)
        1L -> context.getString(R.string.widget_tasks_tomorrow_time, clock)
        else -> "${context.widgetMonthDayText(time.toLocalDate())} $clock"
    }
    return context.getString(
        if (row.momentKind == PendingTaskMomentKind.Start) R.string.widget_tasks_start_at else R.string.widget_tasks_due_at,
        day,
    )
}

private fun pendingTaskUrgencyText(context: Context, urgency: PendingTaskUrgency): String = when (urgency) {
    PendingTaskUrgency.Overdue -> context.getString(R.string.widget_tasks_overdue)
    PendingTaskUrgency.Started -> context.getString(R.string.widget_tasks_started)
    is PendingTaskUrgency.Minutes -> context.getString(R.string.widget_tasks_left_minutes, urgency.left)
    is PendingTaskUrgency.Hours -> context.getString(R.string.widget_tasks_left_hours, urgency.left)
    is PendingTaskUrgency.Days -> context.getString(R.string.widget_tasks_left_days, urgency.left)
    PendingTaskUrgency.NoTime -> "—"
}

/** API 31 以下列表走适配器服务 */
class PendingTaskRemoteViewsService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory {
        val appWidgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        return PendingTaskListFactory(applicationContext, appWidgetId)
    }
}

private class PendingTaskListFactory(appContext: Context, private val appWidgetId: Int) : RemoteViewsService.RemoteViewsFactory {
    private var context: Context = appContext.widgetLocaleContext()
    private var rows: List<PendingTaskRow> = emptyList()
    private var zone: ZoneId = ZoneId.systemDefault()
    private var theme = WidgetThemePreferences()

    override fun onCreate() = Unit

    override fun onDataSetChanged() {
        context = context.widgetLocaleContext()
        runCatching { runBlocking(Dispatchers.IO) { PendingTaskDataSource.load(context) } }
            .onSuccess { data ->
                rows = data.rows.take(taskRowsFor(AppWidgetManager.getInstance(context), appWidgetId))
                zone = data.zone
                theme = data.widgetTheme
            }
    }

    override fun onDestroy() {
        rows = emptyList()
    }

    override fun getCount(): Int = rows.size

    override fun getViewAt(position: Int): RemoteViews =
        rows.getOrNull(position)?.let { buildPendingTaskRow(context, it, zone, theme) }
            ?: RemoteViews(context.packageName, R.layout.widget_task_row)

    override fun getLoadingView(): RemoteViews? = null
    override fun getViewTypeCount(): Int = 1
    override fun getItemId(position: Int): Long = rows.getOrNull(position)?.stableId ?: position.toLong()
    override fun hasStableIds(): Boolean = true
}
