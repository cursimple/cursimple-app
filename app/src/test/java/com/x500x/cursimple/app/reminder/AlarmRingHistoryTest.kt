package com.x500x.cursimple.app.reminder

import org.junit.Assert.assertEquals
import org.junit.Test

class AlarmRingHistoryTest {

    @Test
    fun `编码解码往返后记录不变`() {
        val events = listOf(
            AlarmRingEvent(atMillis = 1_700_000_000_000L, outcome = AlarmRingOutcome.Rang, label = "高等数学"),
            AlarmRingEvent(atMillis = 1_600_000_000_000L, outcome = AlarmRingOutcome.Missed, label = "大学英语"),
        )
        assertEquals(events, AlarmRingHistory.decode(AlarmRingHistory.encode(events)))
    }

    @Test
    fun `课程名里的分隔符不会把记录切坏`() {
        val label = "英语\u001F听力\n实践"
        val decoded = AlarmRingHistory.decode(
            AlarmRingHistory.encode(
                listOf(AlarmRingEvent(atMillis = 1L, outcome = AlarmRingOutcome.Rang, label = label)),
            ),
        )
        assertEquals(1, decoded.size)
        assertEquals("英语 听力 实践", decoded.single().label)
    }

    @Test
    fun `认不出来的行丢掉而不是整段读崩`() {
        val good = AlarmRingHistory.encode(
            listOf(AlarmRingEvent(atMillis = 5L, outcome = AlarmRingOutcome.Rang, label = "体育")),
        )
        val decoded = AlarmRingHistory.decode("坏行\n$good\n字段不够的坏行")
        assertEquals(1, decoded.size)
        assertEquals(5L, decoded.single().atMillis)
    }

    @Test
    fun `空记录读出空列表`() {
        assertEquals(emptyList<AlarmRingEvent>(), AlarmRingHistory.decode(null))
        assertEquals(emptyList<AlarmRingEvent>(), AlarmRingHistory.decode(""))
    }
}
