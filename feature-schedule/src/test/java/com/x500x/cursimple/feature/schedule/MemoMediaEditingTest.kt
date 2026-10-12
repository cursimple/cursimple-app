package com.x500x.cursimple.feature.schedule

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import com.x500x.cursimple.core.kernel.model.MemoImageRef
import org.junit.Assert.*
import org.junit.Test

class MemoMediaEditingTest {
    private val image = MemoImageRef.markdown("a".repeat(64), "图片")
    @Test fun `inserting an image at the cursor preserves text on both sides and supports history`() {
        val editor = MemoBlockEditorState("BeforeAfter", clock = { 0L })
        val id = editor.lines.first().id
        editor.onFocusChanged(id, true)
        editor.onFieldChange(id, TextFieldValue("\u200BBeforeAfter", TextRange(7)))
        editor.insertBlock(image)
        assertEquals("Before\n$image\nAfter", editor.body)
        editor.undo(); assertEquals("BeforeAfter", editor.body)
        editor.redo(); assertEquals("Before\n$image\nAfter", editor.body)
        val block = editor.lines.single { it.kind == MemoLineKind.Image }
        editor.removeBlock(block.id)
        assertEquals("Before\nAfter", editor.body)
        editor.undo(); assertTrue(editor.body.contains(image))
    }
    @Test fun `backspace after media never merges text into the image or divider`() {
        for (body in listOf("$image\nFollowing", "---\nFollowing")) {
            var id = 0L
            val lines = parseMemoLines(body) { ++id }
            assertNull(memoBackspaceAtStart(lines, 1))
            assertEquals(body, serializeMemoLines(lines))
        }
    }
    @Test fun `image-only notes remain content and render as images`() {
        assertTrue(MemoRichTextState(image).hasContent)
        assertTrue(parseMemoBlocks(image).single() is MemoBlock.Image)
        assertTrue(parseMemoBlocks("---").single() is MemoBlock.Divider)
    }
    @Test fun `tools act on an empty text line after a saved image without corrupting it`() {
        val editor = MemoBlockEditorState(image, clock = { 0L })
        editor.applyKind(MemoLineKind.Check)
        assertEquals("$image\n- [ ] ", editor.body)
        editor.undo(); assertEquals(image, editor.body)
    }
    @Test fun `links replace selected text and history restores the original selection text`() {
        val editor = MemoBlockEditorState("Read notes today", clock = { 0L })
        val id = editor.lines.first().id
        editor.onFocusChanged(id, true)
        editor.onFieldChange(id, TextFieldValue("\u200BRead notes today", TextRange(6, 11)))
        editor.insertLink("notes", "https://example.com")
        assertEquals("Read [notes](https://example.com) today", editor.body)
        editor.undo(); assertEquals("Read notes today", editor.body)
    }
    @Test fun `clear formatting preserves readable text and safe links`() {
        val editor = MemoBlockEditorState("# **Important** [link](https://example.com)", clock = { 0L })
        editor.clearFormatting()
        assertEquals("Important [link](https://example.com)", editor.body)
        editor.undo(); assertEquals("# **Important** [link](https://example.com)", editor.body)
        assertTrue(memoLinkIsValid("https://example.com/notes"))
        assertFalse(memoLinkIsValid("javascript:alert(1)"))
        assertFalse(memoLinkIsValid("file:///secret"))
    }
}
