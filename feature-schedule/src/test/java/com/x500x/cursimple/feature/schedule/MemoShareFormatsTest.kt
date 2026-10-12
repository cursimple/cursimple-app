package com.x500x.cursimple.feature.schedule

import com.x500x.cursimple.core.kernel.model.MemoPriority
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipInputStream
import javax.xml.parsers.DocumentBuilderFactory

class MemoShareFormatsTest {
    @Test fun `Word and Markdown embed portable images with consistent relationships`() {
        val bytes = kotlin.io.encoding.Base64.Default.decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR4nGP4z8DwHwAFAAH/iZk9HQAAAABJRU5ErkJggg==")
        val id = com.x500x.cursimple.core.data.memo.MemoTransferImage.imageId(bytes)
        val image = MemoShareEmbeddedImage(id, bytes, 1, 1, "image/png")
        val body = com.x500x.cursimple.core.kernel.model.MemoImageRef.markdown(id, "A & B") + "\n---\nText"
        val doc = MemoShareDocument(null, null, listOf(MemoShareEntry("With image", body, "Course")), mapOf(id to image))
        val output = ByteArrayOutputStream().also { writeMemoDocx(doc, 0xFF3FA277.toInt(), it) }.toByteArray()
        val parts = mutableMapOf<String, ByteArray>()
        ZipInputStream(ByteArrayInputStream(output)).use { zip ->
            generateSequence { zip.nextEntry }.forEach { parts[it.name] = zip.readBytes() }
        }
        assertTrue(parts.containsKey("word/media/$id.png"))
        org.junit.Assert.assertArrayEquals(bytes, parts.getValue("word/media/$id.png"))
        assertTrue(parts.getValue("word/_rels/document.xml.rels").toString(Charsets.UTF_8).contains("Target=\"media/$id.png\""))
        assertTrue(parts.getValue("word/document.xml").toString(Charsets.UTF_8).contains("r:embed=\"rImg$id\""))
        val factory = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
        parts.filterKeys { it.endsWith(".xml") || it.endsWith(".rels") }.values.forEach {
            factory.newDocumentBuilder().parse(ByteArrayInputStream(it))
        }
        val markdown = memoShareMarkdown(doc)
        assertTrue(markdown.contains("![A & B](images/$id.png)"))
        assertFalse(markdown.contains("cursimple-image:"))
        assertTrue(memoSharePlainText(doc).contains("[A & B]"))
    }
    private val lab = MemoShareEntry(
        title = "Lab <3>",
        body = "# Steps\n- [ ] **Upload** report\n- [x] Draft\n- item\n1. first\n> quote\nplain `code` & ~~old~~",
        notebook = "Data structures",
        notebookSeed = "Data structures",
        priority = MemoPriority.High,
        priorityLabel = "High priority",
        dueLabel = "Due Oct 9 12:00",
        pinnedLabel = "Pinned",
    )
    private val other = MemoShareEntry(title = "Shopping", body = "milk", notebook = "Other", completedLabel = "Done")
    private val all = MemoShareDocument(title = "Notes · All", subtitle = "Exported now · 2 notes", entries = listOf(lab, other))
    private val single = MemoShareDocument(title = null, subtitle = null, entries = listOf(lab))

    @Test fun `markdown nests body headings under note titles`() {
        val md = memoShareMarkdown(all)
        assertTrue(md.startsWith("# Notes · All\n\n> Exported now · 2 notes\n"))
        assertTrue(md.contains("## Lab <3>\n\n*Data structures · High priority · Due Oct 9 12:00 · Pinned*\n"))
        assertTrue(md.contains("### Steps\n- [ ] **Upload** report\n- [x] Draft\n"))
        assertTrue(md.contains("\n---\n\n## Shopping\n"))
        assertTrue(memoShareMarkdown(single).startsWith("# Lab <3>\n"))
        assertTrue(memoShareMarkdown(single).contains("\n## Steps\n"))
    }

    @Test fun `plain text strips inline markers and renders checkboxes`() {
        val text = memoSharePlainText(all)
        assertTrue(text.contains("【Lab <3>】"))
        assertTrue(text.contains("☐ Upload report"))
        assertTrue(text.contains("☑ Draft"))
        assertTrue(text.contains("• item"))
        assertTrue(text.contains("1. first"))
        assertTrue(text.contains("plain code & old"))
        assertFalse(text.contains("**"))
        assertTrue(text.contains("Other · Done"))
    }

    @Test fun `docx package holds well formed parts with headings and lists`() {
        val bytes = ByteArrayOutputStream().also { writeMemoDocx(all, 0xFF3FA277.toInt(), it) }.toByteArray()
        val parts = mutableMapOf<String, String>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            generateSequence { zip.nextEntry }.forEach { parts[it.name] = zip.readBytes().toString(Charsets.UTF_8) }
        }
        assertEquals(
            setOf("[Content_Types].xml", "_rels/.rels", "word/_rels/document.xml.rels", "word/styles.xml", "word/numbering.xml", "word/document.xml"),
            parts.keys,
        )
        val factory = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
        parts.values.forEach { factory.newDocumentBuilder().parse(ByteArrayInputStream(it.toByteArray())) }
        val document = parts.getValue("word/document.xml")
        assertTrue(document.contains("<w:pStyle w:val=\"Title\"/>"))
        assertTrue(document.contains("<w:pStyle w:val=\"Heading1\"/></w:pPr><w:r><w:t xml:space=\"preserve\">Lab &lt;3&gt;"))
        assertTrue(document.contains("<w:pStyle w:val=\"Heading2\"/>"))
        assertTrue(document.contains("<w:numId w:val=\"1\"/>"))
        assertTrue(document.contains("<w:b/></w:rPr><w:t xml:space=\"preserve\">Upload</w:t>"))
        assertTrue(document.contains("☑ "))
        assertTrue(document.contains("&amp;"))
    }

    @Test fun `xml escape drops forbidden control characters but keeps emoji`() {
        assertEquals("a&lt;b😀c", xmlEscape("a<b\u0001😀c"))
    }

    @Test fun `file stems remove path separators`() {
        assertEquals("a_b_c", MemoShareExporter.fileStem("a/b:c"))
        assertEquals("notes", MemoShareExporter.fileStem("  ...  "))
    }
}
