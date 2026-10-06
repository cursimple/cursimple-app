package com.x500x.cursimple.feature.widget

import com.x500x.cursimple.core.kernel.mood.dayMoodLine
import com.x500x.cursimple.core.kernel.mood.DayMood
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.os.Build
import android.util.SizeF
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.RelativeSizeSpan
import android.text.style.StyleSpan
import android.view.View
import android.widget.RemoteViews
import android.widget.FrameLayout
import kotlinx.coroutines.withContext
import com.x500x.cursimple.core.data.widget.WidgetThemePreferences
import com.x500x.cursimple.core.reminder.logging.ReminderLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.TextStyle

/** Week/month calendar widget with date navigation into the app's day view. */
open class CalendarWidgetReceiver : AppWidgetProvider() {
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

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle,
    ) {
        super.onAppWidgetOptionsChanged(context, appWidgetManager, appWidgetId, newOptions)
        launchAsync { updateWidgets(context.applicationContext, intArrayOf(appWidgetId)) }
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        super.onDeleted(context, appWidgetIds)
        appWidgetIds.forEach { CalendarWidgetState.clear(context, it) }
        WidgetLifecycleRefresher.onWidgetSetChanged(context.applicationContext, reason = "calendar_widget_deleted")
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
        suspend fun updateWidgets(context: Context, appWidgetIds: IntArray? = null) {
            val appContext = context.widgetLocaleContext()
            val manager = AppWidgetManager.getInstance(appContext)
            val ids = appWidgetIds ?: catalogWidgetIds(appContext, manager, CATALOG_ID)
            ids.forEach { appWidgetId ->
                runCatching {
                    val data = CalendarWidgetDataSource.load(appContext, appWidgetId)
                    manager.updateAppWidget(appWidgetId, sizedViews(appContext, manager, appWidgetId, data))
                }.onFailure { error ->
                    ReminderLogger.warn("widget.calendar.update.failure", mapOf("id" to appWidgetId), error)
                }
            }
        }

        internal const val CATALOG_ID = "calendar"

        private suspend fun sizedViews(context: Context, manager: AppWidgetManager, id: Int, data: CalendarWidgetData): RemoteViews {
            val options = manager.getAppWidgetOptions(id)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                @Suppress("DEPRECATION")
                val sizes = options.getParcelableArrayList<SizeF>(AppWidgetManager.OPTION_APPWIDGET_SIZES)
                    .orEmpty().filter { it.width > 0 && it.height > 0 }.distinct().take(4)
                if (sizes.isNotEmpty()) return RemoteViews(sizes.associateWith { size ->
                    buildViews(context, id, data, calendarBodySizeDp(size.width, size.height))
                })
            }
            // Render orientation bounds separately for older launchers rather than stretching one bitmap.
            val minW = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, DEFAULT_WIDTH_DP).takeIf { it > 0 } ?: DEFAULT_WIDTH_DP
            val maxW = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, minW).coerceAtLeast(minW)
            val minH = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, DEFAULT_HEIGHT_DP).takeIf { it > 0 } ?: DEFAULT_HEIGHT_DP
            val maxH = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, minH).coerceAtLeast(minH)
            val portrait = buildViews(context, id, data, calendarBodySizeDp(minW.toFloat(), maxH.toFloat()))
            val landscape = buildViews(context, id, data, calendarBodySizeDp(maxW.toFloat(), minH.toFloat()))
            return RemoteViews(landscape, portrait)
        }

        private suspend fun buildViews(
            context: Context,
            appWidgetId: Int,
            data: CalendarWidgetData,
            bodySize: Pair<Float, Float>,
        ): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.widget_calendar)
            val theme = data.widgetTheme
            views.applyWidgetBackground(context, R.id.calendar_root, theme)
            views.applyOpenAppClick(context, R.id.calendar_root, appWidgetId, theme)

            val week = data.mode == CalendarWidgetMode.Week
            views.setTextViewText(R.id.calendar_title, calendarTitle(context, data))
            val (widthDp, heightDp) = bodySize
            // Move the legend to the subtitle on narrow widgets so week labels remain readable.
            val legend = data.week?.legend()?.takeUnless { it.isEmpty }?.let { calendarLegendText(context, it, theme, compact = widthDp < WIDE_HEADER_MIN_DP) }
            val legendInHeader = legend != null && widthDp >= WIDE_HEADER_MIN_DP
            views.setTextViewText(R.id.calendar_legend, if (legendInHeader) legend else "")
            views.setViewVisibility(R.id.calendar_legend, if (legendInHeader) View.VISIBLE else View.GONE)
            val subtitle: CharSequence? = if (legend != null && !legendInHeader) legend else calendarSubtitle(context, data)
            views.setTextViewText(R.id.calendar_subtitle, subtitle ?: "")
            views.setViewVisibility(R.id.calendar_subtitle, if (subtitle == null) View.GONE else View.VISIBLE)

            views.setTextViewText(
                R.id.calendar_mode,
                context.getString(if (week) R.string.widget_calendar_mode_week else R.string.widget_calendar_mode_month),
            )
            views.setContentDescription(R.id.calendar_mode, context.getString(R.string.widget_calendar_mode_switch))
            views.setTextViewText(
                R.id.calendar_reset,
                context.getString(if (week) R.string.widget_calendar_reset_week else R.string.widget_calendar_reset_month),
            )
            views.setViewVisibility(R.id.calendar_reset, if (data.offset == 0) View.GONE else View.VISIBLE)
            views.setOnClickPendingIntent(R.id.calendar_prev, actionIntent(context, appWidgetId, CalendarWidgetActionReceiver.ACTION_PREV))
            views.setOnClickPendingIntent(R.id.calendar_next, actionIntent(context, appWidgetId, CalendarWidgetActionReceiver.ACTION_NEXT))
            views.setOnClickPendingIntent(R.id.calendar_reset, actionIntent(context, appWidgetId, CalendarWidgetActionReceiver.ACTION_RESET))
            views.setOnClickPendingIntent(R.id.calendar_mode, actionIntent(context, appWidgetId, CalendarWidgetActionReceiver.ACTION_TOGGLE))

            val density = context.resources.displayMetrics.density
            val measuredBody = withContext(Dispatchers.Main) {
                val root = views.apply(context, FrameLayout(context))
                val widthPixels = ((widthDp + BODY_HORIZONTAL_INSET_DP) * density).toInt()
                root.measure(View.MeasureSpec.makeMeasureSpec(widthPixels, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
                widthDp to (heightDp + BODY_VERTICAL_INSET_DP - root.measuredHeight / density).coerceAtLeast(1f)
            }
            val bitmap = CalendarWidgetRenderer.render(
                data = data,
                widthDp = measuredBody.first,
                heightDp = measuredBody.second,
                density = density,
                labels = renderLabels(context, data),
            )
            views.setImageViewBitmap(R.id.calendar_grid, bitmap)
            bindHitArea(context, views, appWidgetId, data, density)
            return views
        }

        private fun bindHitArea(context: Context, views: RemoteViews, appWidgetId: Int, data: CalendarWidgetData, density: Float) {
            if (!data.widgetTheme.openAppOnDoubleClickEnabled) {
                views.setViewVisibility(R.id.calendar_hit_area, View.GONE)
                return
            }
            views.setViewVisibility(R.id.calendar_hit_area, View.VISIBLE)
            val days: List<LocalDate>
            val rows: Int
            if (data.week != null) {
                days = data.week.days.map { it.date }
                rows = 1
                views.setViewPadding(R.id.calendar_hit_area, (CalendarWidgetRenderer.weekGutterDp(data.week) * density).toInt(), 0, 0, 0)
            } else {
                val month = data.month ?: return
                days = month.days.map { it.date }
                rows = month.rows
                views.setViewPadding(
                    R.id.calendar_hit_area,
                    0,
                    (CalendarWidgetRenderer.MONTH_HEADER_DP * density).toInt(),
                    0,
                    (CalendarWidgetRenderer.MONTH_LEGEND_DP * density).toInt(),
                )
            }
            for (row in 0 until HIT_ROWS) {
                views.setViewVisibility(HIT_ROW_IDS[row], if (row < rows) View.VISIBLE else View.GONE)
                for (col in 0 until 7) {
                    val cellId = HIT_CELL_IDS[row][col]
                    val date = if (row < rows) days.getOrNull(row * 7 + col) else null
                    if (date == null) {
                        views.setViewVisibility(cellId, View.GONE)
                        continue
                    }
                    views.setViewVisibility(cellId, View.VISIBLE)
                    views.setContentDescription(cellId, context.widgetDateWithWeekdayText(date))
                    openDatePendingIntent(context, appWidgetId, row * 7 + col, date)?.let { views.setOnClickPendingIntent(cellId, it) }
                }
            }
        }

        private fun openDatePendingIntent(context: Context, appWidgetId: Int, index: Int, date: LocalDate): PendingIntent? {
            val launch = context.packageManager.getLaunchIntentForPackage(context.packageName) ?: return null
            launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                .putExtra(WidgetDeepLinks.EXTRA_OPEN_SCHEDULE_DATE, date.toString())
            return PendingIntent.getActivity(
                context,
                OPEN_DATE_REQUEST_BASE + appWidgetId * HIT_ROWS * 7 + index,
                launch,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }

        private fun actionIntent(context: Context, appWidgetId: Int, action: String): PendingIntent {
            val intent = Intent(context, CalendarWidgetActionReceiver::class.java).apply {
                setPackage(context.packageName)
                putExtra(CalendarWidgetActionReceiver.EXTRA_ACTION, action)
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            }
            return PendingIntent.getBroadcast(
                context,
                ACTION_REQUEST_BASE + appWidgetId * 8 + CalendarWidgetActionReceiver.ACTIONS.indexOf(action).coerceAtLeast(0),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }

        private fun calendarTitle(context: Context, data: CalendarWidgetData): String = when (data.mode) {
            CalendarWidgetMode.Week -> {
                val index = data.weekIndex
                when {
                    index == null -> weekRangeText(context, data)
                    index < 1 -> context.getString(R.string.widget_calendar_before_term_title)
                    else -> context.getString(R.string.widget_calendar_week_title, index)
                }
            }
            CalendarWidgetMode.Month -> context.getString(
                R.string.widget_calendar_month_title,
                data.anchor.year,
                data.anchor.month.getDisplayName(TextStyle.FULL, context.resources.configuration.locales[0]),
            )
        }

        private fun calendarSubtitle(context: Context, data: CalendarWidgetData): String? = when {
            data.termStartMissing -> context.getString(R.string.widget_calendar_no_term_start)
            data.mode == CalendarWidgetMode.Week -> weekRangeText(context, data)
            data.offset == 0 -> context.getString(R.string.widget_calendar_today_subtitle, context.widgetMonthDayText(data.today))
            else -> null
        }

        private fun calendarLegendText(context: Context, legend: CalendarWeekLegend, theme: WidgetThemePreferences, compact: Boolean): CharSequence {
            val items = buildList {
                if (legend.events) add("●" to CalendarWidgetRenderer.EVENT_ORANGE to R.string.widget_calendar_legend_event)
                if (legend.exam) add("□" to CalendarWidgetRenderer.EXAM_RED to R.string.widget_calendar_legend_exam)
                if (legend.holiday) add(context.getString(R.string.widget_calendar_tag_holiday) to CalendarWidgetRenderer.EXAM_RED to R.string.widget_calendar_legend_holiday)
                if (legend.makeUp) add(context.getString(R.string.widget_calendar_tag_makeup) to widgetAccentTextColor(theme) to R.string.widget_calendar_legend_makeup)
            }
            val text = SpannableStringBuilder()
            items.forEachIndexed { index, (markAndColor, label) ->
                val (mark, color) = markAndColor
                // Use one subtitle row or two-column title-area legend as space permits.
                if (index > 0) text.append(if (!compact && index % 2 == 0) "\n" else "  ")
                val start = text.length
                text.append(mark)
                text.setSpan(ForegroundColorSpan(color), start, text.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                text.setSpan(StyleSpan(Typeface.BOLD), start, text.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                if (mark == "●") text.setSpan(RelativeSizeSpan(0.7f), start, text.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                text.append(" ").append(context.getString(label))
            }
            return text
        }

        private fun weekRangeText(context: Context, data: CalendarWidgetData): String {
            val days = data.week?.days.orEmpty()
            val first = days.firstOrNull()?.date ?: data.anchor
            val last = days.lastOrNull()?.date ?: data.anchor.plusDays(6)
            return context.getString(R.string.widget_calendar_range, context.widgetMonthDayText(first), context.widgetMonthDayText(last))
        }

        private fun renderLabels(context: Context, data: CalendarWidgetData): CalendarRenderLabels {
            val empty = when {
                data.mode != CalendarWidgetMode.Week -> null
                data.termStartMissing -> context.getString(R.string.widget_calendar_empty_no_term)
                else -> context.dayMoodLine(data.today, DayMood.WeekFree)
                    ?: context.getString(R.string.widget_calendar_empty_week)
            }
            return CalendarRenderLabels(
                weekdays = context.resources.getStringArray(R.array.widget_calendar_weekdays).toList(),
                holidayTag = context.getString(R.string.widget_calendar_tag_holiday),
                makeUpTag = context.getString(R.string.widget_calendar_tag_makeup),
                legendClass = context.getString(R.string.widget_calendar_legend_class),
                legendExam = context.getString(R.string.widget_calendar_legend_exam),
                legendEvent = context.getString(R.string.widget_calendar_legend_event),
                emptyMessage = empty,
            )
        }

        /**
         * Grid dimensions exclude padding and header; portrait uses minimum width and maximum
         * height, defaulting to a four-column size.
         */
        private fun calendarBodySizeDp(width: Float, height: Float): Pair<Float, Float> {
            return (width - BODY_HORIZONTAL_INSET_DP).coerceAtLeast(60f) to
                (height - BODY_VERTICAL_INSET_DP).coerceAtLeast(60f)
        }

        private const val WIDE_HEADER_MIN_DP = 290
        private const val DEFAULT_WIDTH_DP = 300
        private const val DEFAULT_HEIGHT_DP = 300
        private const val BODY_HORIZONTAL_INSET_DP = 16
        private const val BODY_VERTICAL_INSET_DP = 16 + 34 + 4

        private const val ACTION_REQUEST_BASE = 610000
        private const val OPEN_DATE_REQUEST_BASE = 620000
        private const val HIT_ROWS = 6

        private val HIT_ROW_IDS = intArrayOf(
            R.id.calendar_hit_row_0, R.id.calendar_hit_row_1, R.id.calendar_hit_row_2,
            R.id.calendar_hit_row_3, R.id.calendar_hit_row_4, R.id.calendar_hit_row_5,
        )
        private val HIT_CELL_IDS = arrayOf(
            intArrayOf(R.id.calendar_hit_0_0, R.id.calendar_hit_0_1, R.id.calendar_hit_0_2, R.id.calendar_hit_0_3, R.id.calendar_hit_0_4, R.id.calendar_hit_0_5, R.id.calendar_hit_0_6),
            intArrayOf(R.id.calendar_hit_1_0, R.id.calendar_hit_1_1, R.id.calendar_hit_1_2, R.id.calendar_hit_1_3, R.id.calendar_hit_1_4, R.id.calendar_hit_1_5, R.id.calendar_hit_1_6),
            intArrayOf(R.id.calendar_hit_2_0, R.id.calendar_hit_2_1, R.id.calendar_hit_2_2, R.id.calendar_hit_2_3, R.id.calendar_hit_2_4, R.id.calendar_hit_2_5, R.id.calendar_hit_2_6),
            intArrayOf(R.id.calendar_hit_3_0, R.id.calendar_hit_3_1, R.id.calendar_hit_3_2, R.id.calendar_hit_3_3, R.id.calendar_hit_3_4, R.id.calendar_hit_3_5, R.id.calendar_hit_3_6),
            intArrayOf(R.id.calendar_hit_4_0, R.id.calendar_hit_4_1, R.id.calendar_hit_4_2, R.id.calendar_hit_4_3, R.id.calendar_hit_4_4, R.id.calendar_hit_4_5, R.id.calendar_hit_4_6),
            intArrayOf(R.id.calendar_hit_5_0, R.id.calendar_hit_5_1, R.id.calendar_hit_5_2, R.id.calendar_hit_5_3, R.id.calendar_hit_5_4, R.id.calendar_hit_5_5, R.id.calendar_hit_5_6),
        )
    }
}

/** Vendor provider copy with separate launcher metadata. */
class CalendarWidgetReceiverMIUI : CalendarWidgetReceiver()

class CalendarWidgetActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val appWidgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        val action = intent.getStringExtra(EXTRA_ACTION) ?: return
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) return
        val app = context.applicationContext
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            try {
                val todayIso = widgetTodayIso(app)
                when (action) {
                    ACTION_PREV -> CalendarWidgetState.shift(app, appWidgetId, -1, todayIso)
                    ACTION_NEXT -> CalendarWidgetState.shift(app, appWidgetId, 1, todayIso)
                    ACTION_RESET -> CalendarWidgetState.reset(app, appWidgetId)
                    ACTION_TOGGLE -> CalendarWidgetState.toggleMode(app, appWidgetId)
                    else -> return@launch
                }
                CalendarWidgetReceiver.updateWidgets(app, intArrayOf(appWidgetId))
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_PREV = "prev"
        const val ACTION_NEXT = "next"
        const val ACTION_RESET = "reset"
        const val ACTION_TOGGLE = "toggle"
        const val EXTRA_ACTION = "calendar_widget_action"
        internal val ACTIONS = listOf(ACTION_PREV, ACTION_NEXT, ACTION_RESET, ACTION_TOGGLE)
    }
}

internal fun catalogWidgetIds(context: Context, manager: AppWidgetManager, catalogId: String): IntArray =
    WidgetCatalog.entries(context)
        .firstOrNull { it.id == catalogId }
        ?.let { entry ->
            (listOf(entry.provider) + entry.vendorProviders).flatMap { component ->
                runCatching { manager.getAppWidgetIds(component).toList() }.getOrDefault(emptyList())
            }
        }
        .orEmpty()
        .toIntArray()
