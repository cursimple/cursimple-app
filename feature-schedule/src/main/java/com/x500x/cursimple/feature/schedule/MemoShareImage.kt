package com.x500x.cursimple.feature.schedule

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.text.Layout
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.StaticLayout
import android.text.TextPaint
import android.text.style.BackgroundColorSpan
import android.text.style.StrikethroughSpan
import android.text.style.StyleSpan
import android.text.style.TypefaceSpan
import androidx.core.graphics.ColorUtils
import com.x500x.cursimple.core.kernel.model.MemoPriority
import kotlin.math.ceil
import kotlin.math.max

/** Light share palette derived from the app accent; dark-theme accents are deepened for contrast. */
internal data class MemoShareImageColors(
    val accent: Int,
    val background: Int,
    val surface: Int = 0xFFFFFFFF.toInt(),
    val outline: Int,
    val text: Int = 0xFF1F2328.toInt(),
    val muted: Int = 0xFF5F6670.toInt(),
    val error: Int = 0xFFB3261E.toInt(),
    val warning: Int = 0xFFB86E00.toInt(),
    val codeBackground: Int = 0xFFEEF0F2.toInt(),
) {
    companion object {
        fun fromAccent(argb: Int): MemoShareImageColors {
            val opaque = argb or 0xFF000000.toInt()
            val accent = if (ColorUtils.calculateLuminance(opaque) > 0.3) {
                ColorUtils.blendARGB(opaque, 0xFF000000.toInt(), 0.45f)
            } else {
                opaque
            }
            return MemoShareImageColors(
                accent = accent,
                background = ColorUtils.blendARGB(accent, 0xFFFFFFFF.toInt(), 0.94f),
                outline = ColorUtils.blendARGB(accent, 0xFFFFFFFF.toInt(), 0.8f),
            )
        }
    }
}

/** Light course palette shared with the timetable and widgets, keyed by course title. */
private val NOTEBOOK_PALETTE = listOf(
    0xFFDDEBFA.toInt() to 0xFF2C5587.toInt(),
    0xFFDCEFD7.toInt() to 0xFF325E2A.toInt(),
    0xFFFBE0E4.toInt() to 0xFF872E48.toInt(),
    0xFFE5DEF6.toInt() to 0xFF4F388B.toInt(),
    0xFFFBEFCE.toInt() to 0xFF7E5B14.toInt(),
    0xFFD5EBE6.toInt() to 0xFF1F5C50.toInt(),
    0xFFFBE0CB.toInt() to 0xFF8C4A1F.toInt(),
)

/**
 * Draw notes as cards on fixed-width pages. Content is split into line-sized units so very long
 * notes break across pages instead of producing an oversized bitmap.
 */
internal class MemoShareImageRenderer(
    private val colors: MemoShareImageColors,
    private val widthPx: Int = 1080,
    private val maxPagePx: Int = 4320,
    private val images: Map<String, MemoShareEmbeddedImage> = emptyMap(),
) {
    private val s = widthPx / 400f
    private fun u(value: Float): Float = value * s
    private fun ui(value: Float): Int = ceil(value * s).toInt()

    private val padH = u(16f)
    private val padTop = ui(20f)
    private val padBottom = ui(20f)
    private val footerHeight = ui(22f)
    private val cardRadius = u(16f)
    private val cardPadStart = u(18f)
    private val cardPadEnd = u(16f)
    private val cardLeft = padH
    private val cardRight = widthPx - padH
    private val textLeft = cardLeft + cardPadStart
    private val textWidth = (cardRight - cardPadEnd - textLeft).toInt()

    private class Chip(val text: String, val background: Int, val foreground: Int)

    private class Slice(
        var height: Int,
        val card: Int? = null,
        var stripe: Int? = null,
        var cardStart: Boolean = false,
        var cardEnd: Boolean = false,
        var keepWithNext: Boolean = false,
        val droppable: Boolean = false,
        val draw: (Canvas, Float) -> kotlin.Unit = { _, _ -> },
    )

    /** Render [doc] page by page into [sink]; returns the page count. */
    fun render(doc: MemoShareDocument, pageLabel: (Int, Int) -> String, sink: (Int, Bitmap) -> kotlin.Unit): Int {
        val pages = paginate(buildUnits(doc))
        pages.forEachIndexed { index, units ->
            val content = units.sumOf { it.height }
            val footer = if (pages.size > 1) footerHeight else 0
            val bitmap = Bitmap.createBitmap(widthPx, padTop + content + padBottom + footer, Bitmap.Config.ARGB_8888)
            try {
                val canvas = Canvas(bitmap)
                canvas.drawColor(colors.background)
                var y = padTop.toFloat()
                units.forEach { unit ->
                    if (unit.card != null) drawCardSlice(canvas, y, unit)
                    unit.draw(canvas, y)
                    y += unit.height
                }
                if (pages.size > 1) {
                    val paint = textPaint(11.5f, colors.muted)
                    val label = pageLabel(index + 1, pages.size)
                    canvas.drawText(label, cardRight - paint.measureText(label), bitmap.height - padBottom.toFloat(), paint)
                }
                sink(index, bitmap)
            } finally {
                bitmap.recycle()
            }
        }
        return pages.size
    }

    private fun paginate(units: List<Slice>): List<List<Slice>> {
        val limit = maxPagePx - padTop - padBottom - footerHeight
        val pages = mutableListOf<MutableList<Slice>>()
        var page = mutableListOf<Slice>()
        var height = 0
        units.forEach { unit ->
            if (page.isEmpty() && unit.droppable) return@forEach
            if (height + unit.height > limit && page.isNotEmpty()) {
                // Carry trailing keep-with-next units (titles, tags) to the next page.
                val carry = ArrayDeque<Slice>()
                while (!unit.droppable && page.size > 1 && page.last().keepWithNext) carry.addFirst(page.removeAt(page.lastIndex))
                while (page.isNotEmpty() && page.last().droppable) page.removeAt(page.lastIndex)
                pages += page
                page = carry.toMutableList()
                height = page.sumOf { it.height }
                if (unit.droppable) return@forEach
            }
            page += unit
            height += unit.height
        }
        while (page.isNotEmpty() && page.last().droppable) page.removeAt(page.lastIndex)
        if (page.isNotEmpty() || pages.isEmpty()) pages += page
        return pages
    }

    private fun drawCardSlice(canvas: Canvas, top: Float, unit: Slice) {
        val bottom = top + unit.height
        val extTop = if (unit.cardStart) top else top - cardRadius * 2
        val extBottom = if (unit.cardEnd) bottom else bottom + cardRadius * 2
        val rect = RectF(cardLeft, extTop, cardRight, extBottom)
        canvas.save()
        canvas.clipRect(0f, top, widthPx.toFloat(), bottom)
        canvas.drawRoundRect(rect, cardRadius, cardRadius, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = colors.surface })
        unit.stripe?.let { stripe ->
            canvas.save()
            canvas.clipPath(Path().apply { addRoundRect(rect, cardRadius, cardRadius, Path.Direction.CW) })
            canvas.drawRect(cardLeft, extTop, cardLeft + u(5f), extBottom, Paint().apply { color = stripe })
            canvas.restore()
        }
        val stroke = u(1f)
        val border = RectF(rect.left + stroke / 2, rect.top + stroke / 2, rect.right - stroke / 2, rect.bottom - stroke / 2)
        canvas.drawRoundRect(border, cardRadius, cardRadius, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = stroke
            color = colors.outline
        })
        canvas.restore()
    }

    private fun buildUnits(doc: MemoShareDocument): List<Slice> {
        val units = mutableListOf<Slice>()
        doc.title?.let { title ->
            units += lineUnits(layout(title, textPaint(22f, colors.text, bold = true), (cardRight - cardLeft).toInt()), card = null, x = cardLeft)
            doc.subtitle?.let {
                units += lineUnits(layout(it, textPaint(12.5f, colors.muted), (cardRight - cardLeft).toInt()), card = null, x = cardLeft, extraTop = ui(4f))
            }
            units += Slice(ui(14f), droppable = true)
        }
        doc.entries.forEachIndexed { index, entry ->
            if (index > 0) units += Slice(ui(10f), droppable = true)
            units += cardUnits(index, entry)
        }
        return units
    }

    private fun cardUnits(card: Int, entry: MemoShareEntry): List<Slice> {
        val units = mutableListOf<Slice>()
        val done = entry.completedLabel != null
        val titlePaint = textPaint(17.5f, if (done) colors.muted else colors.text, bold = true).apply { isStrikeThruText = done }
        units += lineUnits(layout(entry.title, titlePaint, textWidth), card, textLeft, extraTop = ui(14f), keep = true)
        chipUnit(card, entry)?.let { units += it }
        val blocks = parseMemoBlocks(entry.body.trimEnd()).takeIf { entry.body.isNotBlank() }.orEmpty()
        blocks.forEachIndexed { index, block ->
            units += blockUnits(card, block, extraTop = if (index == 0) ui(10f) else 0, first = index == 0)
        }
        val stripe = priorityColor(entry.priority)
        units.forEach { it.stripe = stripe }
        units.first().cardStart = true
        units.last().apply {
            height += ui(14f)
            cardEnd = true
            keepWithNext = false
        }
        return units
    }

    private fun chipUnit(card: Int, entry: MemoShareEntry): Slice? {
        val chips = buildList {
            val palette = entry.notebookSeed?.let { NOTEBOOK_PALETTE[it.hashCode().mod(NOTEBOOK_PALETTE.size)] }
            add(Chip(entry.notebook, palette?.first ?: 0xFFEDEFF1.toInt(), palette?.second ?: colors.muted))
            entry.pinnedLabel?.let { add(tinted(it, colors.accent)) }
            entry.priorityLabel?.let { add(tinted(it, priorityColor(entry.priority) ?: colors.muted)) }
            entry.dueLabel?.let { add(tinted(it, if (entry.overdue) colors.error else colors.muted)) }
            entry.completedLabel?.let { add(tinted(it, colors.muted)) }
        }
        if (chips.isEmpty()) return null
        val paint = textPaint(11.5f, colors.text, bold = true)
        val chipPadH = u(8f)
        val chipHeight = u(22f)
        val gap = u(6f)
        // Wrap chips into rows; overlong labels are clipped to the row width.
        val placed = mutableListOf<Triple<Chip, RectF, String>>()
        var x = 0f
        var row = 0
        chips.forEach { chip ->
            val maxText = textWidth - chipPadH * 2
            val text = android.text.TextUtils.ellipsize(chip.text, paint, maxText, android.text.TextUtils.TruncateAt.END).toString()
            val w = paint.measureText(text) + chipPadH * 2
            if (x > 0 && x + w > textWidth) {
                x = 0f
                row++
            }
            val top = row * (chipHeight + gap)
            placed += Triple(chip, RectF(x, top, x + w, top + chipHeight), text)
            x += w + gap
        }
        val extraTop = ui(8f)
        val height = extraTop + ceil((row + 1) * chipHeight + row * gap).toInt()
        return Slice(height, card, keepWithNext = true) { canvas, top ->
            placed.forEach { (chip, rect, text) ->
                val r = RectF(rect).apply { offset(textLeft, top + extraTop) }
                canvas.drawRoundRect(r, chipHeight / 2, chipHeight / 2, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = chip.background })
                paint.color = chip.foreground
                val baseline = r.centerY() - (paint.descent() + paint.ascent()) / 2
                canvas.drawText(text, r.left + chipPadH, baseline, paint)
            }
        }
    }

    private fun tinted(text: String, color: Int) = Chip(text, ColorUtils.setAlphaComponent(color, 0x24), color)

    private fun priorityColor(priority: MemoPriority): Int? = when (priority) {
        MemoPriority.High -> colors.error
        MemoPriority.Medium -> colors.warning
        MemoPriority.Low -> colors.accent
        MemoPriority.None -> null
    }

    private fun blockUnits(card: Int, block: MemoBlock, extraTop: Int, first: Boolean): List<Slice> {
        val gap = ui(5f)
        val indentStep = u(16f)
        return when (block) {
            is MemoBlock.Heading -> {
                val size = when (block.level) { 1 -> 18f; 2 -> 16.5f; else -> 15.5f }
                val top = extraTop + if (first) 0 else ui(6f)
                lineUnits(layout(spanned(block.text), textPaint(size, colors.text, bold = true), textWidth), card, textLeft, top, gap)
            }
            is MemoBlock.Check -> {
                val indent = block.indent * indentStep
                val box = u(15f)
                val textX = textLeft + indent + box + u(9f)
                val paint = textPaint(14.5f, if (block.checked) colors.muted else colors.text)
                val text = spanned(block.text).apply {
                    if (block.checked) setSpan(StrikethroughSpan(), 0, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                }
                val layout = layout(text, paint, (textLeft + textWidth - textX).toInt().coerceAtLeast(1))
                lineUnits(layout, card, textX, extraTop, gap) { canvas, layoutTop, line ->
                    if (line == 0) drawCheckbox(canvas, textLeft + indent, layoutTop, layout, box, block.checked)
                }
            }
            is MemoBlock.Bullet -> markerUnits(card, block.indent, if (block.indent > 0) "◦" else "•", block.text, extraTop, gap)
            is MemoBlock.Numbered -> markerUnits(card, block.indent, "${block.number}.", block.text, extraTop, gap)
            is MemoBlock.Quote -> {
                val textX = textLeft + u(12f)
                val layout = layout(spanned(block.text), textPaint(14.5f, colors.muted), (textLeft + textWidth - textX).toInt())
                val bar = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = ColorUtils.setAlphaComponent(colors.accent, 0x80) }
                lineUnits(layout, card, textX, extraTop, gap) { canvas, layoutTop, line ->
                    val lineTop = layoutTop + layout.getLineTop(line)
                    val lineBottom = layoutTop + layout.getLineBottom(line)
                    canvas.drawRect(textLeft, lineTop, textLeft + u(3f), lineBottom, bar)
                }
            }
            is MemoBlock.Paragraph ->
                lineUnits(layout(spanned(block.text), textPaint(14.5f, colors.text), textWidth), card, textLeft, extraTop, gap)
            is MemoBlock.Blank -> listOf(Slice(extraTop + ui(6f), card))
            is MemoBlock.Divider -> listOf(Slice(extraTop + ui(18f), card) { canvas, top ->
                canvas.drawLine(textLeft, top + extraTop + ui(9f), textLeft + textWidth, top + extraTop + ui(9f),
                    Paint(Paint.ANTI_ALIAS_FLAG).apply { color = colors.outline; strokeWidth = u(1f) })
            })
            is MemoBlock.Image -> {
                val image = requireNotNull(images[block.ref.id]) { "note_image_missing" }
                val scale = minOf(textWidth.toFloat() / image.width, ui(480f).toFloat() / image.height)
                val w = image.width * scale; val h = image.height * scale
                listOf(Slice(extraTop + ceil(h).toInt() + gap, card) { canvas, top ->
                    var sample = 1
                    while (max(image.width, image.height) / sample > 1920) sample *= 2
                    val bitmap = requireNotNull(BitmapFactory.decodeByteArray(image.bytes, 0, image.bytes.size,
                        BitmapFactory.Options().apply { inSampleSize = sample }))
                    try {
                        val left = textLeft + (textWidth - w) / 2
                        canvas.drawBitmap(bitmap, null, RectF(left, top + extraTop, left + w, top + extraTop + h), Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
                    } finally { bitmap.recycle() }
                })
            }
        }
    }

    private fun markerUnits(card: Int, indentLevel: Int, marker: String, text: String, extraTop: Int, gap: Int): List<Slice> {
        val indent = indentLevel * u(16f)
        val markerPaint = textPaint(14.5f, colors.accent, bold = true)
        val markerWidth = max(u(16f), markerPaint.measureText(marker) + u(6f))
        val textX = textLeft + indent + markerWidth
        val layout = layout(spanned(text), textPaint(14.5f, colors.text), (textLeft + textWidth - textX).toInt().coerceAtLeast(1))
        return lineUnits(layout, card, textX, extraTop, gap) { canvas, layoutTop, line ->
            if (line == 0) canvas.drawText(marker, textLeft + indent, layoutTop + layout.getLineBaseline(0), markerPaint)
        }
    }

    private fun drawCheckbox(canvas: Canvas, x: Float, layoutTop: Float, layout: StaticLayout, box: Float, checked: Boolean) {
        val centerY = layoutTop + layout.getLineBaseline(0) + (layout.paint.ascent() + layout.paint.descent()) / 2
        val rect = RectF(x, centerY - box / 2, x + box, centerY + box / 2)
        val radius = u(4f)
        if (checked) {
            canvas.drawRoundRect(rect, radius, radius, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = colors.accent })
            val check = Path().apply {
                moveTo(rect.left + box * 0.24f, rect.top + box * 0.52f)
                lineTo(rect.left + box * 0.43f, rect.top + box * 0.7f)
                lineTo(rect.left + box * 0.77f, rect.top + box * 0.32f)
            }
            canvas.drawPath(check, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = u(1.8f)
                strokeCap = Paint.Cap.ROUND
                strokeJoin = Paint.Join.ROUND
                color = 0xFFFFFFFF.toInt()
            })
        } else {
            val stroke = u(1.5f)
            rect.inset(stroke / 2, stroke / 2)
            canvas.drawRoundRect(rect, radius, radius, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = stroke
                color = ColorUtils.setAlphaComponent(colors.text, 0x73)
            })
        }
    }

    /** One unit per laid-out line; [decorate] draws markers relative to the layout origin. */
    private fun lineUnits(
        layout: StaticLayout,
        card: Int?,
        x: Float,
        extraTop: Int = 0,
        extraBottom: Int = 0,
        keep: Boolean = false,
        decorate: ((Canvas, Float, Int) -> kotlin.Unit)? = null,
    ): List<Slice> = (0 until layout.lineCount).map { line ->
        val lineTop = layout.getLineTop(line)
        val lineBottom = layout.getLineBottom(line)
        val top = if (line == 0) extraTop else 0
        val bottom = if (line == layout.lineCount - 1) extraBottom else 0
        Slice(top + (lineBottom - lineTop) + bottom, card, keepWithNext = keep) { canvas, y ->
            val layoutTop = y + top - lineTop
            canvas.save()
            canvas.clipRect(0f, y + top, widthPx.toFloat(), y + top + (lineBottom - lineTop))
            canvas.translate(x, layoutTop)
            layout.draw(canvas)
            canvas.restore()
            decorate?.invoke(canvas, layoutTop, line)
        }
    }

    private fun spanned(text: String): SpannableStringBuilder = SpannableStringBuilder().apply {
        memoInlineRuns(text).forEach { run ->
            val start = length
            append(run.text)
            if (run.bold) setSpan(StyleSpan(Typeface.BOLD), start, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            if (run.strike) setSpan(StrikethroughSpan(), start, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            if (run.code) {
                setSpan(TypefaceSpan("monospace"), start, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                setSpan(BackgroundColorSpan(colors.codeBackground), start, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
        }
    }

    private fun layout(text: CharSequence, paint: TextPaint, width: Int): StaticLayout =
        StaticLayout.Builder.obtain(text, 0, text.length, paint, width.coerceAtLeast(1))
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setLineSpacing(0f, 1.3f)
            .setIncludePad(false)
            .build()

    private fun textPaint(sizeU: Float, color: Int, bold: Boolean = false) = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = u(sizeU)
        this.color = color
        typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
    }
}
