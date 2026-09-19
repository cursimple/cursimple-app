package com.x500x.cursimple.feature.widget

import android.os.Build
import android.widget.RemoteViews

/**
 * 给小组件里的列表塞行。
 *
 * API 31 起把行直接内联进 RemoteViews：启动器不用绑定 RemoteViewsService 就能画出列表。
 * 鸿蒙、EMUI 等自带启动器上「小组件加进去一片空白」多半就是那个适配器服务绑不上，
 * 内联这条路绕开了它。更早的系统没有这个接口，仍旧回退到适配器服务。
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
