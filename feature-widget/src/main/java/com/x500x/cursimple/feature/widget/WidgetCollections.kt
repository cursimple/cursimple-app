package com.x500x.cursimple.feature.widget

import android.os.Build
import android.widget.RemoteViews

/**
 * Inline RemoteViews rows on API 31+ avoid launcher adapter-service failures; older systems
 * retain services.
 */
internal fun <T> RemoteViews.setWidgetRows(
    listId: Int,
    rows: List<T>,
    stableId: (T) -> Long,
    buildRow: (T) -> RemoteViews,
    fallbackAdapter: () -> Unit,
) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
        fallbackAdapter()
        return
    }
    val items = RemoteViews.RemoteCollectionItems.Builder()
        .setHasStableIds(true)
        .setViewTypeCount(1)
    rows.forEach { row -> items.addItem(stableId(row), buildRow(row)) }
    setRemoteAdapter(listId, items.build())
}
