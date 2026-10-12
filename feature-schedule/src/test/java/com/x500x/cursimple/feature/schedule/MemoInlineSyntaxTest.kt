package com.x500x.cursimple.feature.schedule

import androidx.compose.ui.graphics.Color
import com.x500x.cursimple.core.kernel.model.MemoNote
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MemoInlineSyntaxTest {

    private val palette = MemoInlinePalette(Color.Gray, Color.LightGray, Color.Blue)

    private fun rendered(raw: String, hide: Boolean = true, prefix: String = "") =
        renderMemoInline(raw, palette, hideMarkers = hide, prefix = prefix)

    @Test
    fun parse_recognizesInlineKinds() {
        val spans = parseMemoInline("**粗** *斜* ~~删~~ `码` [链接](https://a.cn) https://b.cn/x.")
        assertEquals(
            listOf(MemoInlineKind.Bold, MemoInlineKind.Italic, MemoInlineKind.Strike, MemoInlineKind.Code, MemoInlineKind.Link, MemoInlineKind.Url),
            spans.map { it.kind },
        )
        assertEquals("https://a.cn", spans[4].url)
        assertEquals("https://b.cn/x", spans[5].url)
    }

    @Test
    fun parse_ignoresLooseAsterisksAndSnakeCase() {
        assertTrue(parseMemoInline("2 * 3 * 4").isEmpty())
        assertTrue(parseMemoInline("snake_case_name").isEmpty())
        assertTrue(parseMemoInline("a****b").isEmpty())
    }

    @Test
    fun parse_nestsInsideBoldAndLinks() {
        val kinds = parseMemoInline("**重点 *很* 重要** [~~旧~~](https://x.cn)").map { it.kind }
        assertEquals(listOf(MemoInlineKind.Bold, MemoInlineKind.Italic, MemoInlineKind.Link, MemoInlineKind.Strike), kinds)
    }

    @Test
    fun render_hidesMarkersOnlyWhenAsked() {
        assertEquals("重点内容", rendered("**重点**内容").text.text)
        assertEquals("看链接", rendered("看[链接](https://x.cn)").text.text)
        assertEquals("**重点**内容", rendered("**重点**内容", hide = false).text.text)
        assertEquals("## 标题", rendered("标题", hide = false, prefix = "## ").text.text)
    }

    @Test
    fun mapping_isMonotonicAndInRangeForEveryOffset() {
        val samples = listOf("", "a", "**重点**内容", "\u200B**a** `b` [c](https://d.e) ~~f~~ *g*", "****", "中文*斜体*结尾")
        for (raw in samples) for (hide in listOf(true, false)) for (prefix in listOf("", "# ")) {
            val render = rendered(raw, hide, prefix)
            val mapping = render.mapping
            val transformedLength = render.text.length
            assertEquals(transformedLength, mapping.transformedLength)
            var last = -1
            for (o in -2..raw.length + 2) {
                val t = mapping.originalToTransformed(o)
                assertTrue("o2t($o) of '$raw'", t in 0..transformedLength)
                if (o in 0..raw.length) {
                    assertTrue(t >= last)
                    last = t
                }
            }
            last = -1
            for (t in -2..transformedLength + 2) {
                val o = mapping.transformedToOriginal(t)
                assertTrue("t2o($t) of '$raw'", o in 0..raw.length)
                if (t in 0..transformedLength) {
                    assertTrue(o >= last)
                    last = o
                    // Round trip lands on the same visible position (the prefix is not editable).
                    assertEquals(maxOf(t, prefix.length), mapping.originalToTransformed(o))
                }
            }
        }
    }

    @Test
    fun mapping_placesCaretAfterHiddenMarkers() {
        val mapping = rendered("a**b**c").mapping
        // Visible "abc": caret before "b" maps after the opening marker; end maps to original end.
        assertEquals(3, mapping.transformedToOriginal(1))
        assertEquals(7, mapping.transformedToOriginal(3))
        assertEquals(1, mapping.originalToTransformed(2))
        assertEquals(2, mapping.originalToTransformed(5))
        val shifted = rendered("题", hide = false, prefix = "# ").mapping
        assertEquals(2, shifted.originalToTransformed(0))
        assertEquals(0, shifted.transformedToOriginal(1))
        assertEquals(1, shifted.transformedToOriginal(3))
    }

    @Test
    fun draftChanged_ignoresTrailingWhitespaceOnly() {
        val note = MemoNote(id = "n", title = "标题", body = "正文\n")
        val draft = MemoDraft.of(note)
        assertFalse(memoDraftChanged(note, draft, "正文"))
        assertFalse(memoDraftChanged(note, draft.copy(title = "标题 "), "正文\n\n"))
        assertTrue(memoDraftChanged(note, draft, "正文改了"))
        assertTrue(memoDraftChanged(note, draft.copy(pinned = true), "正文"))
    }
}
