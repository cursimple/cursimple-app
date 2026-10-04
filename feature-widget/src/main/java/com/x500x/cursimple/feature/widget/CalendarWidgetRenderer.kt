package com.x500x.cursimple.feature.widget

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import com.x500x.cursimple.core.data.ThemeAccent
import com.x500x.cursimple.core.data.theme.AccentColors
import com.x500x.cursimple.core.data.widget.WidgetThemePreferences
import kotlin.math.max
import kotlin.math.min

/** 画在图上的文字，由界面层按当前语言给出 */
internal data class CalendarRenderLabels(
    /** 周一到周日的短称 */
    val weekdays: List<String>,
    val holidayTag: String,
    val makeUpTag: String,
    val legendClass: String,
    val legendExam: String,
    val legendEvent: String,
    /** 网格里一节课都没有时居中显示；为空则不显示 */
    val emptyMessage: String?,
)

/**
 * 课程日历的网格画成一张图。
 *
 * RemoteViews 排不出按节次跨行的格子，各家桌面对嵌套布局的支持也参差不齐，
 * 画成图在哪个桌面上都一个样。点击由盖在图上的透明格子负责，几何尺寸取自这里的常量。
 */
internal object CalendarWidgetRenderer {
    /** 周视图：顶部日期栏高（dp）；左侧节次栏宽随作息表里的名字长短变，见 [weekGutterDp] */
    const val WEEK_HEADER_DP = 32f
    private const val GUTTER_MIN_DP = 16f
    private const val GUTTER_MAX_DP = 38f
    private const val GUTTER_LABEL_DP = 8.5f
    private const val GUTTER_LABEL_MIN_DP = 6.5f
    private const val GUTTER_TIME_DP = 7f

    /**
     * 左侧节次栏宽（dp）：放得下最长的节次名（如「第一大节」「午间课」），太长的换行或缩小，
     * 但不超过 [GUTTER_MAX_DP]，给课程格留出地方。点击层按同一个宽度对齐。
     */
    fun weekGutterDp(week: CalendarWeekData): Float {
        val paint = textPaint(GUTTER_LABEL_DP, TEXT_SECONDARY, bold = false)
        val widest = week.rows.maxOfOrNull { paint.measureText(it.label) } ?: 0f
        return (widest + 5f).coerceIn(GUTTER_MIN_DP, GUTTER_MAX_DP)
    }

    /** 月视图：顶部星期栏高、底部图例高（dp） */
    const val MONTH_HEADER_DP = 18f
    const val MONTH_LEGEND_DP = 18f

    private const val MAX_BITMAP_EDGE = 1200

    private const val TEXT_PRIMARY = 0xFF20242A.toInt()
    private const val TEXT_SECONDARY = 0xFF66707F.toInt()
    private const val GRID_LINE = 0x1A20242A
    internal const val EXAM_RED = 0xFFD64545.toInt()
    internal const val EVENT_ORANGE = 0xFFE08A2E.toInt()
    private const val INACTIVE_CONTAINER = 0xFFE6EAE7.toInt()
    private const val INACTIVE_ON = 0xFF8E988F.toInt()

    /** 与课表网格同一套课程配色（浅色），按课名取 */
    private val COURSE_PALETTE = listOf(
        0xFFDDEBFA.toInt() to 0xFF2C5587.toInt(),
        0xFFDCEFD7.toInt() to 0xFF325E2A.toInt(),
        0xFFFBE0E4.toInt() to 0xFF872E48.toInt(),
        0xFFE5DEF6.toInt() to 0xFF4F388B.toInt(),
        0xFFFBEFCE.toInt() to 0xFF7E5B14.toInt(),
        0xFFD5EBE6.toInt() to 0xFF1F5C50.toInt(),
        0xFFFBE0CB.toInt() to 0xFF8C4A1F.toInt(),
    )

    internal fun coursePalette(seed: String): Pair<Int, Int> = COURSE_PALETTE[seed.hashCode().mod(COURSE_PALETTE.size)]

    fun render(
        data: CalendarWidgetData,
        widthDp: Float,
        heightDp: Float,
        density: Float,
        labels: CalendarRenderLabels,
    ): Bitmap {
        val rawW = max(1f, widthDp * density)
        val rawH = max(1f, heightDp * density)
        val shrink = min(1f, MAX_BITMAP_EDGE / max(rawW, rawH))
        val bitmap = Bitmap.createBitmap((rawW * shrink).toInt().coerceAtLeast(1), (rawH * shrink).toInt().coerceAtLeast(1), Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val px = density * shrink
        val accent = accentOf(data.widgetTheme)
        val accentText = widgetAccentTextColor(data.widgetTheme)
        when {
            data.week != null -> drawWeek(canvas, data.week, px, accent, accentText, labels)
            data.month != null -> drawMonth(canvas, data.month, px, accent, accentText, labels)
        }
        return bitmap
    }

    private fun accentOf(theme: WidgetThemePreferences): Int =
        if (theme.themeAccent == ThemeAccent.Custom) theme.customColorArgb else AccentColors.presetPrimary(theme.themeAccent)

    // ---------------- 周视图 ----------------

    private fun drawWeek(canvas: Canvas, week: CalendarWeekData, px: Float, accent: Int, accentText: Int, labels: CalendarRenderLabels) {
        val w = canvas.width.toFloat()
        val h = canvas.height.toFloat()
        val gutter = weekGutterDp(week) * px
        val header = WEEK_HEADER_DP * px
        val columns = week.days.size.coerceAtLeast(1)
        val colW = (w - gutter) / columns
        val rowCount = week.rows.size.coerceAtLeast(1)
        val rowH = (h - header) / rowCount
        val fill = Paint(Paint.ANTI_ALIAS_FLAG)

        // 今天那一列铺一层浅底，一眼找到
        week.days.forEachIndexed { index, day ->
            if (!day.isToday) return@forEachIndexed
            fill.color = withAlpha(accent, 0x1F)
            canvas.drawRoundRect(RectF(gutter + index * colW + 1 * px, 0f, gutter + (index + 1) * colW - 1 * px, h), 8 * px, 8 * px, fill)
        }

        // 时段横线与左侧节次名（作息表里的名字，和 App 课表左栏一致）
        val line = Paint().apply { color = GRID_LINE; strokeWidth = max(1f, 0.6f * px) }
        week.rows.forEachIndexed { index, row ->
            val top = header + index * rowH
            if (index > 0) canvas.drawLine(gutter, top, w, top, line)
            drawRowLabel(canvas, row, RectF(0f, top, gutter, top + rowH), px)
        }

        // 顶部：星期、日期，今天用实心胶囊
        val weekdayPaint = textPaint(9f * px, TEXT_SECONDARY, bold = false).apply { textAlign = Paint.Align.CENTER }
        val datePaint = textPaint(12f * px, TEXT_PRIMARY, bold = true).apply { textAlign = Paint.Align.CENTER }
        val tagPaint = textPaint(7.5f * px, EXAM_RED, bold = true).apply { textAlign = Paint.Align.CENTER }
        week.days.forEachIndexed { index, day ->
            val left = gutter + index * colW
            val cx = left + colW / 2f
            if (day.isToday) {
                fill.color = accent
                val pillW = min(colW - 4 * px, 30 * px)
                canvas.drawRoundRect(RectF(cx - pillW / 2, 2 * px, cx + pillW / 2, header - 3 * px), 9 * px, 9 * px, fill)
            }
            val onAccent = day.isToday
            weekdayPaint.color = if (onAccent) 0xE6FFFFFF.toInt() else TEXT_SECONDARY
            datePaint.color = if (onAccent) 0xFFFFFFFF.toInt() else if (day.onHoliday) INACTIVE_ON else TEXT_PRIMARY
            canvas.drawText(labels.weekdays.getOrElse(day.date.dayOfWeek.value - 1) { "" }, cx, 12.5f * px, weekdayPaint)
            canvas.drawText(day.date.dayOfMonth.toString(), cx, 26f * px, datePaint)
            val tag = when {
                day.onHoliday -> labels.holidayTag
                day.makeUpWorkday -> labels.makeUpTag
                else -> null
            }
            if (tag != null) {
                tagPaint.color = if (onAccent) 0xFFFFFFFF.toInt() else if (day.onHoliday) EXAM_RED else accentText
                canvas.drawText(tag, left + colW - 6 * px, 9.5f * px, tagPaint)
            }
            if (day.eventCount > 0) {
                fill.color = if (onAccent) 0xFFFFFFFF.toInt() else EVENT_ORANGE
                canvas.drawCircle(left + 6 * px, 6 * px, 2f * px, fill)
            }
        }

        // 课程块
        val gap = 1.5f * px
        week.blocks.forEachIndexed { index, blocks ->
            val colLeft = gutter + index * colW
            blocks.forEach { block ->
                val span = week.rowSpanOf(block) ?: return@forEach
                val laneW = colW / block.laneCount
                val rect = RectF(
                    colLeft + block.lane * laneW + gap,
                    header + span.first * rowH + gap,
                    colLeft + (block.lane + 1) * laneW - gap,
                    header + (span.last + 1) * rowH - gap,
                )
                if (rect.height() <= 0f || rect.width() <= 0f) return@forEach
                drawCourseBlock(canvas, rect, block, px)
            }
        }

        if (week.blocks.all { it.isEmpty() } && !labels.emptyMessage.isNullOrBlank()) {
            val paint = textPaint(11f * px, TEXT_SECONDARY, bold = true).apply { textAlign = Paint.Align.CENTER }
            canvas.drawText(labels.emptyMessage, gutter + (w - gutter) / 2f, centerBaseline(header, h - header, paint), paint)
        }
    }

    /**
     * 一行的节次名：先按一行放，放不下就换行，行高也不够再把字缩小（最小 [GUTTER_LABEL_MIN_DP]）；
     * 名字下面还有地方时补上开始时间。
     */
    private fun drawRowLabel(canvas: Canvas, row: CalendarRow, cell: RectF, px: Float) {
        val width = (cell.width() - 3f * px).toInt()
        if (width <= 2 || cell.height() <= 2f) return
        var size = GUTTER_LABEL_DP
        var paint = textPaint(size * px, TEXT_SECONDARY, bold = false)
        var layout = centeredLayout(row.label, paint, width, maxLinesFor(cell.height(), paint).coerceAtLeast(1))
        while (layout.isTruncated() && size > GUTTER_LABEL_MIN_DP) {
            size = (size - 0.5f).coerceAtLeast(GUTTER_LABEL_MIN_DP)
            paint = textPaint(size * px, TEXT_SECONDARY, bold = false)
            layout = centeredLayout(row.label, paint, width, maxLinesFor(cell.height(), paint).coerceAtLeast(1))
        }
        val timePaint = textPaint(GUTTER_TIME_DP * px, withAlpha(TEXT_SECONDARY, 0xB3), bold = false)
        val showTime = row.startTime.isNotBlank() &&
            cell.height() - layout.height >= timePaint.fontSpacing + 2f * px &&
            timePaint.measureText(row.startTime) <= width
        val total = layout.height + if (showTime) timePaint.fontSpacing else 0f
        canvas.save()
        canvas.clipRect(cell)
        canvas.translate(cell.left + (cell.width() - width) / 2f, cell.top + (cell.height() - total) / 2f)
        layout.draw(canvas)
        if (showTime) {
            timePaint.textAlign = Paint.Align.CENTER
            canvas.drawText(row.startTime, width / 2f, layout.height - timePaint.ascent(), timePaint)
        }
        canvas.restore()
    }

    private fun centeredLayout(text: String, paint: TextPaint, width: Int, maxLines: Int): StaticLayout =
        StaticLayout.Builder.obtain(text, 0, text.length, paint, width)
            .setAlignment(Layout.Alignment.ALIGN_CENTER)
            .setIncludePad(false)
            .setMaxLines(maxLines)
            .setEllipsize(TextUtils.TruncateAt.END)
            .build()

    /** 被截断时 StaticLayout 会在最后一行留下省略号 */
    private fun StaticLayout.isTruncated(): Boolean = lineCount > 0 && getEllipsisCount(lineCount - 1) > 0

    private fun drawCourseBlock(canvas: Canvas, rect: RectF, block: CalendarCourseBlock, px: Float) {
        val (container, onContainer) = when {
            block.inactive -> INACTIVE_CONTAINER to INACTIVE_ON
            else -> coursePalette(block.colorSeed)
        }
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = container }
        val radius = 6f * px
        canvas.drawRoundRect(rect, radius, radius, fill)
        if (block.isExam && !block.inactive) {
            val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = 1.4f * px
                color = EXAM_RED
            }
            val inset = stroke.strokeWidth / 2
            canvas.drawRoundRect(RectF(rect.left + inset, rect.top + inset, rect.right - inset, rect.bottom - inset), radius, radius, stroke)
        }
        val pad = 3f * px
        val textW = (rect.width() - pad * 2).toInt()
        val availH = rect.height() - pad * 2
        if (textW <= 4) return
        val titlePaint = textPaint(9.5f * px, onContainer, bold = true)
        // 格子矮到放不下一整行时也照样写一行，按格子裁掉，总比一块空色块强
        val titleLines = if (availH <= 4) 0 else maxLinesFor(availH, titlePaint)
        canvas.save()
        canvas.clipRect(rect)
        if (titleLines <= 1) {
            // 只放得下一行时直接按格子裁掉：「高等数」比「高…」认得出；格子比一行还矮就把字缩到放得下
            titlePaint.textSize = min(titlePaint.textSize, max(6f * px, rect.height() * 0.72f))
            val baseline = rect.centerY() - (titlePaint.descent() + titlePaint.ascent()) / 2f
            canvas.drawText(block.title, rect.left + pad, baseline, titlePaint)
            canvas.restore()
            return
        }
        val title = staticLayout(block.title, titlePaint, textW, titleLines)
        canvas.translate(rect.left + pad, rect.top + pad)
        title.draw(canvas)
        val used = title.height.toFloat()
        val location = block.location.trim()
        if (location.isNotEmpty()) {
            val locPaint = textPaint(8.5f * px, withAlpha(onContainer, 0xCC), bold = false)
            val lines = maxLinesFor(availH - used - 1.5f * px, locPaint)
            if (lines > 0) {
                canvas.translate(0f, used + 1.5f * px)
                staticLayout("@$location", locPaint, textW, lines).draw(canvas)
            }
        }
        canvas.restore()
    }

    // ---------------- 月视图 ----------------

    private fun drawMonth(canvas: Canvas, month: CalendarMonthData, px: Float, accent: Int, accentText: Int, labels: CalendarRenderLabels) {
        val w = canvas.width.toFloat()
        val h = canvas.height.toFloat()
        val top = MONTH_HEADER_DP * px
        val bottom = MONTH_LEGEND_DP * px
        val colW = w / 7f
        val rows = month.rows.coerceAtLeast(1)
        val rowH = (h - top - bottom) / rows
        val fill = Paint(Paint.ANTI_ALIAS_FLAG)

        val weekdayPaint = textPaint(9f * px, TEXT_SECONDARY, bold = false).apply { textAlign = Paint.Align.CENTER }
        for (i in 0 until 7) {
            canvas.drawText(labels.weekdays.getOrElse(i) { "" }, colW * i + colW / 2f, centerBaseline(0f, top, weekdayPaint), weekdayPaint)
        }

        val datePaint = textPaint(min(12.5f * px, rowH * 0.42f), TEXT_PRIMARY, bold = true).apply { textAlign = Paint.Align.CENTER }
        val tagPaint = textPaint(7.5f * px, EXAM_RED, bold = true).apply { textAlign = Paint.Align.CENTER }
        val dotR = 2.1f * px
        month.days.forEachIndexed { index, day ->
            val row = index / 7
            val col = index % 7
            val left = col * colW
            val cellTop = top + row * rowH
            val cx = left + colW / 2f
            val inMonth = month.inMonth(day)
            // 数字放在格子上半部，下面留给圆点
            val numberCenterY = cellTop + rowH * 0.42f
            if (day.isToday) {
                fill.color = accent
                val r = min(min(colW, rowH) * 0.34f, 13f * px)
                canvas.drawCircle(cx, numberCenterY, r, fill)
            }
            datePaint.color = when {
                day.isToday -> 0xFFFFFFFF.toInt()
                !inMonth -> withAlpha(TEXT_SECONDARY, 0x66)
                day.onHoliday -> EXAM_RED
                else -> TEXT_PRIMARY
            }
            val baseline = numberCenterY - (datePaint.descent() + datePaint.ascent()) / 2f
            canvas.drawText(day.date.dayOfMonth.toString(), cx, baseline, datePaint)

            val tag = when {
                day.onHoliday -> labels.holidayTag
                day.makeUpWorkday -> labels.makeUpTag
                else -> null
            }
            if (tag != null && inMonth) {
                tagPaint.color = if (day.onHoliday) EXAM_RED else accentText
                canvas.drawText(tag, left + colW - 7 * px, cellTop + 9 * px, tagPaint)
            }

            val dots = buildList {
                if (day.classCount > 0) add(accent)
                if (day.hasExam) add(EXAM_RED)
                if (day.eventCount > 0) add(EVENT_ORANGE)
            }
            if (dots.isNotEmpty()) {
                val spacing = dotR * 2.8f
                val startX = cx - spacing * (dots.size - 1) / 2f
                val dotY = min(cellTop + rowH - 4.5f * px, numberCenterY + rowH * 0.36f)
                dots.forEachIndexed { i, color ->
                    fill.color = if (inMonth) color else withAlpha(color, 0x59)
                    canvas.drawCircle(startX + i * spacing, dotY, dotR, fill)
                }
            }
        }

        // 图例：每种圆点是什么意思
        val legendPaint = textPaint(8.5f * px, TEXT_SECONDARY, bold = false)
        val entries = listOf(accent to labels.legendClass, EXAM_RED to labels.legendExam, EVENT_ORANGE to labels.legendEvent)
        val itemGap = 10f * px
        val textGap = 3.5f * px
        val totalW = entries.sumOf { (_, text) -> (dotR * 2 + textGap + legendPaint.measureText(text)).toDouble() }.toFloat() +
            itemGap * (entries.size - 1)
        var x = (w - totalW) / 2f
        val baseline = centerBaseline(h - bottom, bottom, legendPaint)
        val dotY = h - bottom / 2f
        entries.forEach { (color, text) ->
            fill.color = color
            canvas.drawCircle(x + dotR, dotY, dotR, fill)
            x += dotR * 2 + textGap
            canvas.drawText(text, x, baseline, legendPaint)
            x += legendPaint.measureText(text) + itemGap
        }
    }

    // ---------------- 工具 ----------------

    private fun textPaint(sizePx: Float, color: Int, bold: Boolean) = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = sizePx
        this.color = color
        typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
    }

    private fun centerBaseline(top: Float, height: Float, paint: Paint): Float =
        top + height / 2f - (paint.descent() + paint.ascent()) / 2f

    private fun maxLinesFor(height: Float, paint: TextPaint): Int {
        val lineH = paint.fontSpacing
        return if (lineH <= 0f) 0 else (height / lineH).toInt()
    }

    private fun staticLayout(text: CharSequence, paint: TextPaint, width: Int, maxLines: Int): StaticLayout =
        StaticLayout.Builder.obtain(text, 0, text.length, paint, width.coerceAtLeast(1))
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setIncludePad(false)
            .setLineSpacing(0f, 1f)
            .setMaxLines(maxLines.coerceAtLeast(1))
            .setEllipsize(TextUtils.TruncateAt.END)
            .build()

    private fun withAlpha(color: Int, alpha: Int): Int = (color and 0x00FFFFFF) or (alpha shl 24)
}
