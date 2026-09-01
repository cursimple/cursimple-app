package com.x500x.cursimple.app.util

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface

/** 课程块的一套配色：底色、文字色、左侧色条。 */
private data class BlockPalette(
    val fill: Int,
    val text: Int,
    val accent: Int,
)

/**
 * 把 [ScheduleImageLayoutResult] 画进一张位图。
 * 只负责按已算好的坐标落笔，任何尺寸与换行都在排版阶段决定。
 */
object ScheduleImageRenderer {

    private const val PAGE = 0xFFF4F6F9.toInt()
    private const val CARD = 0xFFFFFFFF.toInt()
    private const val CARD_BORDER = 0xFFE1E6ED.toInt()
    private const val GRID_LINE = 0xFFEDF0F5.toInt()
    private const val HEADER_FILL = 0xFFF8FAFC.toInt()
    private const val WEEKEND_FILL = 0xFFF1F5F9.toInt()
    private const val NODE_FILL = 0xFFFAFBFD.toInt()
    private const val TEXT_PRIMARY = 0xFF1F2430.toInt()
    private const val TEXT_SECONDARY = 0xFF6B7280.toInt()
    private const val TEXT_MUTED = 0xFF9AA3B0.toInt()
    private const val NOTE_TEXT = 0xFFB4530A.toInt()
    private const val HOLIDAY_FILL = 0xFFF2F4F7.toInt()
    private const val HOLIDAY_TEXT = 0xFF7C8697.toInt()
    private const val OVERFLOW_FILL = 0xFFEDEFF3.toInt()
    private const val OVERFLOW_TEXT = 0xFF4B5464.toInt()

    private const val CARD_RADIUS = 22f
    private const val BLOCK_RADIUS = 12f
    private const val ACCENT_WIDTH = 6f

    private val palettes = listOf(
        BlockPalette(0xFFE7F0FE.toInt(), 0xFF1B4A87.toInt(), 0xFF3B82F6.toInt()),
        BlockPalette(0xFFE4F6EC.toInt(), 0xFF15603D.toInt(), 0xFF10B981.toInt()),
        BlockPalette(0xFFFDF0E0.toInt(), 0xFF8A4B0E.toInt(), 0xFFF59E0B.toInt()),
        BlockPalette(0xFFF1EBFD.toInt(), 0xFF553398.toInt(), 0xFF8B5CF6.toInt()),
        BlockPalette(0xFFFDEAEF.toInt(), 0xFF8B2540.toInt(), 0xFFEC4899.toInt()),
        BlockPalette(0xFFE3F5F8.toInt(), 0xFF115E6A.toInt(), 0xFF06B6D4.toInt()),
        BlockPalette(0xFFEEF3E3.toInt(), 0xFF4A5C1F.toInt(), 0xFF84CC16.toInt()),
        BlockPalette(0xFFE9ECF2.toInt(), 0xFF37404F.toInt(), 0xFF64748B.toInt()),
    )

    private val examPalette = BlockPalette(0xFFFDE7E3.toInt(), 0xFF8E2410.toInt(), 0xFFEF4444.toInt())

    /** 用真实字体测量文本，供排版阶段换行使用。 */
    fun textMeasurer(): ScheduleImageTextMeasurer {
        val regular = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT }
        val bold = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD }
        return ScheduleImageTextMeasurer { text, fontSize, isBold ->
            val paint = if (isBold) bold else regular
            paint.textSize = fontSize
            paint.measureText(text)
        }
    }

    fun render(layout: ScheduleImageLayoutResult): Bitmap {
        val bitmap = Bitmap.createBitmap(layout.width, layout.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(PAGE)
        drawHeader(canvas, layout)

        val card = layout.gridRect.toRectF()
        canvas.drawRoundRect(card, CARD_RADIUS, CARD_RADIUS, fillPaint(CARD))
        // 表格内容一律裁在圆角卡片内，避免方角填充盖掉卡片圆角
        canvas.save()
        canvas.clipPath(Path().apply { addRoundRect(card, CARD_RADIUS, CARD_RADIUS, Path.Direction.CW) })
        drawDayHeaders(canvas, layout)
        drawNodeColumn(canvas, layout)
        drawGridLines(canvas, layout)
        drawHolidays(canvas, layout)
        drawBlocks(canvas, layout)
        canvas.restore()
        canvas.drawRoundRect(card, CARD_RADIUS, CARD_RADIUS, strokePaint(CARD_BORDER, 2f))

        drawFootnotes(canvas, layout)
        return bitmap
    }

    private fun drawHeader(canvas: Canvas, layout: ScheduleImageLayoutResult) {
        val metrics = layout.metrics
        val titlePaint = textPaint(metrics.headerTitleFontSize, TEXT_PRIMARY, bold = true)
        val subtitlePaint = textPaint(metrics.headerSubtitleFontSize, TEXT_SECONDARY, bold = false)
        val left = metrics.outerPadding
        val titleTop = metrics.outerPadding + 14f
        canvas.drawTextInLine(layout.title, left, titleTop, metrics.headerTitleFontSize * 1.4f, titlePaint)
        canvas.drawTextInLine(
            layout.subtitle,
            left,
            titleTop + metrics.headerTitleFontSize * 1.4f + 6f,
            metrics.headerSubtitleFontSize * 1.4f,
            subtitlePaint,
        )
    }

    private fun drawDayHeaders(canvas: Canvas, layout: ScheduleImageLayoutResult) {
        val metrics = layout.metrics
        for (header in layout.dayHeaders) {
            val fill = if (header.isWeekend) WEEKEND_FILL else HEADER_FILL
            canvas.drawRect(header.rect.toRectF(), fillPaint(fill))

            val namePaint = centeredPaint(metrics.dayNameFontSize, TEXT_PRIMARY, bold = true)
            val datePaint = centeredPaint(metrics.dayDateFontSize, TEXT_SECONDARY, bold = false)
            val notePaint = centeredPaint(metrics.dayNoteFontSize, NOTE_TEXT, bold = false)

            val nameHeight = metrics.dayNameFontSize * 1.4f
            val dateHeight = metrics.dayDateFontSize * 1.4f
            val noteHeight = metrics.dayNoteFontSize * 1.4f
            val used = nameHeight + dateHeight + (if (header.noteLabel != null) noteHeight else 0f)
            var top = header.rect.top + (header.rect.height - used) / 2f

            canvas.drawTextInLine(header.weekdayLabel, header.rect.centerX, top, nameHeight, namePaint)
            top += nameHeight
            canvas.drawTextInLine(header.dateLabel, header.rect.centerX, top, dateHeight, datePaint)
            top += dateHeight
            header.noteLabel?.let { canvas.drawTextInLine(it, header.rect.centerX, top, noteHeight, notePaint) }
        }
        // 表头与格子之间的分隔线
        val line = layout.bodyRect
        canvas.drawLine(line.left, line.top, line.right, line.top, strokePaint(CARD_BORDER, 2f))
    }

    private fun drawNodeColumn(canvas: Canvas, layout: ScheduleImageLayoutResult) {
        val metrics = layout.metrics
        val column = layout.nodeColumnRect
        canvas.drawRect(
            RectF(column.left, layout.bodyRect.top, column.right, column.bottom),
            fillPaint(NODE_FILL),
        )
        val indexPaint = centeredPaint(metrics.nodeIndexFontSize, TEXT_PRIMARY, bold = true)
        val timePaint = centeredPaint(metrics.nodeTimeFontSize, TEXT_MUTED, bold = false)
        for (row in layout.rows) {
            val indexHeight = metrics.nodeIndexFontSize * 1.4f
            val timeHeight = metrics.nodeTimeFontSize * 1.35f
            val used = indexHeight + timeHeight * 2
            var top = row.rect.top + (row.rect.height - used) / 2f
            canvas.drawTextInLine(row.nodeLabel, row.rect.centerX, top, indexHeight, indexPaint)
            top += indexHeight
            canvas.drawTextInLine(row.startTimeLabel, row.rect.centerX, top, timeHeight, timePaint)
            top += timeHeight
            canvas.drawTextInLine(row.endTimeLabel, row.rect.centerX, top, timeHeight, timePaint)
        }
    }

    private fun drawGridLines(canvas: Canvas, layout: ScheduleImageLayoutResult) {
        val paint = strokePaint(GRID_LINE, 2f)
        val body = layout.bodyRect
        for (row in layout.rows.drop(1)) {
            canvas.drawLine(body.left, row.rect.top, body.right, row.rect.top, paint)
        }
        canvas.drawLine(
            layout.nodeColumnRect.right,
            layout.gridRect.top,
            layout.nodeColumnRect.right,
            body.bottom,
            strokePaint(CARD_BORDER, 2f),
        )
        for (header in layout.dayHeaders.drop(1)) {
            canvas.drawLine(header.rect.left, layout.gridRect.top, header.rect.left, body.bottom, paint)
        }
    }

    private fun drawHolidays(canvas: Canvas, layout: ScheduleImageLayoutResult) {
        for (holiday in layout.holidays) {
            canvas.drawRect(holiday.rect.toRectF(), fillPaint(HOLIDAY_FILL))
            val paint = centeredPaint(holiday.fontSize, HOLIDAY_TEXT, bold = true)
            val used = holiday.lines.size * holiday.lineHeight
            var top = holiday.rect.centerY - used / 2f
            for (line in holiday.lines) {
                canvas.drawTextInLine(line, holiday.rect.centerX, top, holiday.lineHeight, paint)
                top += holiday.lineHeight
            }
        }
    }

    private fun drawBlocks(canvas: Canvas, layout: ScheduleImageLayoutResult) {
        for (block in layout.blocks) {
            val palette = when {
                block.isOverflow -> BlockPalette(OVERFLOW_FILL, OVERFLOW_TEXT, TEXT_MUTED)
                block.isExam -> examPalette
                else -> palettes[block.colorIndex % palettes.size]
            }
            val rect = block.rect.toRectF()
            canvas.drawRoundRect(rect, BLOCK_RADIUS, BLOCK_RADIUS, fillPaint(palette.fill))
            canvas.save()
            canvas.clipRect(rect.left, rect.top, rect.left + ACCENT_WIDTH, rect.bottom)
            canvas.drawRoundRect(rect, BLOCK_RADIUS, BLOCK_RADIUS, fillPaint(palette.accent))
            canvas.restore()

            val detailColor = blend(palette.text, palette.fill, 0.28f)
            var top = block.contentRect.top
            for (line in block.lines) {
                when (line.role) {
                    ScheduleImageTextRole.Title -> {
                        val paint = textPaint(block.titleFontSize, palette.text, bold = true)
                        canvas.drawTextInLine(line.text, block.contentRect.left, top, block.titleLineHeight, paint)
                        top += block.titleLineHeight
                    }

                    ScheduleImageTextRole.Detail -> {
                        val paint = textPaint(block.detailFontSize, detailColor, bold = false)
                        canvas.drawTextInLine(line.text, block.contentRect.left, top, block.detailLineHeight, paint)
                        top += block.detailLineHeight
                    }
                }
            }
        }
    }

    private fun drawFootnotes(canvas: Canvas, layout: ScheduleImageLayoutResult) {
        if (layout.footnotes.isEmpty()) return
        val metrics = layout.metrics
        val paint = textPaint(metrics.footnoteFontSize, TEXT_SECONDARY, bold = false)
        var top = layout.footnoteTop
        for (line in layout.footnotes) {
            canvas.drawTextInLine(line, metrics.outerPadding, top, metrics.footnoteLineHeight, paint)
            top += metrics.footnoteLineHeight
        }
    }

    private fun textPaint(size: Float, color: Int, bold: Boolean): Paint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
            textSize = size
            this.color = color
            textAlign = Paint.Align.LEFT
        }

    private fun centeredPaint(size: Float, color: Int, bold: Boolean): Paint =
        textPaint(size, color, bold).apply { textAlign = Paint.Align.CENTER }

    private fun fillPaint(color: Int): Paint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            this.color = color
        }

    private fun strokePaint(color: Int, width: Float): Paint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = width
            this.color = color
        }

    /** 在高度为 [lineHeight]、顶边为 [lineTop] 的一行里垂直居中地写一行字。 */
    private fun Canvas.drawTextInLine(text: String, x: Float, lineTop: Float, lineHeight: Float, paint: Paint) {
        if (text.isEmpty()) return
        val fm = paint.fontMetrics
        val baseline = lineTop + (lineHeight - (fm.descent - fm.ascent)) / 2f - fm.ascent
        drawText(text, x, baseline, paint)
    }

    private fun ScheduleImageRect.toRectF(): RectF = RectF(left, top, right, bottom)

    /** 把 [color] 按 [ratio] 向 [towards] 靠拢，用来从主色推出次要文字色。 */
    private fun blend(color: Int, towards: Int, ratio: Float): Int {
        fun channel(shift: Int): Int {
            val from = (color shr shift) and 0xFF
            val to = (towards shr shift) and 0xFF
            return (from + (to - from) * ratio).toInt().coerceIn(0, 255)
        }
        return (0xFF shl 24) or (channel(16) shl 16) or (channel(8) shl 8) or channel(0)
    }
}
