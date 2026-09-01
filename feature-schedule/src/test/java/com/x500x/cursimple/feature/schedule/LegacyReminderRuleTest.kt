package com.x500x.cursimple.feature.schedule

import com.x500x.cursimple.core.reminder.model.ReminderRule
import com.x500x.cursimple.core.reminder.model.ReminderScopeType
import org.junit.Assert.assertEquals
import org.junit.Test

class LegacyReminderRuleTest {

    @Test
    fun onlyLegacyScopeRulesAreListedAsInactive() {
        val rules = listOf(
            rule("a", ReminderScopeType.SingleCourse),
            rule("b", ReminderScopeType.TimeSlot),
            rule("c", ReminderScopeType.Exam),
            rule("d", ReminderScopeType.FirstCourseOfPeriod),
            rule("e", ReminderScopeType.LabelRule),
        )

        assertEquals(listOf("a", "b", "c"), legacyReminderRules(rules).map { it.ruleId })
    }

    @Test
    fun legacyRuleLabelFallsBackToScopeType() {
        assertEquals("单课提醒（旧版）", legacyReminderRuleLabel(rule("a", ReminderScopeType.SingleCourse)))
        assertEquals("时间段提醒（旧版）", legacyReminderRuleLabel(rule("b", ReminderScopeType.TimeSlot)))
        assertEquals("考试提醒（旧版）", legacyReminderRuleLabel(rule("c", ReminderScopeType.Exam)))
    }

    @Test
    fun legacyRuleLabelPrefersDisplayName() {
        val named = rule("a", ReminderScopeType.SingleCourse).copy(displayName = "高等数学")

        assertEquals("高等数学", legacyReminderRuleLabel(named))
    }

    private fun rule(ruleId: String, scopeType: ReminderScopeType): ReminderRule = ReminderRule(
        ruleId = ruleId,
        pluginId = "demo",
        scopeType = scopeType,
        advanceMinutes = 15,
        createdAt = "2026-02-23T00:00:00+08:00",
        updatedAt = "2026-02-23T00:00:00+08:00",
    )
}
