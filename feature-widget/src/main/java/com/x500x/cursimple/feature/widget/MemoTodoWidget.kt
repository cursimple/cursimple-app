package com.x500x.cursimple.feature.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.FrameLayout
import kotlinx.coroutines.withContext
import android.widget.RemoteViews
import com.x500x.cursimple.core.data.DataStoreUserPreferencesRepository
import com.x500x.cursimple.core.data.memo.DataStoreMemoRepository
import com.x500x.cursimple.core.data.widget.DataStoreWidgetPreferencesRepository
import com.x500x.cursimple.core.data.widget.WidgetThemePreferences
import com.x500x.cursimple.core.data.widget.resolveAccent
import com.x500x.cursimple.core.kernel.model.MemoNote
import com.x500x.cursimple.core.kernel.model.MemoImageRef
import com.x500x.cursimple.core.kernel.model.MemoPriority
import com.x500x.cursimple.core.kernel.model.memoComparator
import com.x500x.cursimple.core.kernel.model.parseChecklistLine
import com.x500x.cursimple.core.kernel.time.BeijingTime
import com.x500x.cursimple.core.reminder.logging.ReminderLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

internal data class MemoTodoRow(val note: MemoNote, val lineIndex: Int?, val title: String) {
    val stableId: Long get() = "${note.id}/${lineIndex ?: -1}".hashCode().toLong()
}

internal fun memoTodoRows(notes: List<MemoNote>, now: LocalDateTime): List<MemoTodoRow> =
    notes.filter { !it.completed && !it.isBlank }.sortedWith(memoComparator(now)).flatMap { note ->
        val checklist = note.body.lines().mapIndexedNotNull { index, line ->
            parseChecklistLine(line)?.let { index to it }
        }
        if (checklist.isNotEmpty()) checklist.filter { !it.second.first }.map { (index, item) ->
            MemoTodoRow(note, index, item.second.trim().ifBlank { note.title })
        } else listOf(MemoTodoRow(note, null, note.title.ifBlank {
            val first = note.body.lineSequence().firstOrNull { it.isNotBlank() }.orEmpty().trim()
            MemoImageRef.parse(first)?.alt?.ifBlank { "Image" } ?: first
        }))
    }

internal data class MemoTodoData(val rows: List<MemoTodoRow>, val theme: WidgetThemePreferences, val now: LocalDateTime)

internal suspend fun loadMemoTodos(context: Context): MemoTodoData {
    val prefs = DataStoreUserPreferencesRepository(context).preferencesFlow.first()
    val theme = DataStoreWidgetPreferencesRepository(context).themePreferencesFlow.first()
        .resolveAccent(prefs.themeAccent, prefs.themeCustomColorArgb)
    BeijingTime.setForcedNow(prefs.debugForcedDateTime)
    val now = BeijingTime.nowDateTimeIn(BeijingTime.zone)
    return MemoTodoData(memoTodoRows(DataStoreMemoRepository(context).notesFlow.first(), now), theme, now)
}

open class MemoTodoWidgetReceiver : AppWidgetProvider() {
    override fun onReceive(context: Context, intent: Intent) {
        if (handleVendorWidgetUpdate(context, intent) { updateWidgets(it) }) return
        super.onReceive(context, intent)
    }
    override fun onEnabled(context: Context) { super.onEnabled(context); refresh(context) }
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) = refresh(context, ids)
    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, id: Int, options: Bundle) {
        super.onAppWidgetOptionsChanged(context, manager, id, options)
        refresh(context, intArrayOf(id))
    }
    override fun onDeleted(context: Context, ids: IntArray) {
        super.onDeleted(context, ids)
        WidgetLifecycleRefresher.onWidgetSetChanged(context.applicationContext, reason = "memo_widget_deleted")
    }
    private fun refresh(context: Context, ids: IntArray? = null) {
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            try { updateWidgets(context.applicationContext, ids) } finally { pending.finish() }
        }
    }
    companion object {
        internal const val CATALOG_ID = "memo"

        @Suppress("DEPRECATION")
        suspend fun updateWidgets(context: Context, ids: IntArray? = null) {
            val app = context.widgetLocaleContext()
            val manager = AppWidgetManager.getInstance(app)
            val widgetIds = ids ?: catalogWidgetIds(app, manager, CATALOG_ID)
            if (widgetIds.isEmpty()) return
            val data = runCatching { loadMemoTodos(app) }
                .onFailure { ReminderLogger.warn("widget.memo.load.failure", emptyMap(), it) }.getOrNull() ?: return
            widgetIds.forEach { id ->
                val views = RemoteViews(app.packageName, R.layout.widget_memo_surface)
                views.applyWidgetBackground(app, data.theme)
                views.applyOpenAppClick(app, R.id.memo_root, id, data.theme, Intent().putExtra(WidgetDeepLinks.EXTRA_OPEN_MEMO, ""))
                views.setTextViewText(R.id.memo_title, app.getString(R.string.widget_label_memo))
                val compact = manager.getAppWidgetOptions(id).getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 148) < 160
                if (compact && manager.getAppWidgetOptions(id).getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 330) < 240) {
                    views.setTextViewText(R.id.memo_title, app.getString(R.string.widget_memo_pending))
                }
                val minimal = manager.getAppWidgetOptions(id).getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 148) < 128
                val density = app.resources.displayMetrics.density
                val padding = ((if (minimal) 6 else 8) * density).toInt()
                val endPadding = if (minimal) (64 * density).toInt() else padding
                views.setViewPadding(R.id.memo_content, padding, padding, endPadding, padding)
                views.setInt(R.id.memo_content, "setGravity", if (minimal) android.view.Gravity.CENTER_VERTICAL else android.view.Gravity.TOP)
                views.setViewVisibility(R.id.memo_header, if (minimal) View.GONE else View.VISIBLE)
                views.setViewVisibility(R.id.memo_add_small, if (minimal) View.VISIBLE else View.GONE)
                views.applyMemoTodoAdd(app, id)
                val accent = widgetAccentTextColor(data.theme, resolveWidgetIsDark(context, data.theme.widgetThemeMode))
                views.setInt(R.id.memo_icon, "setColorFilter", accent)
                views.setInt(R.id.memo_add, "setColorFilter", accent)
                views.setInt(R.id.memo_add_small, "setColorFilter", accent)
                views.setInt(R.id.memo_empty_icon, "setColorFilter", accent)
                views.setTextColor(R.id.memo_count, accent)
                views.setTextViewText(R.id.memo_count, app.getString(R.string.widget_memo_count, data.rows.size))
                views.setTextViewText(R.id.memo_subtitle, app.getString(R.string.widget_memo_subtitle))
                views.setViewVisibility(R.id.memo_subtitle, if (compact) View.GONE else View.VISIBLE)
                views.setViewVisibility(R.id.memo_header_rule, if (compact) View.GONE else View.VISIBLE)
                views.setInt(R.id.memo_header_rule, "setBackgroundColor", (accent and 0x00FFFFFF) or 0x26000000)
                val layout = withContext(Dispatchers.Main) { memoWidgetLayout(app, manager, id, data, views, compact) }
                // Fitted rows can end exactly at the bottom edge; say that more tasks wait below.
                // The launcher keeps the scroll position, so the hint names no count.
                if (data.rows.size > layout.rows && layout.rows < MEMO_WIDGET_FIT_PROBE_ROWS) {
                    views.setTextViewText(R.id.memo_subtitle, app.getString(R.string.widget_memo_scroll_more))
                }
                views.removeAllViews(R.id.memo_list)
                // Load rows on demand, avoiding Binder payload limits and arbitrary task truncation.
                val adapterIntent = Intent(app, MemoTodoRemoteViewsService::class.java).apply {
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
                    putExtra("rowHeightDp", layout.rowHeightDp)
                    putExtra("compact", layout.compact)
                    putExtra("minimal", layout.minimal)
                    this.data = android.net.Uri.parse("cursimple://memo-widget/$id/${layout.rowHeightDp}/${layout.compact}/${layout.minimal}")
                }
                views.setRemoteAdapter(R.id.memo_scroll_list, adapterIntent)
                views.applyMemoTodoListTemplate(app, R.id.memo_scroll_list, id)
                views.setTextViewText(R.id.memo_empty_text, app.getString(if (minimal) R.string.widget_memo_empty_compact else R.string.widget_memo_empty))
                views.setViewVisibility(R.id.memo_empty_icon, if (minimal) View.GONE else View.VISIBLE)
                views.setInt(R.id.memo_empty_text, "setMaxLines", if (minimal) 2 else 4)
                views.applyOpenAppClick(app, R.id.memo_empty, id, data.theme, Intent().putExtra(WidgetDeepLinks.EXTRA_OPEN_MEMO, ""))
                views.setViewVisibility(R.id.memo_list, View.GONE)
                views.setViewVisibility(R.id.memo_scroll_list, if (data.rows.isNotEmpty()) View.VISIBLE else View.GONE)
                views.setViewVisibility(R.id.memo_empty, if (data.rows.isEmpty()) View.VISIBLE else View.GONE)
                manager.updateAppWidget(id, views)
                manager.notifyAppWidgetViewDataChanged(id, R.id.memo_scroll_list)
            }
        }
    }
}

class MemoTodoWidgetReceiverMIUI : MemoTodoWidgetReceiver()

/** Rows measured when fitting; more than this many visible rows are never counted. */
private const val MEMO_WIDGET_FIT_PROBE_ROWS = 8

internal data class MemoWidgetLayout(val rows: Int, val rowHeightDp: Int, val compact: Boolean, val minimal: Boolean)

/** Widget options already exclude launcher padding. Measure fonts before choosing rows. */
private fun memoWidgetLayout(context: Context, manager: AppWidgetManager, id: Int,
    data: MemoTodoData, header: RemoteViews, compact: Boolean): MemoWidgetLayout {
    val options = manager.getAppWidgetOptions(id)
    val density = context.resources.displayMetrics.density
    val width = (options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 330) * density).toInt()
    val height = (options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 148) * density).toInt()
    val parent = FrameLayout(context)
    val root = header.apply(context, parent)
    // The compact add action overlays the task area; it is not part of the header height.
    root.findViewById<View>(R.id.memo_add_small)?.visibility = View.GONE
    root.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
        View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
    val available = height - root.measuredHeight
    val minimal = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 148) < 128
    fun fit(prototype: MemoWidgetLayout): MemoWidgetLayout {
        var remaining = available
        var count = 0
        for (row in data.rows.take(MEMO_WIDGET_FIT_PROBE_ROWS)) {
            val item = memoTodoView(context, row, data, prototype).apply(context, parent)
            val horizontalPadding = if (minimal) 70 else 16
            item.measure(View.MeasureSpec.makeMeasureSpec((width - horizontalPadding * density).toInt().coerceAtLeast(1), View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
            if (item.measuredHeight > remaining) break
            remaining -= item.measuredHeight
            count++
        }
        return prototype.copy(rows = count)
    }
    val detailed = fit(MemoWidgetLayout(0, if (compact) 48 else 64, compact, minimal))
    if (compact || detailed.rows >= minOf(2, data.rows.size)) return detailed
    val shorter = fit(detailed.copy(rowHeightDp = 58))
    return if (shorter.rows > detailed.rows) shorter else detailed
}

internal fun memoTodoView(
    context: Context,
    row: MemoTodoRow,
    data: MemoTodoData,
    layout: MemoWidgetLayout,
    widgetId: Int? = null,
    fillIn: Boolean = false,
): RemoteViews =
    RemoteViews(context.packageName, R.layout.widget_memo_row).apply {
        applyWidgetTextColors(context, data.theme)
        val accent = widgetAccentTextColor(data.theme, resolveWidgetIsDark(context, data.theme.widgetThemeMode))
        setInt(R.id.memo_row_root, "setMinimumHeight", (layout.rowHeightDp * context.resources.displayMetrics.density).toInt())
        val padding = ((if (layout.compact) 0 else 5) * context.resources.displayMetrics.density).toInt()
        setViewPadding(R.id.memo_row_content, 0, padding, 0, padding)
        val priorityColor = memoWidgetPriorityColor(row.note.priority, accent)
        setInt(R.id.memo_checkbox, "setColorFilter", accent)
        setViewVisibility(R.id.memo_row_priority_stripe, if (priorityColor == null) View.GONE else View.VISIBLE)
        if (priorityColor != null) setInt(R.id.memo_row_priority_stripe, "setColorFilter", priorityColor)
        val badge = when (row.note.priority) {
            MemoPriority.High -> R.string.widget_memo_priority_badge_high
            MemoPriority.Medium -> R.string.widget_memo_priority_badge_medium
            MemoPriority.Low -> R.string.widget_memo_priority_badge_low
            MemoPriority.None -> null
        }
        setViewVisibility(R.id.memo_row_priority, if (badge == null || layout.minimal) View.GONE else View.VISIBLE)
        if (badge != null) {
            setTextViewText(R.id.memo_row_priority, context.getString(badge))
            setTextColor(R.id.memo_row_priority, priorityColor ?: accent)
        }
        setInt(R.id.memo_row_rule, "setBackgroundColor", (accent and 0x00FFFFFF) or 0x18000000)
        setTextViewText(R.id.memo_row_title, row.title)
        setInt(R.id.memo_row_title, "setMaxLines", if (layout.compact) 1 else 2)
        setViewVisibility(R.id.memo_row_pin, if (row.note.pinned && !layout.minimal) View.VISIBLE else View.GONE)
        setInt(R.id.memo_row_pin, "setColorFilter", accent)
        val metadata = listOf(row.note.courseTitle, row.note.title.takeIf { row.lineIndex != null }.orEmpty())
            .filter(String::isNotBlank).joinToString(" · ").ifBlank { context.getString(R.string.widget_memo_notebook_other) }
        setTextViewText(R.id.memo_row_source, metadata)
        val showSource = !layout.minimal && layout.rowHeightDp >= 36
        setViewVisibility(R.id.memo_row_source_row, if (showSource) View.VISIBLE else View.GONE)
        // Notebook color dot follows the source line, or the title when compact rows hide it.
        val notebookColor = memoWidgetNotebookColor(row.note).takeUnless { layout.minimal }
        setViewVisibility(R.id.memo_row_dot, if (notebookColor != null && showSource) View.VISIBLE else View.GONE)
        setViewVisibility(R.id.memo_row_dot_inline, if (notebookColor != null && !showSource) View.VISIBLE else View.GONE)
        notebookColor?.let {
            setInt(R.id.memo_row_dot, "setColorFilter", it)
            setInt(R.id.memo_row_dot_inline, "setColorFilter", it)
        }
        val due = row.note.dueDateTime
        val overdue = due?.isBefore(data.now) == true
        val dueText = when {
            due == null -> ""
            overdue -> context.getString(R.string.widget_memo_overdue) + "\n" + due.format(DateTimeFormatter.ofPattern("M/d HH:mm"))
            else -> due.format(DateTimeFormatter.ofPattern("M/d\nHH:mm"))
        }
        setViewVisibility(R.id.memo_row_due, if (due == null || layout.rowHeightDp < 36 || layout.minimal) View.GONE else View.VISIBLE)
        setTextViewText(R.id.memo_row_due, dueText)
        setTextColor(R.id.memo_row_due, if (overdue) 0xFFB3261E.toInt() else accent)
        val flags = listOfNotNull(
            context.getString(R.string.widget_memo_pinned).takeIf { row.note.pinned },
            memoWidgetPriorityLabel(context, row.note.priority),
        )
        setContentDescription(R.id.memo_row_root, (listOf(row.title) + flags + listOf(metadata, dueText))
            .filter(String::isNotBlank).joinToString(" · "))
        setContentDescription(R.id.memo_checkbox, context.getString(R.string.widget_memo_complete_action, row.title))
        setContentDescription(R.id.memo_row_edit, context.getString(R.string.widget_memo_edit_action, row.title))
        widgetId?.let { id ->
            if (fillIn) applyMemoTodoFillIns(context, row, id) else applyMemoTodoActions(context, row, id)
        }
    }

private const val MEMO_PRIORITY_HIGH = 0xFFBA1A1A.toInt()
private const val MEMO_PRIORITY_MEDIUM = 0xFFE39A2D.toInt()

/** Match light note-tag colors; low priority follows the user's widget accent. */
internal fun memoWidgetPriorityColor(priority: MemoPriority, accent: Int): Int? = when (priority) {
    MemoPriority.High -> MEMO_PRIORITY_HIGH
    MemoPriority.Medium -> MEMO_PRIORITY_MEDIUM
    MemoPriority.Low -> accent
    MemoPriority.None -> null
}

/** Same course palette and title seed as the in-app notebook tag; general notes have no color. */
internal fun memoWidgetNotebookColor(note: MemoNote): Int? {
    val key = note.courseKey ?: return null
    val seed = note.courseTitle.trim().ifBlank { key }
    return CalendarWidgetRenderer.coursePalette(seed).second
}

private fun memoWidgetPriorityLabel(context: Context, priority: MemoPriority): String? = when (priority) {
    MemoPriority.High -> context.getString(R.string.widget_memo_priority_high)
    MemoPriority.Medium -> context.getString(R.string.widget_memo_priority_medium)
    MemoPriority.Low -> context.getString(R.string.widget_memo_priority_low)
    MemoPriority.None -> null
}
