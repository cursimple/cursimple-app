package com.x500x.cursimple.core.data

import com.x500x.cursimple.core.data.widget.MAX_SLOT_NODE
import com.x500x.cursimple.core.data.widget.MIN_SLOT_NODE
import com.x500x.cursimple.core.data.widget.SlotDraftInput
import com.x500x.cursimple.core.data.widget.TimingDraftError
import com.x500x.cursimple.core.data.widget.buildTimingSlots
import com.x500x.cursimple.core.data.widget.normalizeTimeOrNull
import com.x500x.cursimple.core.data.widget.timingTemplates
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TimingProfileEditingTest {

    private fun draft(
        startNode: String,
        endNode: String,
        startTime: String,
        endTime: String,
        label: String = "",
    ) = SlotDraftInput(startNode, endNode, startTime, endTime, label)

    @Test
    fun `normalizeTime pads single digit fields`() {
        assertEquals("08:00", normalizeTimeOrNull("8:0"))
        assertEquals("09:05", normalizeTimeOrNull(" 9:5 "))
        assertEquals("21:35", normalizeTimeOrNull("21:35"))
    }

    @Test
    fun `normalizeTime rejects malformed or out of range input`() {
        assertNull(normalizeTimeOrNull(""))
        assertNull(normalizeTimeOrNull("0800"))
        assertNull(normalizeTimeOrNull("24:00"))
        assertNull(normalizeTimeOrNull("10:60"))
        assertNull(normalizeTimeOrNull("a:b"))
    }

    @Test
    fun `build sorts slots by node and normalizes times`() {
        val result = buildTimingSlots(
            listOf(
                draft("3", "4", "10:0", "11:40", "第二大节"),
                draft("1", "2", "8:00", "9:40", "第一大节"),
            ),
        )

        assertTrue(result.isValid)
        assertEquals(listOf(1, 3), result.slots.map { it.startNode })
        assertEquals("08:00", result.slots.first().startTime)
        assertEquals("第一大节", result.slots.first().label)
    }

    @Test
    fun `build rejects empty input`() {
        val result = buildTimingSlots(emptyList())
        assertFalse(result.isValid)
        assertTrue(result.slots.isEmpty())
        assertEquals(listOf(TimingDraftError.EmptyDraft), result.errors)
    }

    @Test
    fun `build rejects node out of range`() {
        val result = buildTimingSlots(listOf(draft("0", "1", "08:00", "08:45")))
        assertFalse(result.isValid)
        assertEquals(
            listOf(TimingDraftError.NodeOutOfRange(row = 1, min = MIN_SLOT_NODE, max = MAX_SLOT_NODE)),
            result.errors,
        )
    }

    @Test
    fun `build rejects start node greater than end node`() {
        val result = buildTimingSlots(listOf(draft("5", "3", "08:00", "08:45")))
        assertFalse(result.isValid)
        assertEquals(listOf(TimingDraftError.NodeOrderReversed(row = 1)), result.errors)
    }

    @Test
    fun `build rejects non numeric node`() {
        val result = buildTimingSlots(listOf(draft("a", "1", "08:00", "08:45")))
        assertFalse(result.isValid)
        assertEquals(listOf(TimingDraftError.NodeNotNumber(row = 1)), result.errors)
    }

    @Test
    fun `build rejects unparseable time`() {
        val result = buildTimingSlots(listOf(draft("1", "1", "8", "08:45")))
        assertFalse(result.isValid)
        assertEquals(listOf(TimingDraftError.TimeFormatInvalid(row = 1)), result.errors)
    }

    @Test
    fun `build rejects start time not before end time`() {
        val result = buildTimingSlots(listOf(draft("1", "1", "09:00", "09:00")))
        assertFalse(result.isValid)
        assertEquals(listOf(TimingDraftError.TimeOrderReversed(row = 1)), result.errors)
    }

    @Test
    fun `build rejects overlapping node ranges`() {
        val result = buildTimingSlots(
            listOf(
                draft("1", "2", "08:00", "09:40"),
                draft("2", "3", "09:00", "10:40"),
            ),
        )
        assertFalse(result.isValid)
        assertEquals(
            listOf(
                TimingDraftError.NodeRangeOverlap(
                    previousStartNode = 1,
                    previousEndNode = 2,
                    currentStartNode = 2,
                    currentEndNode = 3,
                ),
            ),
            result.errors,
        )
    }

    @Test
    fun `adjacent non overlapping ranges are accepted`() {
        val result = buildTimingSlots(
            listOf(
                draft("1", "2", "08:00", "09:40"),
                draft("3", "4", "10:00", "11:40"),
            ),
        )
        assertTrue(result.isValid)
        assertEquals(2, result.slots.size)
    }

    @Test
    fun `every template passes validation and has no overlap`() {
        val templates = timingTemplates()
        assertTrue(templates.size >= 2)
        templates.forEach { template ->
            val drafts = template.slots.map {
                draft(it.startNode.toString(), it.endNode.toString(), it.startTime, it.endTime)
            }
            val result = buildTimingSlots(drafts)
            assertTrue("模板 ${template.id} 应通过校验", result.isValid)
            assertEquals(template.slots.size, result.slots.size)
        }
    }
}
