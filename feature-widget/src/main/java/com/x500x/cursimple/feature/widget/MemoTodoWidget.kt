package com.x500x.cursimple.feature.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.FrameLayout
import kotlinx.coroutines.withContext
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import com.x500x.cursimple.core.data.DataStoreUserPreferencesRepository
import com.x500x.cursimple.core.data.memo.DataStoreMemoRepository
import com.x500x.cursimple.core.data.widget.DataStoreWidgetPreferencesRepository
import com.x500x.cursimple.core.data.widget.WidgetThemePreferences
import com.x500x.cursimple.core.data.widget.resolveAccent
import com.x500x.cursimple.core.kernel.model.MemoNote
import com.x500x.cursimple.core.kernel.model.memoComparator
import com.x500x.cursimple.core.kernel.model.parseChecklistLine
import com.x500x.cursimple.core.kernel.time.BeijingTime
import com.x500x.cursimple.core.reminder.logging.ReminderLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
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
        } else listOf(MemoTodoRow(note, null, note.title.ifBlank { note.body.lineSequence().firstOrNull { it.isNotBlank() }.orEmpty().trim() }))
    }

private data class MemoTodoData(val rows: List<MemoTodoRow>, val theme: WidgetThemePreferences, val now: LocalDateTime)

private suspend fun loadMemoTodos(context: Context): MemoTodoData {
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
                val views = RemoteViews(app.packageName, R.layout.widget_memo)
                views.applyWidgetBackground(app, R.id.memo_root, data.theme)
                views.applyOpenAppClick(app, R.id.memo_root, id, data.theme, Intent().putExtra(WidgetDeepLinks.EXTRA_OPEN_MEMO, ""))
                views.setTextViewText(R.id.memo_title, app.getString(R.string.widget_label_memo))
                val compact = manager.getAppWidgetOptions(id).getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 148) < 160
                val accent = widgetAccentTextColor(data.theme)
                views.setInt(R.id.memo_icon, "setColorFilter", accent)
                views.setInt(R.id.memo_empty_icon, "setColorFilter", accent)
                views.setTextColor(R.id.memo_count, accent)
                views.setTextViewText(R.id.memo_count, app.getString(R.string.widget_memo_count, data.rows.size))
                views.setTextViewText(R.id.memo_subtitle, app.getString(R.string.widget_memo_subtitle))
                views.setViewVisibility(R.id.memo_subtitle, if (compact) View.GONE else View.VISIBLE)
                views.setViewVisibility(R.id.memo_header_rule, if (compact) View.GONE else View.VISIBLE)
                views.setInt(R.id.memo_header_rule, "setBackgroundColor", (accent and 0x00FFFFFF) or 0x26000000)
                val layout = withContext(Dispatchers.Main) { memoWidgetLayout(app, manager, id, data, views, compact) }
                val visible = data.rows.take(layout.rows)
                views.setWidgetRows(R.id.memo_list, visible, { it.stableId }, { memoTodoView(app, it, data, layout) }) {
                    views.setRemoteAdapter(R.id.memo_list, Intent(app, MemoTodoRemoteViewsService::class.java).apply {
                        putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
                        putExtra(EXTRA_WIDGET_LIST_REVISION, widgetListRevision(data.rows.size, visible))
                        this.data = Uri.parse(toUri(Intent.URI_INTENT_SCHEME))
                    })
                }
                views.applyOpenAppListTemplate(app, R.id.memo_list, id, data.theme)
                views.setEmptyView(R.id.memo_list, R.id.memo_empty)
                views.setTextViewText(R.id.memo_empty_text, app.getString(R.string.widget_memo_empty))
                views.applyOpenAppClick(app, R.id.memo_empty, id, data.theme, Intent().putExtra(WidgetDeepLinks.EXTRA_OPEN_MEMO, ""))
                views.setViewVisibility(R.id.memo_list, if (visible.isEmpty()) View.GONE else View.VISIBLE)
                views.setViewVisibility(R.id.memo_empty, if (data.rows.isEmpty()) View.VISIBLE else View.GONE)
                manager.updateAppWidget(id, views)
                manager.notifyAppWidgetViewDataChanged(intArrayOf(id), R.id.memo_list)
            }
        }
    }
}

class MemoTodoWidgetReceiverMIUI : MemoTodoWidgetReceiver()

private data class MemoWidgetLayout(val rows: Int, val rowHeightDp: Int, val compact: Boolean)

/** Widget options already exclude launcher padding. Measure fonts before choosing rows. */
private fun memoWidgetLayout(context: Context, manager: AppWidgetManager, id: Int,
    data: MemoTodoData, header: RemoteViews, compact: Boolean): MemoWidgetLayout {
    val options = manager.getAppWidgetOptions(id)
    val density = context.resources.displayMetrics.density
    val width = (options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 330) * density).toInt()
    val height = (options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 148) * density).toInt()
    val parent = FrameLayout(context)
    val root = header.apply(context, parent)
    root.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
        View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
    val available = height - root.measuredHeight
    fun fit(prototype: MemoWidgetLayout): MemoWidgetLayout {
        var remaining = available
        var count = 0
        for (row in data.rows.take(8)) {
            val item = memoTodoView(context, row, data, prototype).apply(context, parent)
            item.measure(View.MeasureSpec.makeMeasureSpec((width - 24 * density).toInt().coerceAtLeast(1), View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
            if (item.measuredHeight > remaining) break
            remaining -= item.measuredHeight
            count++
        }
        return prototype.copy(rows = count)
    }
    val detailed = fit(MemoWidgetLayout(0, if (compact) 36 else 64, compact))
    if (!compact || detailed.rows >= minOf(2, data.rows.size)) return detailed
    val shortHeight = (available / density / 2).toInt().coerceIn(24, 35)
    val shorter = fit(MemoWidgetLayout(0, shortHeight, true))
    return if (shorter.rows > detailed.rows) shorter else detailed
}

private fun memoTodoView(context: Context, row: MemoTodoRow, data: MemoTodoData, layout: MemoWidgetLayout): RemoteViews =
    RemoteViews(context.packageName, R.layout.widget_memo_row).apply {
        val accent = widgetAccentTextColor(data.theme)
        setInt(R.id.memo_row_root, "setMinimumHeight", (layout.rowHeightDp * context.resources.displayMetrics.density).toInt())
        val padding = ((if (layout.compact) 2 else 5) * context.resources.displayMetrics.density).toInt()
        setViewPadding(R.id.memo_row_content, 0, padding, 0, padding)
        setInt(R.id.memo_checkbox, "setColorFilter", accent)
        setInt(R.id.memo_row_rule, "setBackgroundColor", (accent and 0x00FFFFFF) or 0x18000000)
        setTextViewText(R.id.memo_row_title, row.title)
        setInt(R.id.memo_row_title, "setMaxLines", if (layout.compact) 1 else 2)
        val metadata = listOf(row.note.courseTitle, row.note.title.takeIf { row.lineIndex != null }.orEmpty())
            .filter(String::isNotBlank).joinToString(" · ").ifBlank { context.getString(R.string.widget_memo_notebook_other) }
        setTextViewText(R.id.memo_row_source, metadata)
        setViewVisibility(R.id.memo_row_source, if (layout.rowHeightDp < 36) View.GONE else View.VISIBLE)
        val due = row.note.dueDateTime
        val overdue = due?.isBefore(data.now) == true
        val dueText = when {
            due == null -> ""
            overdue -> context.getString(R.string.widget_memo_overdue) + "\n" + due.format(DateTimeFormatter.ofPattern("M/d HH:mm"))
            else -> due.format(DateTimeFormatter.ofPattern("M/d\nHH:mm"))
        }
        setViewVisibility(R.id.memo_row_due, if (due == null || layout.rowHeightDp < 36) View.GONE else View.VISIBLE)
        setTextViewText(R.id.memo_row_due, dueText)
        setTextColor(R.id.memo_row_due, if (overdue) 0xFFB3261E.toInt() else accent)
        setContentDescription(R.id.memo_row_root, listOf(row.title, metadata, dueText).filter(String::isNotBlank).joinToString(" · "))
        if (data.theme.openAppOnDoubleClickEnabled) setOnClickFillInIntent(R.id.memo_row_root,
            Intent().putExtra(WidgetDeepLinks.EXTRA_OPEN_MEMO, row.note.id))
    }

class MemoTodoRemoteViewsService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory = MemoTodoFactory(applicationContext,
        intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID))
}

private class MemoTodoFactory(private val app: Context, private val id: Int) : RemoteViewsService.RemoteViewsFactory {
    private var context = app.widgetLocaleContext()
    private var data: MemoTodoData? = null
    private var rows = emptyList<MemoTodoRow>()
    private var layout = MemoWidgetLayout(1, 36, true)
    override fun onCreate() = Unit
    override fun onDataSetChanged() {
        context = app.widgetLocaleContext()
        runCatching { runBlocking(Dispatchers.IO) { loadMemoTodos(context) } }.onSuccess {
            data = it
            val manager = AppWidgetManager.getInstance(context)
            val compact = manager.getAppWidgetOptions(id).getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 148) < 160
            val header = RemoteViews(context.packageName, R.layout.widget_memo).apply {
                setViewVisibility(R.id.memo_subtitle, if (compact) View.GONE else View.VISIBLE)
                setViewVisibility(R.id.memo_header_rule, if (compact) View.GONE else View.VISIBLE)
                setTextViewText(R.id.memo_count, context.getString(R.string.widget_memo_count, it.rows.size))
            }
            layout = runBlocking(Dispatchers.Main) { memoWidgetLayout(context, manager, id, it, header, compact) }
            rows = it.rows.take(layout.rows)
        }
    }
    override fun onDestroy() { rows = emptyList(); data = null }
    override fun getCount(): Int = rows.size
    override fun getViewAt(position: Int): RemoteViews = rows.getOrNull(position)?.let { row -> data?.let { memoTodoView(context, row, it, layout) } }
        ?: RemoteViews(context.packageName, R.layout.widget_memo_row)
    override fun getLoadingView(): RemoteViews? = null
    override fun getViewTypeCount(): Int = 1
    override fun getItemId(position: Int): Long = rows.getOrNull(position)?.stableId ?: position.toLong()
    override fun hasStableIds(): Boolean = true
}
