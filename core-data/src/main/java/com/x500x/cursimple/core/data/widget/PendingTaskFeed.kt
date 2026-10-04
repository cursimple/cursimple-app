package com.x500x.cursimple.core.data.widget

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File

/**
 * 交给桌面「待完成」小组件的一条任务。
 *
 * 只描述「来源、标题、时间」这些通用信息，不带任何组件自己的类型名：
 * 哪些条目算任务由组件在清单里声明，App 按声明挑出来写进这里，小组件只管显示。
 */
@Serializable
data class PendingTask(
    val id: String,
    /** 产出这条任务的组件，点开时进入它的页面 */
    val sourceId: String,
    /** 组件的显示名 */
    val sourceTitle: String,
    val title: String,
    /** 类型显示名，如组件清单里声明的 label；没有时为空 */
    val typeLabel: String = "",
    /** 所属课程或分组；没有时为空 */
    val group: String = "",
    val dueAtMillis: Long? = null,
    val startAtMillis: Long? = null,
    /** 组件声明的类型颜色（ARGB）；没有时用主题色 */
    val colorArgb: Long? = null,
)

/**
 * 待完成任务的快照文件。
 *
 * 由 App 在组件同步、改设置、移除后整份重写；小组件随时读取。
 * 用文件而不是 DataStore：只有一个写入方、整份覆盖，读的一方不需要监听。
 */
object PendingTaskFeed {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = false }
    private val serializer = ListSerializer(PendingTask.serializer())
    private val lock = Any()

    fun read(context: Context): List<PendingTask> = synchronized(lock) {
        val file = fileOf(context)
        if (!file.isFile) return emptyList()
        runCatching { json.decodeFromString(serializer, file.readText()) }.getOrDefault(emptyList())
    }

    /** 内容没变时返回 false，调用方可以省掉一次小组件刷新 */
    fun write(context: Context, tasks: List<PendingTask>): Boolean = synchronized(lock) {
        val file = fileOf(context)
        val text = json.encodeToString(serializer, tasks)
        if (file.isFile && runCatching { file.readText() }.getOrNull() == text) return false
        file.parentFile?.mkdirs()
        val temp = File(file.parentFile, file.name + ".tmp")
        temp.writeText(text)
        if (!temp.renameTo(file)) {
            file.writeText(text)
            temp.delete()
        }
        true
    }

    private fun fileOf(context: Context): File =
        File(context.applicationContext.filesDir, "widget/pending_tasks.json")
}

/** 任务下一个要紧的时刻：开始（如开考）还是截止 */
enum class PendingTaskMomentKind { Start, Due }

data class PendingTaskMoment(val kind: PendingTaskMomentKind, val atMillis: Long)

/**
 * 还没开始的先看开始时间，已经开始或没有开始时间的看截止时间；两个都没有返回 null。
 * 只有开始时间、且已经开始的，仍报开始时间（之后按已过处理）。
 */
fun PendingTask.nextMoment(nowMillis: Long): PendingTaskMoment? {
    startAtMillis?.takeIf { it > nowMillis }?.let { return PendingTaskMoment(PendingTaskMomentKind.Start, it) }
    dueAtMillis?.let { return PendingTaskMoment(PendingTaskMomentKind.Due, it) }
    return startAtMillis?.let { PendingTaskMoment(PendingTaskMomentKind.Start, it) }
}

/**
 * 小组件里的排列顺序：还没到点的按时间先后，没有时间的其次，已过的放最后（最近过的在前）。
 */
fun List<PendingTask>.sortedForWidget(nowMillis: Long): List<PendingTask> {
    fun at(task: PendingTask) = task.nextMoment(nowMillis)?.atMillis
    val upcoming = filter { at(it)?.let { time -> time >= nowMillis } == true }.sortedBy { at(it) }
    val undated = filter { at(it) == null }.sortedBy { it.title }
    val overdue = filter { at(it)?.let { time -> time < nowMillis } == true }.sortedByDescending { at(it) }
    return upcoming + undated + overdue
}
