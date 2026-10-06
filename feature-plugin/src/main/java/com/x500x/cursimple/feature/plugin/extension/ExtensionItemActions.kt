package com.x500x.cursimple.feature.plugin.extension

import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull

fun ExtensionData.isIgnored(item: ExtensionFeedItem, now: Long): Boolean =
    item.id in ignoredItemIds || (host.ignoreOverdue && item.id !in restoredItemIds &&
        !item.done && !item.isNotice() && item.dueAt?.let { it <= now } == true)

fun ExtensionData.withItemIgnored(itemId: String, ignored: Boolean): ExtensionData {
    require(items.any { it.id == itemId }) { "内容已删除，请刷新列表" }
    return copy(
        ignoredItemIds = if (ignored) ignoredItemIds + itemId else ignoredItemIds - itemId,
        restoredItemIds = if (ignored) restoredItemIds - itemId else restoredItemIds + itemId,
    )
}

/** Accept only explicit component confirmation of official read status. */
internal fun confirmedReadResult(data: ExtensionData, itemId: String, run: ExtensionRunResult.Completed): ExtensionData {
    require((run.result["itemId"] as? JsonPrimitive)?.contentOrNull == itemId &&
        (run.result["done"] as? JsonPrimitive)?.booleanOrNull == true) { "官方未确认已读，请稍后重试" }
    require(data.items.any { it.id == itemId && it.isNotice() }) { "公告已删除，请刷新列表" }
    return data.copy(items = data.items.map { if (it.id == itemId) it.copy(done = true) else it })
}
