package com.x500x.cursimple.feature.schedule

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** Shared Markdown model stays alive while the desktop window switches settings panels. */
@Stable
class MemoRichTextState(initialBody: String) {
    internal val blocks = MemoBlockEditorState(initialBody)
    val body: String get() = blocks.body
    val hasContent: Boolean get() = blocks.lines.any { it.content.isNotBlank() }
    val importingImage: Boolean get() = blocks.importingImage

    fun focusLine(index: Int) {
        blocks.lines.getOrNull(index)?.let { blocks.focusLine(it.id) }
    }

    internal companion object {
        val StateSaver = Saver<MemoRichTextState, String>(save = { it.body }, restore = { MemoRichTextState(it) })
    }
}

@Composable
fun rememberMemoRichTextState(initialBody: String, documentId: String): MemoRichTextState =
    rememberSaveable(documentId, saver = MemoRichTextState.StateSaver) { MemoRichTextState(initialBody) }

/** Uses the same native list rows, inline rendering and round checklist controls as Notes. */
@Composable
fun MemoMiniEditorField(state: MemoRichTextState, modifier: Modifier = Modifier, enabled: Boolean = true,
    lineTagPrefix: String? = null) {
    MemoBlockEditor(state.blocks, modifier, minimumHeight = 120.dp, enabled = enabled, lineTagPrefix = lineTagPrefix)
}

@Composable
fun MemoMiniEditorToolbar(state: MemoRichTextState, modifier: Modifier = Modifier, enabled: Boolean = true) {
    MemoEditorToolbar(state.blocks, modifier, enabled = enabled, compact = true)
}

@Composable
fun MemoMiniEditorHistory(state: MemoRichTextState, enabled: Boolean = true) {
    MemoEditorHistoryControls(state.blocks, enabled = enabled)
}
