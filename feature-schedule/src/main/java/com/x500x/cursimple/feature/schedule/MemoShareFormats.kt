package com.x500x.cursimple.feature.schedule

import com.x500x.cursimple.core.kernel.model.MemoPriority
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

internal enum class MemoShareFormat(val extension: String, val mime: String) {
    ShareCode("", "text/plain"),
    QrCode("png", "image/png"),
    CurSimple("csnotes", com.x500x.cursimple.core.data.memo.MemoTransferCodec.MIME),
    Image("png", "image/png"),
    Word("docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document"),
    Markdown("md", "text/markdown"),
    PlainText("txt", "text/plain"),
}

/** Display-ready note; labels are resolved by the caller so formats stay resource-free. */
internal data class MemoShareEntry(
    val title: String,
    val body: String,
    val notebook: String,
    val notebookSeed: String? = null,
    val priority: MemoPriority = MemoPriority.None,
    val priorityLabel: String? = null,
    val dueLabel: String? = null,
    val overdue: Boolean = false,
    val pinnedLabel: String? = null,
    val completedLabel: String? = null,
) {
    val metaLabels: List<String> get() = listOfNotNull(notebook, priorityLabel, dueLabel, pinnedLabel, completedLabel)
}

/** A null [title] means a single-note export whose note title becomes the document title. */
internal data class MemoShareDocument(
    val title: String?,
    val subtitle: String?,
    val entries: List<MemoShareEntry>,
    val images: Map<String, MemoShareEmbeddedImage> = emptyMap(),
) {
    val single: Boolean get() = title == null
}

internal data class MemoShareEmbeddedImage(val id: String, val bytes: ByteArray, val width: Int, val height: Int, val mime: String) {
    val extension: String get() = if (mime == "image/png") "png" else "jpg"
    val path: String get() = "images/$id.$extension"
}

internal data class MemoInlineRun(
    val text: String,
    val bold: Boolean = false,
    val strike: Boolean = false,
    val code: Boolean = false,
)

// Same inline subset that the in-app note renderer understands.
private val SHARE_INLINE = Regex("""\*\*(.+?)\*\*|~~(.+?)~~|`([^`]+)`""")
private val SHARE_HEADING = Regex("""^(#{1,6})(\s+.*)$""")

internal fun memoInlineRuns(text: String): List<MemoInlineRun> {
    val runs = mutableListOf<MemoInlineRun>()
    var cursor = 0
    SHARE_INLINE.findAll(text).forEach { match ->
        if (match.range.first > cursor) runs += MemoInlineRun(text.substring(cursor, match.range.first))
        val (bold, strike, code) = match.destructured
        runs += when {
            bold.isNotEmpty() -> MemoInlineRun(bold, bold = true)
            strike.isNotEmpty() -> MemoInlineRun(strike, strike = true)
            else -> MemoInlineRun(code, code = true)
        }
        cursor = match.range.last + 1
    }
    if (cursor < text.length) runs += MemoInlineRun(text.substring(cursor))
    return runs
}

internal fun memoStripInline(text: String): String = memoInlineRuns(text).joinToString("") { it.text }

// ---- Markdown ----

internal fun memoShareMarkdown(doc: MemoShareDocument): String = buildString {
    val noteLevel = if (doc.single) 1 else 2
    doc.title?.let { title ->
        append("# ").append(title).append("\n\n")
        doc.subtitle?.let { append("> ").append(it).append("\n\n") }
    }
    doc.entries.forEachIndexed { index, entry ->
        if (index > 0) append("\n---\n\n")
        append("#".repeat(noteLevel)).append(' ').append(entry.title).append('\n')
        if (entry.metaLabels.isNotEmpty()) append('\n').append('*').append(entry.metaLabels.joinToString(" · ")).append("*\n")
        val body = entry.body.trim('\n').trimEnd()
        if (body.isNotBlank()) {
            append('\n')
            body.split('\n').forEach { line ->
                val image = com.x500x.cursimple.core.kernel.model.MemoImageRef.parse(line)
                val heading = SHARE_HEADING.matchEntire(line)
                if (image != null && doc.images.containsKey(image.id)) {
                    append("![").append(image.alt).append("](").append(doc.images.getValue(image.id).path).append(')')
                } else if (heading != null) {
                    // Nest body headings under the note title.
                    append("#".repeat((heading.groupValues[1].length + noteLevel).coerceAtMost(6))).append(heading.groupValues[2])
                } else {
                    append(line)
                }
                append('\n')
            }
        }
    }
}

// ---- Plain text ----

internal fun memoSharePlainText(doc: MemoShareDocument): String = buildString {
    doc.title?.let { title ->
        append(title).append('\n')
        doc.subtitle?.let { append(it).append('\n') }
        append('\n')
    }
    doc.entries.forEachIndexed { index, entry ->
        if (index > 0) append("\n──────────\n\n")
        append(if (doc.single) entry.title else "【${entry.title}】").append('\n')
        if (entry.metaLabels.isNotEmpty()) append(entry.metaLabels.joinToString(" · ")).append('\n')
        val blocks = parseMemoBlocks(entry.body.trimEnd())
        if (entry.body.isNotBlank() && blocks.isNotEmpty()) {
            append('\n')
            blocks.forEach { block -> append(plainLine(block)).append('\n') }
        }
    }
}.trimEnd() + "\n"

private fun plainLine(block: MemoBlock): String = when (block) {
    is MemoBlock.Heading -> memoStripInline(block.text)
    is MemoBlock.Check -> "  ".repeat(block.indent) + (if (block.checked) "☑ " else "☐ ") + memoStripInline(block.text)
    is MemoBlock.Bullet -> "  ".repeat(block.indent) + (if (block.indent > 0) "◦ " else "• ") + memoStripInline(block.text)
    is MemoBlock.Numbered -> "  ".repeat(block.indent) + "${block.number}. " + memoStripInline(block.text)
    is MemoBlock.Quote -> "│ " + memoStripInline(block.text)
    is MemoBlock.Paragraph -> memoStripInline(block.text)
    is MemoBlock.Blank -> ""
    is MemoBlock.Image -> "[${block.ref.alt.ifBlank { "Image" }}]"
    is MemoBlock.Divider -> "──────────"
}

// ---- Word (.docx) ----

private const val W_NS = "http://schemas.openxmlformats.org/wordprocessingml/2006/main"
private const val MUTED_HEX = "6B7280"

/** Write a minimal WordprocessingML package; headings use built-in styles so navigation works. */
internal fun writeMemoDocx(doc: MemoShareDocument, accentArgb: Int, out: OutputStream) {
    val accent = "%06X".format(accentArgb and 0xFFFFFF)
    ZipOutputStream(out).use { zip ->
        fun part(name: String, content: String) {
            zip.putNextEntry(ZipEntry(name))
            zip.write(content.toByteArray(Charsets.UTF_8))
            zip.closeEntry()
        }
        part("[Content_Types].xml", if (doc.images.isEmpty()) DOCX_CONTENT_TYPES else DOCX_CONTENT_TYPES.replace("</Types>",
            "<Default Extension=\"png\" ContentType=\"image/png\"/><Default Extension=\"jpg\" ContentType=\"image/jpeg\"/></Types>"))
        part("_rels/.rels", DOCX_ROOT_RELS)
        part("word/_rels/document.xml.rels", DOCX_DOCUMENT_RELS.replace("</Relationships>",
            doc.images.values.joinToString("") { "<Relationship Id=\"rImg${it.id}\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/image\" Target=\"media/${it.id}.${it.extension}\"/>" } + "</Relationships>"))
        part("word/styles.xml", docxStyles(accent))
        part("word/numbering.xml", DOCX_NUMBERING)
        part("word/document.xml", memoDocxDocumentXml(doc, accentArgb))
        doc.images.values.forEach { image ->
            zip.putNextEntry(ZipEntry("word/media/${image.id}.${image.extension}")); zip.write(image.bytes); zip.closeEntry()
        }
    }
}

internal fun memoDocxDocumentXml(doc: MemoShareDocument, accentArgb: Int): String = buildString {
    val accent = "%06X".format(accentArgb and 0xFFFFFF)
    append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
    append("""<w:document xmlns:w="$W_NS" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships" xmlns:wp="http://schemas.openxmlformats.org/drawingml/2006/wordprocessingDrawing" xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main" xmlns:pic="http://schemas.openxmlformats.org/drawingml/2006/picture"><w:body>""")
    doc.title?.let { title ->
        paragraph(style = "Title", runs = listOf(MemoInlineRun(title)))
        doc.subtitle?.let { paragraph(style = "Subtitle", runs = listOf(MemoInlineRun(it))) }
    }
    val headingOffset = if (doc.single) 0 else 1
    var imageCounter = 0
    doc.entries.forEach { entry ->
        paragraph(style = if (doc.single) "Title" else "Heading1", runs = listOf(MemoInlineRun(entry.title)))
        if (entry.metaLabels.isNotEmpty()) {
            paragraph(runs = listOf(MemoInlineRun(entry.metaLabels.joinToString(" · "))), color = MUTED_HEX, size = 18)
        }
        parseMemoBlocks(entry.body.trimEnd()).forEach { block ->
            when (block) {
                is MemoBlock.Heading -> paragraph(
                    style = "Heading${(block.level + headingOffset).coerceIn(1, 4)}",
                    runs = memoInlineRuns(block.text),
                )
                is MemoBlock.Check -> paragraph(
                    props = hanging(block.indent),
                    runs = listOf(MemoInlineRun(if (block.checked) "☑ " else "☐ ")) + memoInlineRuns(block.text),
                    color = MUTED_HEX.takeIf { block.checked },
                    strike = block.checked,
                    prefixRuns = 1,
                )
                is MemoBlock.Bullet -> paragraph(
                    style = "ListParagraph",
                    props = "<w:numPr><w:ilvl w:val=\"${block.indent.coerceIn(0, 2)}\"/><w:numId w:val=\"1\"/></w:numPr>",
                    runs = memoInlineRuns(block.text),
                )
                is MemoBlock.Numbered -> paragraph(
                    props = hanging(block.indent),
                    runs = listOf(MemoInlineRun("${block.number}. ")) + memoInlineRuns(block.text),
                )
                is MemoBlock.Quote -> paragraph(
                    props = "<w:pBdr><w:left w:val=\"single\" w:sz=\"18\" w:space=\"8\" w:color=\"$accent\"/></w:pBdr><w:ind w:left=\"240\"/>",
                    runs = memoInlineRuns(block.text),
                    color = MUTED_HEX,
                )
                is MemoBlock.Paragraph -> paragraph(runs = memoInlineRuns(block.text))
                is MemoBlock.Blank -> Unit
                is MemoBlock.Divider -> paragraph(runs = emptyList(), props = "<w:pBdr><w:bottom w:val=\"single\" w:sz=\"6\" w:color=\"$MUTED_HEX\"/></w:pBdr>")
                is MemoBlock.Image -> {
                    val image = requireNotNull(doc.images[block.ref.id]) { "note_image_missing" }
                    val scale = minOf(5.5 * 914400 / image.width, 6.5 * 914400 / image.height, 9525.0)
                    val cx = (image.width * scale).toLong(); val cy = (image.height * scale).toLong()
                    val id = ++imageCounter
                    append("""<w:p><w:r><w:drawing><wp:inline distT="0" distB="0" distL="0" distR="0"><wp:extent cx="$cx" cy="$cy"/><wp:docPr id="$id" name="Image $id" descr="${xmlEscape(block.ref.alt)}"/><a:graphic><a:graphicData uri="http://schemas.openxmlformats.org/drawingml/2006/picture"><pic:pic><pic:nvPicPr><pic:cNvPr id="0" name="Image $id"/><pic:cNvPicPr/></pic:nvPicPr><pic:blipFill><a:blip r:embed="rImg${image.id}"/><a:stretch><a:fillRect/></a:stretch></pic:blipFill><pic:spPr><a:xfrm><a:off x="0" y="0"/><a:ext cx="$cx" cy="$cy"/></a:xfrm><a:prstGeom prst="rect"><a:avLst/></a:prstGeom></pic:spPr></pic:pic></a:graphicData></a:graphic></wp:inline></w:drawing></w:r></w:p>""")
                }
            }
        }
    }
    append("""<w:sectPr><w:pgSz w:w="11906" w:h="16838"/>""")
    append("""<w:pgMar w:top="1440" w:right="1304" w:bottom="1440" w:left="1304" w:header="708" w:footer="708" w:gutter="0"/></w:sectPr>""")
    append("</w:body></w:document>")
}

private fun hanging(indent: Int): String = "<w:ind w:left=\"${360 * indent + 360}\" w:hanging=\"360\"/>"

/** [prefixRuns] leading runs skip [color] and [strike] so checkbox glyphs stay plain. Emit rPr in schema order. */
private fun StringBuilder.paragraph(
    runs: List<MemoInlineRun>,
    style: String? = null,
    props: String = "",
    color: String? = null,
    size: Int? = null,
    strike: Boolean = false,
    prefixRuns: Int = 0,
) {
    append("<w:p>")
    if (style != null || props.isNotEmpty()) {
        append("<w:pPr>")
        style?.let { append("<w:pStyle w:val=\"").append(it).append("\"/>") }
        append(props)
        append("</w:pPr>")
    }
    runs.forEachIndexed { index, run ->
        if (run.text.isEmpty()) return@forEachIndexed
        val styled = index >= prefixRuns
        val rPr = buildString {
            if (run.code) append("<w:rFonts w:ascii=\"Consolas\" w:hAnsi=\"Consolas\"/>")
            if (run.bold) append("<w:b/>")
            if (run.strike || (strike && styled)) append("<w:strike/>")
            if (color != null && styled) append("<w:color w:val=\"").append(color).append("\"/>")
            if (size != null) append("<w:sz w:val=\"").append(size).append("\"/>")
            if (run.code) append("<w:shd w:val=\"clear\" w:color=\"auto\" w:fill=\"EEF0F2\"/>")
        }
        append("<w:r>")
        if (rPr.isNotEmpty()) append("<w:rPr>").append(rPr).append("</w:rPr>")
        append("<w:t xml:space=\"preserve\">").append(xmlEscape(run.text)).append("</w:t></w:r>")
    }
    append("</w:p>")
}

/** Escape markup and drop code points that XML 1.0 forbids. */
internal fun xmlEscape(text: String): String = buildString(text.length) {
    var i = 0
    while (i < text.length) {
        val cp = text.codePointAt(i)
        i += Character.charCount(cp)
        when {
            cp == '&'.code -> append("&amp;")
            cp == '<'.code -> append("&lt;")
            cp == '>'.code -> append("&gt;")
            cp == '"'.code -> append("&quot;")
            cp == 0x9 || cp == 0xA || cp == 0xD || cp in 0x20..0xD7FF || cp in 0xE000..0xFFFD || cp in 0x10000..0x10FFFF ->
                appendCodePoint(cp)
        }
    }
}

private const val DOCX_CONTENT_TYPES = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Default Extension="xml" ContentType="application/xml"/><Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/><Override PartName="/word/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.styles+xml"/><Override PartName="/word/numbering.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.numbering+xml"/></Types>"""

private const val DOCX_ROOT_RELS = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/></Relationships>"""

private const val DOCX_DOCUMENT_RELS = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/><Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/numbering" Target="numbering.xml"/></Relationships>"""

private const val DOCX_NUMBERING = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><w:numbering xmlns:w="$W_NS"><w:abstractNum w:abstractNumId="0"><w:multiLevelType w:val="hybridMultilevel"/><w:lvl w:ilvl="0"><w:start w:val="1"/><w:numFmt w:val="bullet"/><w:lvlText w:val="•"/><w:lvlJc w:val="left"/><w:pPr><w:ind w:left="360" w:hanging="360"/></w:pPr></w:lvl><w:lvl w:ilvl="1"><w:start w:val="1"/><w:numFmt w:val="bullet"/><w:lvlText w:val="◦"/><w:lvlJc w:val="left"/><w:pPr><w:ind w:left="720" w:hanging="360"/></w:pPr></w:lvl><w:lvl w:ilvl="2"><w:start w:val="1"/><w:numFmt w:val="bullet"/><w:lvlText w:val="▪"/><w:lvlJc w:val="left"/><w:pPr><w:ind w:left="1080" w:hanging="360"/></w:pPr></w:lvl></w:abstractNum><w:num w:numId="1"><w:abstractNumId w:val="0"/></w:num></w:numbering>"""

private fun docxStyles(accent: String): String =
    """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><w:styles xmlns:w="$W_NS">""" +
        """<w:docDefaults><w:rPrDefault><w:rPr><w:rFonts w:ascii="Calibri" w:hAnsi="Calibri" w:eastAsia="Microsoft YaHei" w:cs="Calibri"/>""" +
        """<w:sz w:val="22"/><w:szCs w:val="22"/><w:lang w:val="en-US" w:eastAsia="zh-CN"/></w:rPr></w:rPrDefault>""" +
        """<w:pPrDefault><w:pPr><w:spacing w:after="80" w:line="320" w:lineRule="auto"/></w:pPr></w:pPrDefault></w:docDefaults>""" +
        """<w:style w:type="paragraph" w:default="1" w:styleId="Normal"><w:name w:val="Normal"/><w:qFormat/></w:style>""" +
        """<w:style w:type="paragraph" w:styleId="Title"><w:name w:val="Title"/><w:basedOn w:val="Normal"/><w:next w:val="Normal"/><w:qFormat/>""" +
        """<w:pPr><w:spacing w:before="120" w:after="120"/></w:pPr><w:rPr><w:b/><w:color w:val="$accent"/><w:sz w:val="40"/><w:szCs w:val="40"/></w:rPr></w:style>""" +
        """<w:style w:type="paragraph" w:styleId="Subtitle"><w:name w:val="Subtitle"/><w:basedOn w:val="Normal"/><w:next w:val="Normal"/><w:qFormat/>""" +
        """<w:pPr><w:spacing w:after="240"/></w:pPr><w:rPr><w:color w:val="$MUTED_HEX"/><w:sz w:val="20"/></w:rPr></w:style>""" +
        headingStyle(1, 32, accent, before = 360) +
        headingStyle(2, 28, "1F2328", before = 240) +
        headingStyle(3, 24, "1F2328", before = 200) +
        headingStyle(4, 22, "1F2328", before = 160) +
        """<w:style w:type="paragraph" w:styleId="ListParagraph"><w:name w:val="List Paragraph"/><w:basedOn w:val="Normal"/><w:qFormat/></w:style>""" +
        "</w:styles>"

private fun headingStyle(level: Int, size: Int, color: String, before: Int): String =
    """<w:style w:type="paragraph" w:styleId="Heading$level"><w:name w:val="heading $level"/><w:basedOn w:val="Normal"/><w:next w:val="Normal"/><w:qFormat/>""" +
        """<w:pPr><w:keepNext/><w:spacing w:before="$before" w:after="80"/><w:outlineLvl w:val="${level - 1}"/></w:pPr>""" +
        """<w:rPr><w:b/><w:color w:val="$color"/><w:sz w:val="$size"/><w:szCs w:val="$size"/></w:rPr></w:style>"""
