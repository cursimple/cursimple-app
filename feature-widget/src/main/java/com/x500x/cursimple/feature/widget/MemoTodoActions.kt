package com.x500x.cursimple.feature.widget

import android.app.PendingIntent
import android.app.ActivityOptions
import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.RemoteViews
import com.x500x.cursimple.core.data.memo.memoTodoRevision
import com.x500x.cursimple.core.reminder.logging.ReminderLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

private const val ACTION_COMPLETE = "com.x500x.cursimple.widget.COMPLETE_MEMO_TODO"
private const val EXTRA_NOTE = "memo_note_id"
private const val EXTRA_LINE = "memo_checklist_line"
private const val EXTRA_REVISION = "memo_body_revision"

internal fun RemoteViews.applyMemoTodoAdd(context: Context, widgetId: Int) {
    val create = Intent().setClassName(context.packageName, WidgetDeepLinks.MEMO_QUICK_EDIT_ACTIVITY).apply {
        data = Uri.Builder().scheme("cursimple-widget").authority("memo").appendPath(widgetId.toString()).appendPath("create").build()
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NO_ANIMATION)
        putExtra(WidgetDeepLinks.EXTRA_CREATE_MEMO, true)
    }
    val action = memoWindowPendingIntent(context, create)
    setOnClickPendingIntent(R.id.memo_add, action)
    setOnClickPendingIntent(R.id.memo_add_small, action)
}

/** Each task gets separate completion and Activity actions, including on older launchers. */
internal fun RemoteViews.applyMemoTodoActions(context: Context, row: MemoTodoRow, widgetId: Int) {
    val (complete, edit) = memoTodoRowIntents(context, row, widgetId)
    setOnClickPendingIntent(R.id.memo_checkbox, memoWindowPendingIntent(context, complete))
    val editAction = memoWindowPendingIntent(context, edit)
    setOnClickPendingIntent(R.id.memo_row_edit, editAction)
    setOnClickPendingIntent(R.id.memo_row_due, editAction)
    setOnClickPendingIntent(R.id.memo_row_root, editAction)
}

/** Scrolling list rows fill in the list template, which always targets the quick edit window. */
internal fun RemoteViews.applyMemoTodoFillIns(context: Context, row: MemoTodoRow, widgetId: Int) {
    val (complete, edit) = memoTodoRowIntents(context, row, widgetId)
    setOnClickFillInIntent(R.id.memo_checkbox, complete)
    setOnClickFillInIntent(R.id.memo_row_edit, edit)
    setOnClickFillInIntent(R.id.memo_row_due, edit)
    setOnClickFillInIntent(R.id.memo_row_root, edit)
}

internal fun RemoteViews.applyMemoTodoListTemplate(context: Context, listId: Int, widgetId: Int) {
    val template = Intent().setClassName(context.packageName, WidgetDeepLinks.MEMO_QUICK_EDIT_ACTIVITY)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NO_ANIMATION)
    setPendingIntentTemplate(listId, PendingIntent.getActivity(
        context, MEMO_LIST_TEMPLATE_REQUEST_BASE + widgetId, template,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
        ActivityOptions.makeCustomAnimation(context, 0, 0).toBundle(),
    ))
}

private const val MEMO_LIST_TEMPLATE_REQUEST_BASE = 29400

/** Completion confirmation and edit intents of one row; distinct data keeps their PendingIntents apart. */
private fun memoTodoRowIntents(context: Context, row: MemoTodoRow, widgetId: Int): Pair<Intent, Intent> {
    val revision = memoTodoRevision(row.note.body)
    fun uri(action: String): Uri = Uri.Builder().scheme("cursimple-widget").authority("memo")
        .appendPath(widgetId.toString()).appendPath(row.note.id)
        .appendPath((row.lineIndex ?: -1).toString()).appendPath(action).appendPath(revision).build()
    val complete = memoTodoConfirmationIntent(context, row.note.id, row.lineIndex ?: -1, revision, widgetId).apply {
        data = uri("complete")
    }
    val edit = Intent().setClassName(context.packageName, WidgetDeepLinks.MEMO_QUICK_EDIT_ACTIVITY).apply {
        data = uri("edit")
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NO_ANIMATION)
        putExtra(WidgetDeepLinks.EXTRA_OPEN_MEMO, row.note.id)
        putExtra(WidgetDeepLinks.EXTRA_MEMO_LINE, row.lineIndex ?: -1)
        putExtra(WidgetDeepLinks.EXTRA_MEMO_REVISION, revision)
    }
    return complete to edit
}

/** Keep launcher actions static; the widget supplies only pressed-state feedback. */
private fun memoWindowPendingIntent(context: Context, intent: Intent): PendingIntent = PendingIntent.getActivity(
    context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    ActivityOptions.makeCustomAnimation(context, 0, 0).toBundle(),
)

private fun memoTodoConfirmationIntent(context: Context, noteId: String, line: Int, revision: String, widgetId: Int) =
    Intent().setClassName(context.packageName, WidgetDeepLinks.MEMO_QUICK_EDIT_ACTIVITY).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NO_ANIMATION)
        putExtra(WidgetDeepLinks.EXTRA_CONFIRM_MEMO_COMPLETION, true)
        putExtra(WidgetDeepLinks.EXTRA_OPEN_MEMO, noteId)
        putExtra(WidgetDeepLinks.EXTRA_MEMO_LINE, line)
        putExtra(WidgetDeepLinks.EXTRA_MEMO_REVISION, revision)
        putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
    }

/** Old launcher PendingIntents request confirmation too; they never commit completion. */
class MemoTodoActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_COMPLETE) return
        val widgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        val app = context.applicationContext
        val manager = AppWidgetManager.getInstance(app)
        if (widgetId !in catalogWidgetIds(app, manager, MemoTodoWidgetReceiver.CATALOG_ID)) return
        val noteId = intent.getStringExtra(EXTRA_NOTE) ?: return
        val revision = intent.getStringExtra(EXTRA_REVISION) ?: return
        val line = intent.getIntExtra(EXTRA_LINE, -1)
        if (line < -1) return
        runCatching { context.startActivity(memoTodoConfirmationIntent(app, noteId, line, revision, widgetId)) }
            .onFailure { ReminderLogger.warn("widget.memo.confirm.failure", emptyMap(), it) }
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                MemoTodoWidgetReceiver.updateWidgets(app)
            } catch (error: Exception) {
                ReminderLogger.warn("widget.memo.refresh.failure", emptyMap(), error)
            } finally {
                pending.finish()
            }
        }
    }

}
