package com.x500x.cursimple.core.reminder.dispatch

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AlarmScheduleDecisionTest {
    @Test
    fun `alarm clock stays primary and backup joins when exact scheduling is allowed`() {
        val decision = alarmScheduleDecision(canScheduleExact = true)

        assertEquals(AlarmPrimaryChannel.AlarmClock, decision.primary)
        assertTrue(decision.backup)
    }

    @Test
    fun `losing exact permission keeps the alarm clock channel and only drops the backup`() {
        val decision = alarmScheduleDecision(canScheduleExact = false)

        // setAlarmClock 由系统按用户闹钟对待，权限被收回也不该整条放弃排程
        assertEquals(AlarmPrimaryChannel.AlarmClock, decision.primary)
        assertEquals(false, decision.backup)
    }

    @Test
    fun `without the alarm clock channel scheduling falls back to a window`() {
        val decision = alarmScheduleDecision(canScheduleExact = false, alarmClockAvailable = false)

        assertEquals(AlarmPrimaryChannel.Window, decision.primary)
        assertEquals(false, decision.backup)
    }

    @Test
    fun `backup request code never collides with the primary one`() {
        listOf(0, 1, 7401, Int.MAX_VALUE, 123456789).forEach { primary ->
            val backup = backupRequestCode(primary)
            assertNotEquals("primary=$primary", primary, backup)
            assertTrue("primary=$primary", backup >= 0)
        }
    }
}

class AlarmArrivalOutcomeTest {
    private val trigger = 1_000_000L

    @Test
    fun `on time arrival rings`() {
        assertEquals(
            AlarmArrivalOutcome.Ring,
            alarmArrivalOutcome(trigger, nowMillis = trigger, alreadyHandled = false),
        )
    }

    @Test
    fun `small scheduling jitter still counts as on time`() {
        assertEquals(
            AlarmArrivalOutcome.Ring,
            alarmArrivalOutcome(trigger, nowMillis = trigger + 20_000L, alreadyHandled = false),
        )
    }

    @Test
    fun `late arrival within the tolerance window rings anyway`() {
        val outcome = alarmArrivalOutcome(trigger, nowMillis = trigger + 5 * 60_000L, alreadyHandled = false)

        assertTrue(outcome is AlarmArrivalOutcome.RingLate)
        assertEquals(5 * 60_000L, (outcome as AlarmArrivalOutcome.RingLate).delayMillis)
    }

    @Test
    fun `arrival past the tolerance window is reported as missed`() {
        val outcome = alarmArrivalOutcome(trigger, nowMillis = trigger + 30 * 60_000L, alreadyHandled = false)

        assertTrue(outcome is AlarmArrivalOutcome.Missed)
    }

    @Test
    fun `second channel arriving for the same trigger is discarded`() {
        assertEquals(
            AlarmArrivalOutcome.Duplicate,
            alarmArrivalOutcome(trigger, nowMillis = trigger, alreadyHandled = true),
        )
    }

    @Test
    fun `broadcast from an older generation is discarded before anything else`() {
        val outcome = alarmArrivalOutcome(
            triggerAtMillis = trigger,
            nowMillis = trigger,
            alreadyHandled = false,
            intentGeneration = 3L,
            currentGeneration = 4L,
        )

        assertEquals(AlarmArrivalOutcome.Outdated, outcome)
    }

    @Test
    fun `same generation is not outdated`() {
        val outcome = alarmArrivalOutcome(
            triggerAtMillis = trigger,
            nowMillis = trigger,
            alreadyHandled = false,
            intentGeneration = 4L,
            currentGeneration = 4L,
        )

        assertEquals(AlarmArrivalOutcome.Ring, outcome)
    }

    @Test
    fun `missing trigger time rings rather than being judged late`() {
        assertEquals(
            AlarmArrivalOutcome.Ring,
            alarmArrivalOutcome(triggerAtMillis = 0L, nowMillis = trigger, alreadyHandled = false),
        )
    }
}

class AlarmVolumeRampTest {
    @Test
    fun `ramp starts audible and reaches full volume at the end`() {
        assertEquals(ALARM_VOLUME_RAMP_START, alarmRampVolume(0L), 0.001f)
        assertEquals(1f, alarmRampVolume(ALARM_VOLUME_RAMP_MILLIS), 0.001f)
    }

    @Test
    fun `ramp is monotonic and stays within range`() {
        var previous = -1f
        for (step in 0..40) {
            val value = alarmRampVolume(step * 1000L)
            assertTrue("step=$step", value >= previous)
            assertTrue("step=$step", value in 0f..1f)
            previous = value
        }
    }

    @Test
    fun `past the ramp the volume stays at full`() {
        assertEquals(1f, alarmRampVolume(ALARM_VOLUME_RAMP_MILLIS * 10), 0.001f)
    }

    @Test
    fun `a non positive ramp goes straight to full volume`() {
        assertEquals(1f, alarmRampVolume(0L, rampMillis = 0L), 0.001f)
    }
}
