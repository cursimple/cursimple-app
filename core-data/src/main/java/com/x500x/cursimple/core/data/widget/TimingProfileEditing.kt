package com.x500x.cursimple.core.data.widget

import android.content.Context
import android.content.res.Configuration
import android.os.LocaleList
import com.x500x.cursimple.core.data.AppLanguage
import com.x500x.cursimple.core.data.R
import com.x500x.cursimple.core.data.toLocale
import com.x500x.cursimple.core.kernel.model.ClassSlotTime
import com.x500x.cursimple.core.kernel.model.TermTimingProfile
import com.x500x.cursimple.core.kernel.model.slotsCovering
import java.time.LocalTime
import java.util.Locale

/** Period-number bounds shared with course entry. */
const val MIN_SLOT_NODE = 1
const val MAX_SLOT_NODE = 32

data class SlotDraftInput(
    val startNode: String,
    val endNode: String,
    val startTime: String,
    val endTime: String,
    val label: String,
    val labelKey: String? = null,
    val shownLabel: String = label,
    /**
     * Preserve unedited label text because reminder rules use it as a stable key across locale
     * changes.
     */
    val storedLabel: String = label,
)

sealed interface TimingDraftError {
    data object EmptyDraft : TimingDraftError
    data class NodeNotNumber(val row: Int) : TimingDraftError
    data class NodeOutOfRange(val row: Int, val min: Int, val max: Int) : TimingDraftError
    data class NodeOrderReversed(val row: Int) : TimingDraftError
    data class TimeFormatInvalid(val row: Int) : TimingDraftError
    data class TimeOrderReversed(val row: Int) : TimingDraftError
    data class NodeRangeOverlap(
        val previousStartNode: Int,
        val previousEndNode: Int,
        val currentStartNode: Int,
        val currentEndNode: Int,
    ) : TimingDraftError
}

fun Context.timingDraftErrorText(error: TimingDraftError): String = when (error) {
    TimingDraftError.EmptyDraft -> getString(R.string.data_timing_error_no_slots)
    is TimingDraftError.NodeNotNumber ->
        getString(R.string.data_timing_error_node_not_number, error.row)
    is TimingDraftError.NodeOutOfRange ->
        getString(R.string.data_timing_error_node_out_of_range, error.row, error.min, error.max)
    is TimingDraftError.NodeOrderReversed ->
        getString(R.string.data_timing_error_node_reversed, error.row)
    is TimingDraftError.TimeFormatInvalid ->
        getString(R.string.data_timing_error_time_format, error.row)
    is TimingDraftError.TimeOrderReversed ->
        getString(R.string.data_timing_error_time_reversed, error.row)
    is TimingDraftError.NodeRangeOverlap -> getString(
        R.string.data_timing_error_node_overlap,
        error.previousStartNode,
        error.previousEndNode,
        error.currentStartNode,
        error.currentEndNode,
    )
}

/** Validation errors leave slots empty and prohibit saving. */
data class TimingDraftResult(
    val slots: List<ClassSlotTime>,
    val errors: List<TimingDraftError>,
) {
    val isValid: Boolean get() = errors.isEmpty()
}

data class TimingTemplateSlot(
    val startNode: Int,
    val endNode: Int,
    val startTime: String,
    val endTime: String,
    val labelRes: Int,
    val labelArg: Int? = null,
    val labelKey: String? = null,
)

/** Sample timing templates require local timetable adjustment. */
data class TimingTemplate(
    val id: String,
    val nameRes: Int,
    val summaryRes: Int,
    val slots: List<TimingTemplateSlot>,
)

fun TimingTemplate.slotTimes(context: Context): List<ClassSlotTime> = slots.map { slot ->
    ClassSlotTime(
        startNode = slot.startNode,
        endNode = slot.endNode,
        startTime = slot.startTime,
        endTime = slot.endTime,
        label = slot.labelArg
            ?.let { context.getString(slot.labelRes, it) }
            ?: context.getString(slot.labelRes),
        labelKey = slot.labelKey,
    )
}

fun classSlotLabelRes(labelKey: String?): Int? = when (labelKey) {
    SLOT_LABEL_KEY_BLOCK_1 -> R.string.data_timing_slot_label_block_1
    SLOT_LABEL_KEY_BLOCK_2 -> R.string.data_timing_slot_label_block_2
    SLOT_LABEL_KEY_BLOCK_3 -> R.string.data_timing_slot_label_block_3
    SLOT_LABEL_KEY_BLOCK_4 -> R.string.data_timing_slot_label_block_4
    SLOT_LABEL_KEY_BLOCK_5 -> R.string.data_timing_slot_label_block_5
    SLOT_LABEL_KEY_BLOCK_6 -> R.string.data_timing_slot_label_block_6
    SLOT_LABEL_KEY_BLOCK_7 -> R.string.data_timing_slot_label_block_7
    SLOT_LABEL_KEY_BLOCK_8 -> R.string.data_timing_slot_label_block_8
    else -> null
}

fun blockLabelKeyOfIndex(index: Int): String? =
    "block_$index".takeIf { classSlotLabelRes(it) != null }

fun Context.slotBlockIndex(slot: ClassSlotTime): Int? {
    val key = slot.labelKey ?: inferSlotLabelKey(slot.label) ?: return null
    return key.removePrefix("block_").toIntOrNull()?.takeIf { blockLabelKeyOfIndex(it) == key }
}

fun Context.classSlotLabelOfBlock(index: Int): String? =
    blockLabelKeyOfIndex(index)?.let { classSlotLabelRes(it) }?.let(::getString)

/** Cache built-in label mappings from each locale without changing the process locale. */
@Volatile
private var blockLabelKeysByText: Map<String, String>? = null

private fun Context.blockLabelKeysByText(): Map<String, String> =
    blockLabelKeysByText ?: buildMap {
        val locales = AppLanguage.entries.mapNotNull { it.toLocale() }
        (listOf(null) + locales).forEach { locale ->
            val source = if (locale == null) this@blockLabelKeysByText else localizedContext(locale)
            BLOCK_LABEL_KEYS.forEach { key ->
                classSlotLabelRes(key)?.let { put(source.getString(it).trim(), key) }
            }
        }
    }.also { blockLabelKeysByText = it }

private fun Context.localizedContext(locale: Locale): Context {
    val configuration = Configuration(resources.configuration)
    configuration.setLocales(LocaleList(locale))
    return createConfigurationContext(configuration)
}

/** Infer legacy label IDs against every translation; unmatched text remains user-defined. */
private fun Context.inferSlotLabelKey(label: String): String? {
    val trimmed = label.trim()
    if (trimmed.isBlank()) return null
    return runCatching { blockLabelKeysByText()[trimmed] }.getOrNull()
}

/** Localize built-in IDs, preserve custom labels and generate missing labels by index. */
fun Context.classSlotLabelText(slot: ClassSlotTime, fallbackIndex: Int): String = when {
    slot.labelKey == SLOT_LABEL_KEY_PERIOD ->
        getString(R.string.data_timing_slot_label_period, slot.startNode)

    else -> classSlotLabelRes(slot.labelKey ?: inferSlotLabelKey(slot.label))?.let(::getString)
        ?: slot.label.takeIf { it.isNotBlank() }
        ?: getString(R.string.data_timing_slot_label_period, fallbackIndex)
}

/**
 * Return a label only when the course occupies one timing slot; multi-slot and missing matches
 * return null.
 */
fun Context.courseSlotLabelText(profile: TermTimingProfile?, startNode: Int, endNode: Int): String? =
    slotLabelText(profile?.slotsCovering(startNode, endNode).orEmpty())

/** [courseSlotLabelText] with already-resolved indexed slots. */
fun Context.slotLabelText(covering: List<IndexedValue<ClassSlotTime>>): String? {
    val (index, slot) = covering.singleOrNull() ?: return null
    return classSlotLabelText(slot, index + 1).trim().takeIf { it.isNotBlank() }
}

fun Context.classSlotLabelOfIndex(index: Int): String =
    getString(R.string.data_timing_slot_label_period, index)

const val SLOT_LABEL_KEY_BLOCK_1 = "block_1"
const val SLOT_LABEL_KEY_BLOCK_2 = "block_2"
const val SLOT_LABEL_KEY_BLOCK_3 = "block_3"
const val SLOT_LABEL_KEY_BLOCK_4 = "block_4"
const val SLOT_LABEL_KEY_BLOCK_5 = "block_5"
const val SLOT_LABEL_KEY_BLOCK_6 = "block_6"
const val SLOT_LABEL_KEY_BLOCK_7 = "block_7"
const val SLOT_LABEL_KEY_BLOCK_8 = "block_8"
const val SLOT_LABEL_KEY_PERIOD = "period"

private val BLOCK_LABEL_KEYS = listOf(
    SLOT_LABEL_KEY_BLOCK_1,
    SLOT_LABEL_KEY_BLOCK_2,
    SLOT_LABEL_KEY_BLOCK_3,
    SLOT_LABEL_KEY_BLOCK_4,
    SLOT_LABEL_KEY_BLOCK_5,
    SLOT_LABEL_KEY_BLOCK_6,
    SLOT_LABEL_KEY_BLOCK_7,
    SLOT_LABEL_KEY_BLOCK_8,
)

/** Normalize full-width input and pad valid times; return null when parsing fails. */
fun normalizeTimeOrNull(raw: String): String? {
    val text = raw.trim().halfWidthDigitsAndColon()
    if (text.isEmpty()) return null
    val parts = text.split(":")
    if (parts.size != 2) return null
    val hour = parts[0].trim().toIntOrNull() ?: return null
    val minute = parts[1].trim().toIntOrNull() ?: return null
    return runCatching {
        val time = LocalTime.of(hour, minute)
        "%02d:%02d".format(time.hour, time.minute)
    }.getOrNull()
}

private fun String.halfWidthDigitsAndColon(): String = map { ch ->
    when (ch) {
        '\uFF1A', '\u2236' -> ':'
        in '\uFF10'..'\uFF19' -> ch - 0xFEE0
        else -> ch
    }
}.joinToString("")

/** Parse and normalize timing rows, returning per-row validation errors. */
fun buildTimingSlots(drafts: List<SlotDraftInput>): TimingDraftResult {
    if (drafts.isEmpty()) {
        return TimingDraftResult(emptyList(), listOf(TimingDraftError.EmptyDraft))
    }

    val errors = mutableListOf<TimingDraftError>()
    val parsed = mutableListOf<ClassSlotTime>()

    drafts.forEachIndexed { index, draft ->
        val row = index + 1
        val startNode = draft.startNode.trim().toIntOrNull()
        val endNode = draft.endNode.trim().toIntOrNull()
        if (startNode == null || endNode == null) {
            errors += TimingDraftError.NodeNotNumber(row)
            return@forEachIndexed
        }
        if (startNode !in MIN_SLOT_NODE..MAX_SLOT_NODE || endNode !in MIN_SLOT_NODE..MAX_SLOT_NODE) {
            errors += TimingDraftError.NodeOutOfRange(row, MIN_SLOT_NODE, MAX_SLOT_NODE)
            return@forEachIndexed
        }
        if (startNode > endNode) {
            errors += TimingDraftError.NodeOrderReversed(row)
            return@forEachIndexed
        }
        val startTime = normalizeTimeOrNull(draft.startTime)
        val endTime = normalizeTimeOrNull(draft.endTime)
        if (startTime == null || endTime == null) {
            errors += TimingDraftError.TimeFormatInvalid(row)
            return@forEachIndexed
        }
        if (LocalTime.parse(startTime) >= LocalTime.parse(endTime)) {
            errors += TimingDraftError.TimeOrderReversed(row)
            return@forEachIndexed
        }
        parsed += ClassSlotTime(
            startNode = startNode,
            endNode = endNode,
            startTime = startTime,
            endTime = endTime,
            label = draft.resolvedLabel(),
            labelKey = draft.resolvedLabelKey(),
        )
    }

    if (errors.isNotEmpty()) {
        return TimingDraftResult(emptyList(), errors)
    }

    val sorted = parsed.sortedWith(compareBy({ it.startNode }, { it.endNode }))
    for (i in 1 until sorted.size) {
        val prev = sorted[i - 1]
        val current = sorted[i]
        if (current.startNode <= prev.endNode) {
            errors += TimingDraftError.NodeRangeOverlap(
                previousStartNode = prev.startNode,
                previousEndNode = prev.endNode,
                currentStartNode = current.startNode,
                currentEndNode = current.endNode,
            )
        }
    }

    return if (errors.isEmpty()) {
        TimingDraftResult(sorted, emptyList())
    } else {
        TimingDraftResult(emptyList(), errors)
    }
}

fun timingTemplates(): List<TimingTemplate> = listOf(
    TimingTemplate(
        id = "single_11",
        nameRes = R.string.data_timing_template_single11_name,
        summaryRes = R.string.data_timing_template_single11_summary,
        slots = listOf(
            periodSlot(1, "08:00", "08:45"),
            periodSlot(2, "08:55", "09:40"),
            periodSlot(3, "10:00", "10:45"),
            periodSlot(4, "10:55", "11:40"),
            periodSlot(5, "14:00", "14:45"),
            periodSlot(6, "14:55", "15:40"),
            periodSlot(7, "16:00", "16:45"),
            periodSlot(8, "16:55", "17:40"),
            periodSlot(9, "19:00", "19:45"),
            periodSlot(10, "19:55", "20:40"),
            periodSlot(11, "20:50", "21:35"),
        ),
    ),
    TimingTemplate(
        id = "block_5",
        nameRes = R.string.data_timing_template_block5_name,
        summaryRes = R.string.data_timing_template_block5_summary,
        slots = listOf(
            TimingTemplateSlot(1, 2, "08:00", "09:40", R.string.data_timing_slot_label_block_1, labelKey = SLOT_LABEL_KEY_BLOCK_1),
            TimingTemplateSlot(3, 4, "10:00", "11:40", R.string.data_timing_slot_label_block_2, labelKey = SLOT_LABEL_KEY_BLOCK_2),
            TimingTemplateSlot(5, 6, "14:00", "15:40", R.string.data_timing_slot_label_block_3, labelKey = SLOT_LABEL_KEY_BLOCK_3),
            TimingTemplateSlot(7, 8, "16:00", "17:40", R.string.data_timing_slot_label_block_4, labelKey = SLOT_LABEL_KEY_BLOCK_4),
            TimingTemplateSlot(9, 10, "19:00", "20:40", R.string.data_timing_slot_label_block_5, labelKey = SLOT_LABEL_KEY_BLOCK_5),
        ),
    ),
    TimingTemplate(
        id = "single_8",
        nameRes = R.string.data_timing_template_single8_name,
        summaryRes = R.string.data_timing_template_single8_summary,
        slots = listOf(
            periodSlot(1, "08:00", "08:45"),
            periodSlot(2, "08:55", "09:40"),
            periodSlot(3, "10:00", "10:45"),
            periodSlot(4, "10:55", "11:40"),
            periodSlot(5, "14:00", "14:45"),
            periodSlot(6, "14:55", "15:40"),
            periodSlot(7, "16:00", "16:45"),
            periodSlot(8, "16:55", "17:40"),
        ),
    ),
)

private fun periodSlot(node: Int, startTime: String, endTime: String): TimingTemplateSlot =
    TimingTemplateSlot(
        startNode = node,
        endNode = node,
        startTime = startTime,
        endTime = endTime,
        labelRes = R.string.data_timing_slot_label_period,
        labelArg = node,
        labelKey = SLOT_LABEL_KEY_PERIOD,
    )

fun ClassSlotTime.toDraftInput(): SlotDraftInput = SlotDraftInput(
    startNode = startNode.toString(),
    endNode = endNode.toString(),
    startTime = startTime,
    endTime = endTime,
    label = label,
    labelKey = labelKey,
    shownLabel = label,
    storedLabel = label,
)

fun ClassSlotTime.toDraftInput(context: Context, fallbackIndex: Int): SlotDraftInput {
    val shown = context.classSlotLabelText(this, fallbackIndex)
    return SlotDraftInput(
        startNode = startNode.toString(),
        endNode = endNode.toString(),
        startTime = startTime,
        endTime = endTime,
        label = shown,
        labelKey = labelKey,
        shownLabel = shown,
        storedLabel = label,
    )
}

private fun SlotDraftInput.resolvedLabel(): String =
    if (label.trim() == shownLabel.trim()) storedLabel else label.trim()

private fun SlotDraftInput.resolvedLabelKey(): String? =
    labelKey.takeIf { label.trim() == shownLabel.trim() }
