package com.x500x.cursimple.feature.schedule

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.FormatListBulleted
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.automirrored.rounded.Redo
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.x500x.cursimple.core.data.memo.MemoImageStore
import com.x500x.cursimple.core.kernel.model.MemoImageRef
import com.x500x.cursimple.feature.plugin.ui.AppAtomicText
import com.x500x.cursimple.feature.plugin.ui.AppOutlinedButton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.net.URI

/** Header-only history controls; the bottom bar remains entirely for editing tools. */
@Composable
internal fun MemoEditorHistoryControls(state: MemoBlockEditorState, enabled: Boolean = true) {
    Row {
        MemoTool(Icons.AutoMirrored.Rounded.Undo, R.string.memo_tool_undo, "undo", enabled && state.canUndo, onClick = state::undo)
        MemoTool(Icons.AutoMirrored.Rounded.Redo, R.string.memo_tool_redo, "redo", enabled && state.canRedo, onClick = state::redo)
    }
}

internal fun memoLinkIsValid(url: String): Boolean = runCatching {
    val uri = URI(url.trim())
    uri.scheme?.lowercase() in setOf("https", "http") && !uri.host.isNullOrBlank() &&
        url.none { it in "()\n\r" }
}.getOrDefault(false)

/** Compact icons scroll in one row, retaining native 48dp touch targets. */
@Composable
internal fun MemoEditorToolbar(state: MemoBlockEditorState, modifier: Modifier = Modifier, enabled: Boolean = true, compact: Boolean = false) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var failure by remember { mutableStateOf(false) }
    var linkOpen by remember { mutableStateOf(false) }
    val toolScroll = rememberScrollState()
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val barColor = MaterialTheme.colorScheme.surfaceContainer
    val imageLabel = stringResource(R.string.memo_tool_image)
    // The content picker opens the phone's gallery, which previews before selecting and allows
    // several photos; the single-select photo picker returned on the first tap.
    val photo = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        if (uris.isNotEmpty() && !state.importingImage) {
            state.importingImage = true; failure = false
            scope.launch {
                try {
                    val store = MemoImageStore(context)
                    val images = uris.mapNotNull { uri ->
                        try { MemoImageRef.markdown(store.importImage(uri), imageLabel) }
                        catch (cancel: CancellationException) { throw cancel }
                        catch (_: Exception) { failure = true; null }
                    }
                    // One block keeps the batch in picking order and undoes in a single step.
                    if (images.isNotEmpty()) state.insertBlock(images.joinToString("\n"))
                }
                finally { state.importingImage = false }
            }
        }
    }
    if (linkOpen) MemoLinkDialog(state.selectedText(),
        onInsert = { label, url -> state.insertLink(label, url); linkOpen = false }, onDismiss = { linkOpen = false })
    Surface(modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.surfaceContainer) {
        Column {
            if (failure) Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.memo_image_insert_failed), color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                IconButton(onClick = { failure = false }) { Icon(Icons.Rounded.Close, stringResource(R.string.schedule_action_close)) }
            }
            Box(Modifier.fillMaxWidth()) {
            Row(Modifier.fillMaxWidth().horizontalScroll(toolScroll).padding(horizontal = 4.dp, vertical = if (compact) 0.dp else 2.dp)
                .testTag("memo-edit-tools"), verticalAlignment = Alignment.CenterVertically) {
                if (state.importingImage) Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                } else MemoTool(Icons.Rounded.Image, R.string.memo_tool_image, "image", enabled, onClick = {
                    photo.launch("image/*")
                })
                val target = state.targetLine
                MemoTool(Icons.Rounded.Check, R.string.memo_tool_check, "check", enabled, target.kind == MemoLineKind.Check) { state.applyKind(MemoLineKind.Check) }
                MemoTool(Icons.AutoMirrored.Rounded.FormatListBulleted, R.string.memo_tool_bullet, "bullet", enabled, target.kind == MemoLineKind.Bullet) { state.applyKind(MemoLineKind.Bullet) }
                MemoTool(Icons.Rounded.FormatListNumbered, R.string.memo_tool_numbered, "numbered", enabled, target.kind == MemoLineKind.Numbered) { state.applyKind(MemoLineKind.Numbered) }
                MemoTool(Icons.Rounded.FormatBold, R.string.memo_tool_bold, "bold", enabled) { state.wrap("**") }
                MemoTool(Icons.Rounded.FormatItalic, R.string.memo_tool_italic, "italic", enabled) { state.wrap("*") }
                MemoTool(Icons.Rounded.Title, R.string.memo_tool_heading, "heading", enabled, target.kind == MemoLineKind.Heading) { state.applyKind(MemoLineKind.Heading) }
                MemoTool(Icons.Rounded.FormatQuote, R.string.memo_tool_quote, "quote", enabled, target.kind == MemoLineKind.Quote) { state.applyKind(MemoLineKind.Quote) }
                MemoTool(Icons.Rounded.FormatStrikethrough, R.string.memo_tool_strike, "strike", enabled) { state.wrap("~~") }
                MemoTool(Icons.Rounded.Code, R.string.memo_tool_code, "code", enabled) { state.wrap("`") }
                MemoTool(Icons.Rounded.Link, R.string.memo_tool_link, "link", enabled) { linkOpen = true }
                MemoTool(Icons.Rounded.HorizontalRule, R.string.memo_tool_divider, "divider", enabled) { state.insertBlock("---") }
                MemoTool(Icons.Rounded.FormatClear, R.string.memo_tool_clear, "clear", enabled, onClick = state::clearFormatting)
            }
            if (toolScroll.canScrollForward) Box(
                Modifier.align(if (rtl) Alignment.CenterStart else Alignment.CenterEnd)
                    .width(28.dp).height(if (compact) 48.dp else 52.dp)
                    .background(Brush.horizontalGradient(if (rtl) listOf(barColor, Color.Transparent) else listOf(Color.Transparent, barColor)))
                    .testTag("memo-tools-more-end"), contentAlignment = Alignment.Center,
            ) {
                Icon(if (rtl) Icons.Rounded.ChevronLeft else Icons.Rounded.ChevronRight, contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f), modifier = Modifier.size(18.dp))
            }
            if (toolScroll.canScrollBackward) Box(
                Modifier.align(if (rtl) Alignment.CenterEnd else Alignment.CenterStart)
                    .width(20.dp).height(if (compact) 48.dp else 52.dp)
                    .background(Brush.horizontalGradient(if (rtl) listOf(Color.Transparent, barColor) else listOf(barColor, Color.Transparent)))
                    .testTag("memo-tools-more-start"),
            )
            }
        }
    }
}

/** Hover or long-press names the tool, since the icons alone are ambiguous. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MemoTool(icon: ImageVector, label: Int, tag: String, enabled: Boolean,
    selected: Boolean = false, onClick: () -> Unit) {
    val name = stringResource(label)
    TooltipBox(
        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
        tooltip = { PlainTooltip { Text(name) } },
        state = rememberTooltipState(),
    ) {
        IconButton(onClick, enabled = enabled, modifier = Modifier.size(48.dp).testTag("memo-tool-$tag")) {
            Icon(icon, name, modifier = Modifier.size(32.dp)
                .background(if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent, CircleShape).padding(6.dp),
                tint = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else LocalContentColor.current)
        }
    }
}

@Composable
private fun MemoLinkDialog(initialLabel: String, onInsert: (String, String) -> Unit, onDismiss: () -> Unit) {
    var label by remember { mutableStateOf(initialLabel) }
    var url by remember { mutableStateOf("") }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(stringResource(R.string.memo_tool_link)) }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(label, { label = it }, label = { Text(stringResource(R.string.memo_link_label)) },
                singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(url, { url = it }, label = { Text(stringResource(R.string.memo_link_url)) },
                singleLine = true, modifier = Modifier.fillMaxWidth())
        }
    }, confirmButton = {
        Button(onClick = { onInsert(label.trim(), url.trim()) }, enabled = memoLinkIsValid(url)) {
            AppAtomicText(stringResource(R.string.memo_tool_insert))
        }
    }, dismissButton = { AppOutlinedButton(onClick = onDismiss) { AppAtomicText(stringResource(R.string.schedule_action_cancel)) } })
}
