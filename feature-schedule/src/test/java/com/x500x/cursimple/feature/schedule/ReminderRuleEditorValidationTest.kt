package com.x500x.cursimple.feature.schedule

import com.x500x.cursimple.core.reminder.model.ReminderLabelAction
import com.x500x.cursimple.core.reminder.model.ReminderLabelActionType
import com.x500x.cursimple.core.reminder.model.ReminderLabelCondition
import com.x500x.cursimple.core.reminder.model.ReminderLabelPresence
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReminderRuleEditorValidationTest {
    @Test
    fun `label rule can be saved without trigger conditions`() {
        assertTrue(
            canSaveReminderLabelRule(
                displayName = "考试提醒：期末考试",
                advanceMinutes = 40,
                conditions = emptyList(),
                actions = listOf(ReminderLabelAction("期末考试", ReminderLabelActionType.Remind)),
            ),
        )
    }

    @Test
    fun `label rule can be saved after if then rows are deleted`() {
        assertTrue(
            canSaveReminderLabelRule(
                displayName = "考试提醒",
                advanceMinutes = 40,
                conditions = emptyList(),
                actions = emptyList(),
            ),
        )
    }

    @Test
    fun `blank labels still block saving while rows exist`() {
        assertFalse(
            canSaveReminderLabelRule(
                displayName = "考试提醒",
                advanceMinutes = 40,
                conditions = listOf(ReminderLabelCondition("", ReminderLabelPresence.Exists)),
                actions = emptyList(),
            ),
        )
        assertFalse(
            canSaveReminderLabelRule(
                displayName = "考试提醒",
                advanceMinutes = 40,
                conditions = emptyList(),
                actions = listOf(ReminderLabelAction(" ", ReminderLabelActionType.Remind)),
            ),
        )
    }
}
