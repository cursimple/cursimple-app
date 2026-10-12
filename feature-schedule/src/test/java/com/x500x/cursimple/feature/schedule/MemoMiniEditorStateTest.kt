package com.x500x.cursimple.feature.schedule

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import org.junit.Assert.*
import org.junit.Test

class MemoMiniEditorStateTest {
    @Test fun checklistIsEditableAndUndoRestoresItsStateWithoutDroppingOtherBlocks() {
        val source = "Intro\n- [ ] Report\n- [x] Review\n1. First\n2. Second"
        val state = MemoBlockEditorState(source, clock = { 0L })
        state.toggleChecked(state.lines[1].id)
        assertTrue(state.body.contains("- [x] Report"))
        assertTrue(state.body.contains("- [x] Review"))
        assertTrue(state.body.contains("2. Second"))
        state.undo()
        assertFalse(state.lines[1].checked)
        assertTrue(state.lines[2].checked)
        state.redo()
        assertTrue(state.lines[1].checked)
    }

    @Test fun nativeListFormattingRoundTripsIntoTheSavedBodyAndKeepsEnterPlain() {
        val state = MemoBlockEditorState("Report", clock = { 0L })
        val id = state.lines.first().id
        state.onFocusChanged(id, true)
        state.applyKind(MemoLineKind.Numbered)
        assertEquals("1. Report", state.body)
        val value = "\u200BReport\nNotes"
        state.onFieldChange(id, TextFieldValue(value, TextRange(value.length)))
        assertEquals(listOf(MemoLineKind.Numbered, MemoLineKind.Plain), state.lines.map { it.kind })
        assertEquals("1. Report\nNotes", state.body)
    }

    @Test fun inlineFormattingAndTypingUseTheSameModelAsTheFullEditor() {
        val state = MemoBlockEditorState("Report", clock = { 0L })
        val id = state.lines.first().id
        state.onFocusChanged(id, true)
        state.onFieldChange(id, TextFieldValue("\u200BReport", TextRange(1, 7)))
        state.wrap("**")
        assertEquals("**Report**", state.body)
        state.undo()
        assertEquals("Report", state.body)
    }

    @Test fun emptyChecklistMarkersAreNotConsideredContentAndUntouchedMarkdownStaysIntact() {
        assertFalse(MemoRichTextState("- [ ] ").hasContent)
        assertTrue(MemoRichTextState("- [ ] Report").hasContent)
        val source = "  * [ ] Report\n\n> Notes"
        assertEquals(source, MemoRichTextState(source).body)
        val editor = MemoBlockEditorState(source, clock = { 0L })
        editor.toggleChecked(editor.lines.first().id)
        editor.undo()
        assertEquals(source, editor.body)
    }
}
