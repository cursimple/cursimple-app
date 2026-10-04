package com.x500x.cursimple.app.extension

import com.x500x.cursimple.core.data.widget.PendingTask
import com.x500x.cursimple.core.plugin.manifest.PluginFeedTypeSpec
import com.x500x.cursimple.feature.plugin.extension.ExtensionData
import com.x500x.cursimple.feature.plugin.extension.ExtensionLoginState
import com.x500x.cursimple.feature.plugin.extension.isNotice

/**
 * 一个组件里还没完成的任务，交给桌面「待完成」小组件。
 *
 * 判定与组件自己页面上的「待完成」一致：没做完、不是往期、组件声明为任务。
 * 登出或从未登录时不交；登录过期的仍交上次同步的结果，截止时间照样有用。
 */
internal fun pendingTasksOf(
    pluginId: String,
    sourceTitle: String,
    data: ExtensionData,
    feedTypes: List<PluginFeedTypeSpec>,
): List<PendingTask> {
    if (data.loginState == ExtensionLoginState.LoggedOut || data.loginState == ExtensionLoginState.Never) return emptyList()
    val types = feedTypes.associateBy { it.id }
    return data.items
        .filter { !it.done && !it.historical && !it.isNotice() }
        .map { item ->
            val type = types[item.type]
            PendingTask(
                id = "$pluginId/${item.id}",
                sourceId = pluginId,
                sourceTitle = sourceTitle,
                title = item.title.trim().ifBlank { type?.label ?: item.type },
                typeLabel = type?.label ?: item.category,
                group = item.course,
                dueAtMillis = item.dueAt,
                startAtMillis = item.startAt,
                colorArgb = ExtensionScheduleBridge.parseColor(type?.color),
            )
        }
}
