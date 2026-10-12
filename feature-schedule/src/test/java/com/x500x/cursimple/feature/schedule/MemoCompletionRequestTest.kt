package com.x500x.cursimple.feature.schedule

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import com.x500x.cursimple.core.kernel.model.MemoNote
import com.x500x.cursimple.core.kernel.model.MemoPriority
import org.junit.Assert.*
import org.junit.Test

class MemoCompletionRequestTest {
    private val note = MemoNote("note", title = "实验报告", body = "说明\n- [ ] 上传报告\n- [ ] 检查附件")

    @Test fun requestingCompletionLeavesTheNoteAndChecklistUntouched() {
        val request = MemoCompletionRequest.from(note, 1)!!
        assertEquals("上传报告", request.label)
        assertFalse(note.completed)
        assertEquals("说明\n- [ ] 上传报告\n- [ ] 检查附件", note.body)
    }

    @Test fun confirmedChecklistChangePreservesNewMetadataAndOtherCompletedItems() {
        val request = MemoCompletionRequest.from(note, 1)!!
        val latest = note.copy(priority = MemoPriority.High, pinned = true,
            body = "说明\n- [ ] 上传报告\n- [x] 检查附件")
        assertEquals(latest.copy(body = "说明\n- [x] 上传报告\n- [x] 检查附件"), request.completedNote(latest))
    }

    @Test fun editedRemovedOrAlreadyCompletedTargetsCannotBeCompletedAgain() {
        val request = MemoCompletionRequest.from(note, 1)!!
        assertNull(request.completedNote(note.copy(body = "说明\n- [ ] 另一项\n- [ ] 检查附件")))
        assertNull(request.completedNote(note.copy(id = "different")))
        assertNull(request.completedNote(note.copy(completed = true)))
        val completed = request.completedNote(note)!!
        assertNull(request.completedNote(completed))
    }

    @Test fun wholeNoteConfirmationKeepsItsChecklistAndRejectsTitleChanges() {
        val request = MemoCompletionRequest.from(note)!!
        assertEquals(note.copy(completed = true), request.completedNote(note))
        assertNull(request.completedNote(note.copy(title = "已修改的标题")))
    }

    @Test fun checkedNonChecklistAndInvalidLinesDoNotCreateRequests() {
        assertNull(MemoCompletionRequest.from(note, 0))
        assertNull(MemoCompletionRequest.from(note, -1))
        assertNull(MemoCompletionRequest.from(note, 99))
        assertNull(MemoCompletionRequest.from(note.copy(completed = true)))
        assertNull(MemoCompletionRequest.from(note.copy(body = "- [x] 已完成"), 0))
    }

    @Test fun editorConfirmationIsIdempotentAndRemainsUndoable() {
        val state = MemoBlockEditorState(note.body, clock = { 0L })
        val target = state.lines[1]
        assertFalse(state.canUndo)
        state.confirmChecked(target)
        assertTrue(state.lines[1].checked)
        assertFalse(state.lines[2].checked)
        state.confirmChecked(target)
        state.undo()
        assertEquals(note.body, state.body)
    }

    @Test fun editorConfirmationRejectsEditedAndRemovedLines() {
        val state = MemoBlockEditorState(note.body, clock = { 0L })
        val target = state.lines[1]
        state.onFieldChange(target.id, TextFieldValue("\u200B已修改", TextRange(4)))
        state.confirmChecked(target)
        assertFalse(state.lines[1].checked)
        state.removeBlock(target.id)
        val body = state.body
        state.confirmChecked(target)
        assertEquals(body, state.body)
    }
}
