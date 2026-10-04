package com.x500x.cursimple.app.util

import android.content.Context
import android.util.Log
import androidx.annotation.StringRes
import com.x500x.cursimple.R
import com.x500x.cursimple.core.plugin.logging.PluginLogSink
import com.x500x.cursimple.core.reminder.logging.ReminderLogSink

/**
 * 诊断日志按用途分几类，高级设置里可以只留自己在查的那几类。
 *
 * 类别从事件名的前缀认出来（「class_notice.deliver.failure」就是上课通知），
 * 不用改每一处打日志的地方。关掉的类别只丢 info，警告和错误照记：
 * 出了问题回头翻日志时不能是空的。
 */
enum class LogCategory(
    val key: String,
    @param:StringRes val labelRes: Int,
    private val prefixes: List<String>,
) {
    Notice("notice", R.string.log_category_notice, listOf("class_notice.", "alarm_pre_notice.", "notice.")),
    Reminder("reminder", R.string.log_category_reminder, listOf("reminder.", "alarm.")),
    Widget("widget", R.string.log_category_widget, listOf("widget.")),
    Plugin("plugin", R.string.log_category_plugin, emptyList()),
    General("general", R.string.log_category_general, emptyList()),
    ;

    companion object {
        /** 插件日志走自己那条 sink，不按前缀认；其余认不出的都算「其他」 */
        fun of(message: String): LogCategory =
            entries.firstOrNull { category -> category.prefixes.any(message::startsWith) } ?: General
    }
}

object LogCategories {
    private const val PREFS = "diagnostics_log_categories"
    private const val KEY_DISABLED = "disabled"

    /** 每条日志都要问一遍，读一次 SharedPreferences 后留在内存里 */
    @Volatile
    private var disabled: Set<String>? = null

    fun isEnabled(context: Context, category: LogCategory): Boolean =
        category.key !in disabled(context)

    fun setEnabled(context: Context, category: LogCategory, enabled: Boolean) {
        val next = if (enabled) disabled(context) - category.key else disabled(context) + category.key
        disabled = next
        prefs(context).edit().putStringSet(KEY_DISABLED, next).apply()
    }

    private fun disabled(context: Context): Set<String> =
        disabled ?: prefs(context).getStringSet(KEY_DISABLED, emptySet()).orEmpty().toSet()
            .also { disabled = it }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    internal fun shouldWrite(context: Context, priority: Int, category: LogCategory): Boolean =
        priority >= Log.WARN || isEnabled(context, category)
}

/** 应用和提醒日志共用的那份文件，写之前按类别过一遍。 */
class CategoryFilteredSink(
    context: Context,
    private val delegate: AppDiagnosticsFileSink,
) : AppDiagnosticsSink, ReminderLogSink {
    private val context = context.applicationContext

    override fun write(priority: Int, tag: String, message: String, throwableText: String?) {
        if (!LogCategories.shouldWrite(context, priority, LogCategory.of(message))) return
        delegate.write(priority, tag, message, throwableText)
    }
}

/** 插件日志整类开关。 */
class CategoryFilteredPluginSink(
    context: Context,
    private val delegate: PluginLogSink,
) : PluginLogSink {
    private val context = context.applicationContext

    override fun write(priority: Int, tag: String, message: String, throwableText: String?) {
        if (!LogCategories.shouldWrite(context, priority, LogCategory.Plugin)) return
        delegate.write(priority, tag, message, throwableText)
    }
}
