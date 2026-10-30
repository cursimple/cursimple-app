package com.x500x.cursimple.app.util

import com.x500x.cursimple.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ImportDiagnosisTest {
    private fun ics(message: String) = diagnoseImportFailure(ImportSource.Ics, IllegalArgumentException(message))
    private fun wake(message: String) = diagnoseImportFailure(ImportSource.WakeUp, IllegalArgumentException(message))
    private fun ai(error: Throwable) = diagnoseImportFailure(ImportSource.Ai, error)

    @Test
    fun anIcsWithoutTimedEventsIsExplainedAndOffersAnotherRoute() {
        assertEquals(R.string.import_diag_ics_no_timed, ics("ICS 文件中没有可导入的定时课程").reason)
        assertEquals(R.string.import_diag_ics_no_timed, ics("暂不支持全天 ICS 事件，请导入含上课时间的课程").reason)
    }

    @Test
    fun unsupportedRepeatRulesAreNamed() {
        assertEquals(R.string.import_diag_ics_recurrence, ics("暂不支持此 ICS 重复频率：MONTHLY").reason)
        assertEquals(R.string.import_diag_ics_recurrence, ics("暂不支持此 ICS 例外规则：RDATE").reason)
        assertEquals(R.string.import_diag_ics_recurrence, ics("不支持的 ICS BYDAY：1MO").reason)
    }

    @Test
    fun crossMidnightAndBrokenFilesGetTheirOwnReasons() {
        assertEquals(R.string.import_diag_ics_midnight, ics("暂不支持跨午夜的 ICS 课程").reason)
        assertEquals(R.string.import_diag_ics_broken, ics("ICS 事件未结束").reason)
    }

    @Test
    fun hugeInputIsReportedAsTooBigForEverySource() {
        assertEquals(R.string.import_diag_too_big, ics("导入文件过大").reason)
        assertEquals(R.string.import_diag_too_big, wake("课程数量过多").reason)
        assertEquals(R.string.import_diag_too_big, ai(IllegalStateException("Parser output exceeds import limit")).reason)
    }

    @Test
    fun wakeUpProblemsSeparateWrongFileFromMissingTimes() {
        assertEquals(R.string.import_diag_wakeup_format, wake("无效的 WakeUp 多行课程包").reason)
        assertEquals(R.string.import_diag_wakeup_format, wake("课程包缺少 JSON 数据").reason)
        assertEquals(R.string.import_diag_wakeup_times, wake("无效的节次时间").reason)
    }

    @Test
    fun aiFailuresPointAtKeyTimeoutNetworkOrAnswer() {
        assertEquals(R.string.import_diag_ai_key, ai(RuntimeException("HTTP 401 Unauthorized")).reason)
        assertEquals(R.string.import_diag_ai_timeout, ai(java.net.SocketTimeoutException("timeout")).reason)
        assertEquals(R.string.import_diag_ai_network, ai(java.net.UnknownHostException("api.example.com")).reason)
        assertEquals(R.string.import_diag_ai_unreadable, ai(RuntimeException("无法解析 JSON")).reason)
    }

    @Test
    fun anythingElseFallsBackToAGeneralHintAndKeepsTheRawMessage() {
        val diagnosis = ics("something odd")
        assertEquals(R.string.import_diag_unknown, diagnosis.reason)
        assertTrue(diagnosis.detail.contains("something odd"))
    }
}
