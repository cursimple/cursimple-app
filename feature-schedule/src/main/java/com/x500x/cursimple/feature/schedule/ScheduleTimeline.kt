package com.x500x.cursimple.feature.schedule

/**
 * Map period rows and actual event minutes onto one vertical axis. Insert only event-occupied
 * gaps and remove them when events disappear.
 */
internal class GridTimeline private constructor(
    val rows: List<TimelineRow>,
    private val anchors: List<TimelineRow>,
    private val slotRows: Map<Int, TimelineRow.Slot>,
) {
    val totalUnits: Float = rows.lastOrNull()?.let { it.top + it.height } ?: 0f

    val hasInsertedRows: Boolean = rows.any { it is TimelineRow.Gap }

    fun slotTop(slotIndex: Int): Float = slotRows[slotIndex]?.top ?: slotIndex.toFloat()

    fun slotBottom(slotIndex: Int): Float = slotRows[slotIndex]?.let { it.top + it.height } ?: (slotIndex + 1f)

    fun yOf(minute: Int): Float {
        if (anchors.isEmpty()) return 0f
        val first = anchors.first()
        if (minute <= first.startMinute!!) return first.top
        var previousBottom = first.top
        for (row in anchors) {
            val start = row.startMinute!!
            val end = row.endMinute!!
            if (minute < start) return previousBottom
            if (minute <= end) {
                val fraction = (minute - start).toFloat() / (end - start).coerceAtLeast(1)
                return row.top + row.height * fraction
            }
            previousBottom = row.top + row.height
        }
        return previousBottom
    }

    fun slotIndexAt(units: Float): Int? {
        val row = rows.firstOrNull { units >= it.top && units < it.top + it.height } ?: rows.lastOrNull()
        return (row as? TimelineRow.Slot)?.index
    }

    /**
     * Snap [units] to the nearest actual period boundary before converting to row displacement.
     */
    fun nearestSlotIndex(units: Float): Int =
        slotRows.values.minByOrNull { kotlin.math.abs(it.top - units) }?.index ?: 0

    companion object {
        internal const val MIN_GAP_MINUTES = 15

        internal const val MIN_GAP_UNITS = 0.6f
        internal const val MAX_GAP_UNITS = 2f

        private const val DEFAULT_SLOT_MINUTES = 45

        /**
         * Slots follow display order with null untimed rows; events provide shared weekly
         * minute ranges so columns align.
         */
        fun build(slots: List<SlotClock>, events: List<MinuteRange>): GridTimeline {
            val timed = slots.withIndex().filter { (_, s) -> s.isTimed }
            val slotMinutes = timed.map { it.value.end!! - it.value.start!! }.average()
                .takeIf { !it.isNaN() && it > 0 } ?: DEFAULT_SLOT_MINUTES.toDouble()

            val inserts = sortedMapOf<Int, MinuteRange>()
            if (timed.isEmpty()) {
                coverAll(events)?.let { inserts[slots.size] = it }
            } else {
                val firstStart = timed.first().value.start!!
                coverBefore(events, firstStart)?.let { inserts[timed.first().index] = it }
                timed.zipWithNext().forEach { (a, b) ->
                    val gapStart = a.value.end!!
                    val gapEnd = b.value.start!!
                    if (gapEnd > gapStart) coverBetween(events, gapStart, gapEnd)?.let { inserts[b.index] = it }
                }
                val lastEnd = timed.last().value.end!!
                coverAfter(events, lastEnd)?.let { inserts[timed.last().index + 1] = it }
            }

            val rows = mutableListOf<TimelineRow>()
            var top = 0f
            fun addGap(range: MinuteRange) {
                val height = ((range.end - range.start) / slotMinutes).toFloat()
                    .coerceIn(MIN_GAP_UNITS, MAX_GAP_UNITS)
                rows += TimelineRow.Gap(range.start, range.end, top, height)
                top += height
            }
            slots.forEachIndexed { index, slot ->
                inserts[index]?.let(::addGap)
                rows += TimelineRow.Slot(index, slot.start, slot.end, top, 1f)
                top += 1f
            }
            inserts[slots.size]?.let(::addGap)

            val anchors = rows.filter { it.startMinute != null && it.endMinute != null && it.endMinute!! > it.startMinute!! }
                .sortedBy { it.startMinute }
            val slotRows = rows.filterIsInstance<TimelineRow.Slot>().associateBy { it.index }
            return GridTimeline(rows, anchors, slotRows)
        }

        private fun coverAll(events: List<MinuteRange>): MinuteRange? {
            if (events.isEmpty()) return null
            return MinuteRange(events.minOf { it.start }, events.maxOf { it.end })
        }

        private fun coverBefore(events: List<MinuteRange>, firstStart: Int): MinuteRange? {
            val hits = events.filter { it.start < firstStart && qualifies(it, Int.MIN_VALUE, firstStart) }
            if (hits.isEmpty()) return null
            return MinuteRange(hits.minOf { it.start }, minOf(firstStart, hits.maxOf { it.end }))
        }

        private fun coverAfter(events: List<MinuteRange>, lastEnd: Int): MinuteRange? {
            val hits = events.filter { it.end > lastEnd && qualifies(it, lastEnd, Int.MAX_VALUE) }
            if (hits.isEmpty()) return null
            return MinuteRange(maxOf(lastEnd, hits.minOf { it.start }), hits.maxOf { it.end })
        }

        /** Expand only event-occupied gap intervals. */
        private fun coverBetween(events: List<MinuteRange>, gapStart: Int, gapEnd: Int): MinuteRange? {
            val hits = events.filter { it.start < gapEnd && it.end > gapStart && qualifies(it, gapStart, gapEnd) }
            if (hits.isEmpty()) return null
            return MinuteRange(
                maxOf(gapStart, hits.minOf { it.start }),
                minOf(gapEnd, hits.maxOf { it.end }),
            )
        }

        private fun qualifies(event: MinuteRange, gapStart: Int, gapEnd: Int): Boolean {
            val inside = event.start >= gapStart && event.end <= gapEnd
            val overlap = minOf(event.end, gapEnd) - maxOf(event.start, gapStart)
            return inside || overlap >= MIN_GAP_MINUTES
        }
    }
}

internal sealed interface TimelineRow {
    val top: Float
    val height: Float
    val startMinute: Int?
    val endMinute: Int?

    data class Slot(
        val index: Int,
        override val startMinute: Int?,
        override val endMinute: Int?,
        override val top: Float,
        override val height: Float,
    ) : TimelineRow

    data class Gap(
        override val startMinute: Int,
        override val endMinute: Int,
        override val top: Float,
        override val height: Float,
    ) : TimelineRow
}

internal data class SlotClock(val start: Int?, val end: Int?) {
    val isTimed: Boolean get() = start != null && end != null && end > start
}

internal data class MinuteRange(val start: Int, val end: Int)

internal fun clockMinute(raw: String): Int? {
    val parts = raw.trim().split(':')
    if (parts.size != 2) return null
    val h = parts[0].toIntOrNull() ?: return null
    val m = parts[1].toIntOrNull() ?: return null
    if (h !in 0..23 || m !in 0..59) return null
    return h * 60 + m
}

internal fun DisplaySlot.clock(): SlotClock = SlotClock(clockMinute(startTime), clockMinute(endTime))

/** Assign parallel lanes to overlapping courses and events, placing courses first. */
internal data class LaneItem<K>(val key: K, val top: Float, val bottom: Float, val order: Int)

internal data class LanePosition(val lane: Int, val laneCount: Int)

internal fun <K> assignLanes(items: List<LaneItem<K>>): Map<K, LanePosition> {
    if (items.isEmpty()) return emptyMap()
    val epsilon = 0.001f
    val sorted = items.sortedWith(compareBy({ it.top }, { it.order }, { -it.bottom }))
    val result = HashMap<K, LanePosition>()
    var cluster = mutableListOf<Pair<LaneItem<K>, Int>>()
    val laneBottoms = mutableListOf<Float>()
    var clusterBottom = Float.NEGATIVE_INFINITY

    fun flush() {
        val count = laneBottoms.size.coerceAtLeast(1)
        cluster.forEach { (item, lane) -> result[item.key] = LanePosition(lane, count) }
        cluster = mutableListOf()
        laneBottoms.clear()
    }

    for (item in sorted) {
        if (item.top >= clusterBottom - epsilon) flush()
        val lane = laneBottoms.indexOfFirst { it <= item.top + epsilon }
            .takeIf { it >= 0 } ?: laneBottoms.size.also { laneBottoms += item.bottom }
        laneBottoms[lane] = item.bottom
        cluster += item to lane
        clusterBottom = maxOf(clusterBottom, item.bottom)
    }
    flush()
    return result
}

/** Express lanes as column fractions; every overlap gets equal width without occlusion. */
internal fun laneFraction(position: LanePosition): Pair<Float, Float> {
    val n = position.laneCount.coerceAtLeast(1)
    return position.lane.toFloat() / n to 1f / n
}

/** Bound expanded day weights by [MAX_COLUMN_WEIGHT] so one day cannot consume the week. */
internal class DayColumns private constructor(private val weights: List<Float>) {
    private val starts: List<Float> = weights.runningFold(0f) { acc, w -> acc + w }

    val totalUnits: Float = starts.last()

    val isUniform: Boolean = weights.all { it == 1f }

    fun start(index: Int): Float = starts.getOrElse(index.coerceIn(0, weights.size)) { 0f }

    fun width(index: Int): Float = weights.getOrElse(index) { 1f }

    fun indexAt(units: Float): Int {
        val i = starts.indexOfFirst { it > units } - 1
        return (if (i < 0) weights.size - 1 else i).coerceIn(0, (weights.size - 1).coerceAtLeast(0))
    }

    /**
     * Fit all days within [availableDp]; normalize common expansion and shrink extra widths
     * toward equal columns if necessary.
     */
    fun fitInto(availableDp: Float, minUnitDp: Float): DayColumns {
        if (weights.isEmpty()) return this
        val base = weights.min()
        val normalized = weights.map { it / base }
        val count = normalized.size
        val extra = normalized.sumOf { (it - 1f).toDouble() }.toFloat()
        if (extra <= 0f) return DayColumns(normalized)
        // Enforce minUnitDp while distributing extra column weight.
        val k = ((availableDp / minUnitDp - count) / extra).coerceIn(0f, 1f)
        return DayColumns(normalized.map { 1f + (it - 1f) * k })
    }

    companion object {
        const val MAX_COLUMN_WEIGHT = 3

        fun of(laneCounts: List<Int>): DayColumns =
            DayColumns(laneCounts.map { it.coerceIn(1, MAX_COLUMN_WEIGHT).toFloat() })
    }
}

/**
 * Group overlapping rendered event bounds, including minimum-height expansion; adjacent
 * nonoverlapping bounds stay separate.
 */
internal fun <T> clusterByOverlap(items: List<T>, top: (T) -> Float, bottom: (T) -> Float): List<List<T>> {
    val epsilon = 0.001f
    val clusters = mutableListOf<MutableList<T>>()
    var clusterBottom = Float.NEGATIVE_INFINITY
    for (item in items.sortedBy(top)) {
        if (clusters.isEmpty() || top(item) >= clusterBottom - epsilon) {
            clusters += mutableListOf(item)
            clusterBottom = bottom(item)
        } else {
            clusters.last() += item
            clusterBottom = maxOf(clusterBottom, bottom(item))
        }
    }
    return clusters
}
