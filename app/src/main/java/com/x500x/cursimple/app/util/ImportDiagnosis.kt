package com.x500x.cursimple.app.util

import androidx.annotation.StringRes
import com.x500x.cursimple.R

enum class ImportSource { Ics, WakeUp, Ai }

/** Why an import failed in plain words, what to try next, and the raw message for anyone who needs it. */
data class ImportDiagnosis(
    @param:StringRes val reason: Int,
    @param:StringRes val hint: Int,
    val detail: String,
)

/**
 * Turn an importer's exception into something a student can act on. Importers report problems in their own words
 * (Chinese messages, JSON or network exceptions), so this matches on those and falls back to a general suggestion.
 */
fun diagnoseImportFailure(source: ImportSource, error: Throwable): ImportDiagnosis {
    val message = error.message.orEmpty()
    val text = (error.javaClass.simpleName + " " + message).lowercase()
    val detail = listOf(error.javaClass.simpleName, message).filter { it.isNotBlank() }.joinToString(": ")
    fun has(vararg keys: String) = keys.any { it.lowercase() in text }
    val tooBig = has("过大", "过多", "超限", "过深", "exceeds", "too large", "too many")
    return when {
        tooBig -> ImportDiagnosis(R.string.import_diag_too_big, R.string.import_diag_too_big_hint, detail)
        source == ImportSource.Ics -> when {
            has("没有可导入的定时课程", "全天") ->
                ImportDiagnosis(R.string.import_diag_ics_no_timed, R.string.import_diag_ics_no_timed_hint, detail)
            has("重复", "rrule", "exrule", "rdate", "byday", "interval", "count", "until", "wkst", "freq") ->
                ImportDiagnosis(R.string.import_diag_ics_recurrence, R.string.import_diag_ics_recurrence_hint, detail)
            has("跨午夜") -> ImportDiagnosis(R.string.import_diag_ics_midnight, R.string.import_diag_ics_midnight_hint, detail)
            has("vcalendar", "ics 事件", "ics 文件", "开始时间", "结束时间") ->
                ImportDiagnosis(R.string.import_diag_ics_broken, R.string.import_diag_ics_broken_hint, detail)
            else -> ImportDiagnosis(R.string.import_diag_unknown, R.string.import_diag_ics_unknown_hint, detail)
        }
        source == ImportSource.WakeUp -> when {
            has("节次时间", "课程时间", "课程基础") ->
                ImportDiagnosis(R.string.import_diag_wakeup_times, R.string.import_diag_wakeup_times_hint, detail)
            has("缺少", "无效", "wakeup", "json", "serializ", "unexpected") ->
                ImportDiagnosis(R.string.import_diag_wakeup_format, R.string.import_diag_wakeup_format_hint, detail)
            else -> ImportDiagnosis(R.string.import_diag_unknown, R.string.import_diag_wakeup_unknown_hint, detail)
        }
        else -> when {
            has("401", "403", "unauthorized", "invalid api key", "api key", "密钥") ->
                ImportDiagnosis(R.string.import_diag_ai_key, R.string.import_diag_ai_key_hint, detail)
            has("timeout", "timed out", "超时") ->
                ImportDiagnosis(R.string.import_diag_ai_timeout, R.string.import_diag_ai_timeout_hint, detail)
            has("unknownhost", "unable to resolve", "connect", "network", "网络") ->
                ImportDiagnosis(R.string.import_diag_ai_network, R.string.import_diag_ai_network_hint, detail)
            has("json", "解析", "parse", "unexpected", "没有识别") ->
                ImportDiagnosis(R.string.import_diag_ai_unreadable, R.string.import_diag_ai_unreadable_hint, detail)
            else -> ImportDiagnosis(R.string.import_diag_unknown, R.string.import_diag_ai_unknown_hint, detail)
        }
    }
}
