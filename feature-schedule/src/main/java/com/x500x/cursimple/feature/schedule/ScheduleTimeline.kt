package com.x500x.cursimple.feature.schedule

/**
 * 周网格的纵轴：把「第几节」和「几点几分」对到同一把尺子上。
 *
 * 单位是「节高」：一节课占 1。平时每节一行、首尾相接，和原来的网格一模一样。
 * 事务是按钟点排的，落在节次里就按钟点在那一节里插值；
 * 落在没有节次的时段（午休、早上第一节前、晚上最后一节后）时，
 * 在那个位置临时插一段出来，高度按时长算，下面的节次跟着往下挪。
 * 这一周没有事务、或者事务删掉了，插的段就不存在，网格原样恢复。
 */
internal class GridTimeline private constructor(
    val rows: List<TimelineRow>,
    /** 有钟点的行，按钟点排好，用来把分钟换成纵坐标 */
    private val anchors: List<TimelineRow>,
    private val slotRows: Map<Int, TimelineRow.Slot>,
) {
    val totalUnits: Float = rows.lastOrNull()?.let { it.top + it.height } ?: 0f

    /** 这一周有没有插过段；没插过的网格和原来完全一样 */
    val hasInsertedRows: Boolean = rows.any { it is TimelineRow.Gap }

    fun slotTop(slotIndex: Int): Float = slotRows[slotIndex]?.top ?: slotIndex.toFloat()

    fun slotBottom(slotIndex: Int): Float = slotRows[slotIndex]?.let { it.top + it.height } ?: (slotIndex + 1f)

    /** 某一分钟在纵轴上的位置。落在被跳过的课间里时贴到上一段的底边。 */
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

    /** 纵坐标落在哪一节；落在插出来的段里返回 null（那里没有节次可以加课）。 */
    fun slotIndexAt(units: Float): Int? {
        val row = rows.firstOrNull { units >= it.top && units < it.top + it.height } ?: rows.lastOrNull()
        return (row as? TimelineRow.Slot)?.index
    }

    /**
     * 拖动课程块时的落点：离 [units] 最近的那一节的上沿。
     *
     * 拖动吸附是按「挪几节」算的，插了段之后一节不再对应固定的高度，
     * 所以先在这把尺子上找最近的节，再换回挪了几节。
     */
    fun nearestSlotIndex(units: Float): Int =
        slotRows.values.minByOrNull { kotlin.math.abs(it.top - units) }?.index ?: 0

    companion object {
        /** 事务在课间里待的时间短于这个就不插段，直接跨过去：十分钟的课间不值得撑开一行 */
        internal const val MIN_GAP_MINUTES = 15

        /** 插出来的段最矮、最高各占几节：太矮放不下字，太高一个午休能把课表撑出一屏 */
        internal const val MIN_GAP_UNITS = 0.6f
        internal const val MAX_GAP_UNITS = 2f

        private const val DEFAULT_SLOT_MINUTES = 45

        /**
         * @param slots 网格的每一行（按显示顺序），没有钟点的行给 null
         * @param events 这一周要画的事务的起止分钟（不分哪天，插段对整周统一，各天才对得齐）
         */
        fun build(slots: List<SlotClock>, events: List<MinuteRange>): GridTimeline {
            val timed = slots.withIndex().filter { (_, s) -> s.isTimed }
            val slotMinutes = timed.map { it.value.end!! - it.value.start!! }.average()
                .takeIf { !it.isNaN() && it > 0 } ?: DEFAULT_SLOT_MINUTES.toDouble()

            // 插在哪一行前面 → 插的那一段
            val inserts = sortedMapOf<Int, MinuteRange>()
            if (timed.isEmpty()) {
                // 连作息都没有，钟点无从对齐：事务整体接在最后一行后面，按时间先后排
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

        /** 第一节之前：事务在那儿待够一刻钟，或者整个都在那儿，才撑开一段 */
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

        /** 课间：只撑开事务真正占到的那一截，剩下的课间照旧合拢 */
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

    /** 原本的一节 */
    data class Slot(
        val index: Int,
        override val startMinute: Int?,
        override val endMinute: Int?,
        override val top: Float,
        override val height: Float,
    ) : TimelineRow

    /** 为事务临时插出来的一段 */
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

/** 「08:00」→ 480；解析不了给 null */
internal fun clockMinute(raw: String): Int? {
    val parts = raw.trim().split(':')
    if (parts.size != 2) return null
    val h = parts[0].toIntOrNull() ?: return null
    val m = parts[1].toIntOrNull() ?: return null
    if (h !in 0..23 || m !in 0..59) return null
    return h * 60 + m
}

internal fun DisplaySlot.clock(): SlotClock = SlotClock(clockMinute(startTime), clockMinute(endTime))

/**
 * 同一天里在纵向上叠在一起的块，排成并排的几条道。
 *
 * 课和事务一起排：不叠的块照旧占满整列；叠在一起的一簇平分列宽，
 * 课先占左边的道，事务往右排。
 */
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

/**
 * 一条道在列里的横向位置，按列宽的比例给：起点、宽度。
 *
 * 叠在一起的块一律并排、平分这一列，谁也不盖住谁。
 * 列宽由 [DayColumns] 按这天要并排几条道放宽，平分下来每块仍有正常一列那么宽。
 */
internal fun laneFraction(position: LanePosition): Pair<Float, Float> {
    val n = position.laneCount.coerceAtLeast(1)
    return position.lane.toFloat() / n to 1f / n
}

/**
 * 一周各列的宽度，单位是「普通一列」。
 *
 * 平时每列都是 1，和原来的等宽网格一样。某天有课和事务撞在一起要并排两条道，
 * 这一列就放宽到 2，其余列一起让出一点；最多放到 [MAX_COLUMN_WEIGHT]，
 * 再多道就在这宽度里平分，免得一列把整周挤没了。
 */
internal class DayColumns private constructor(private val weights: List<Float>) {
    private val starts: List<Float> = weights.runningFold(0f) { acc, w -> acc + w }

    val totalUnits: Float = starts.last()

    /** 没有哪一列被放宽：和原来的等宽网格一样 */
    val isUniform: Boolean = weights.all { it == 1f }

    fun start(index: Int): Float = starts.getOrElse(index.coerceIn(0, weights.size)) { 0f }

    fun width(index: Int): Float = weights.getOrElse(index) { 1f }

    /** 横坐标（单位同上）落在第几列 */
    fun indexAt(units: Float): Int {
        val i = starts.indexOfFirst { it > units } - 1
        return (if (i < 0) weights.size - 1 else i).coerceIn(0, (weights.size - 1).coerceAtLeast(0))
    }

    /**
     * 保证整周塞得进 [availableDp]：课表不能左右滑，超出去的那几天就看不到了。
     *
     * 先约掉大家都有的那部分放宽（七天都要两条道等于谁都没放宽）；
     * 普通一列还窄于 [minUnitDp] 的话，按比例收回多放的宽度，最坏退回等宽——
     * 那样并排的块在一列里平分，窄一点，但一天都不会丢。
     */
    fun fitInto(availableDp: Float, minUnitDp: Float): DayColumns {
        if (weights.isEmpty()) return this
        val base = weights.min()
        val normalized = weights.map { it / base }
        val count = normalized.size
        val extra = normalized.sumOf { (it - 1f).toDouble() }.toFloat()
        if (extra <= 0f) return DayColumns(normalized)
        // 普通一列至少 minUnitDp：count + k·extra 份要塞进 availableDp
        val k = ((availableDp / minUnitDp - count) / extra).coerceIn(0f, 1f)
        return DayColumns(normalized.map { 1f + (it - 1f) * k })
    }

    companion object {
        const val MAX_COLUMN_WEIGHT = 3

        /** [laneCounts]：每一列当天最多并排几条道 */
        fun of(laneCounts: List<Int>): DayColumns =
            DayColumns(laneCounts.map { it.coerceIn(1, MAX_COLUMN_WEIGHT).toFloat() })
    }
}

/**
 * 把纵向上互相重叠的块归成一组（只要有一部分叠着就算，首尾相接不算）。
 *
 * 事务之间叠在一起时不再各开一条道——一列里挤三四条道谁都看不清——
 * 而是合成一块「⋯」，点开再列出这一组里的全部事务。
 * 按画出来的上下沿算而不是按钟点：十分钟的事按最小高度画出来会比钟点长，
 * 钟点上首尾相接的两件事画出来可能已经叠在一起了。
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
