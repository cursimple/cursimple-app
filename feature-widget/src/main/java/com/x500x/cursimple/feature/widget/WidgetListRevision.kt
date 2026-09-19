package com.x500x.cursimple.feature.widget

/**
 * 列表适配器的内容版本号。
 *
 * 启动器按 Intent 的 data URI 认适配器，URI 一直不变时不少启动器（鸿蒙、EMUI、MIUI 上尤其明显）
 * 会继续沿用上一份缓存好的行，于是头部已经换了日期、列表还停在上一天，
 * 放假那天的灰字也跟着留在翻过去的日子上。把内容摘要写进 URI，内容一变适配器就重建。
 */
internal fun widgetListRevision(vararg parts: Any?): String =
    parts.joinToString(separator = "|") { it?.toString().orEmpty() }
        .hashCode()
        .toUInt()
        .toString(16)

/** 列表 Intent 上带内容版本的额外字段名。 */
internal const val EXTRA_WIDGET_LIST_REVISION = "widget_list_revision"
