package com.x500x.cursimple.feature.schedule

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.x500x.cursimple.core.kernel.model.MemoNote
import com.x500x.cursimple.core.kernel.model.MemoPriority
import com.x500x.cursimple.core.kernel.time.BeijingTime
import com.x500x.cursimple.feature.plugin.ui.AppOutlinedButton
import com.x500x.cursimple.feature.schedule.time.LocalAppZone
import java.time.LocalDateTime
import java.time.LocalTime

private enum class MemoDuePick { Date, Time }

/**
 * 新建、修改一条备忘。整屏的对话框：正文长了也有地方写。
 *
 * 正文就是一个文本框，上面一排按钮帮忙加复选框、列表、标题、粗体；
 * 在复选框、列表那一行按回车会自动续上下一项。切到「预览」就是卡片上看到的样子，
 * 复选框在预览里也能直接点。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MemoEditorDialog(
    initial: MemoNote,
    isNew: Boolean,
    notebooks: List<MemoNotebook>,
    openCounts: Map<String?, Int>,
    onDismiss: () -> Unit,
    onSave: (MemoNote) -> Unit,
    onDelete: () -> Unit,
) {
    val zone = LocalAppZone.current
    var courseKey by remember { mutableStateOf(initial.courseKey) }
    var title by remember { mutableStateOf(initial.title) }
    var body by remember { mutableStateOf(initial.body) }
    var priority by remember { mutableStateOf(initial.priority) }
    var pinned by remember { mutableStateOf(initial.pinned) }
    var due by remember { mutableStateOf(initial.dueDateTime) }
    var duePick by remember { mutableStateOf<MemoDuePick?>(null) }

    when (duePick) {
        MemoDuePick.Date -> AlarmDatePickerDialog(
            initial = (due ?: BeijingTime.nowDateTimeIn(zone)).toLocalDate(),
            onDismiss = { duePick = null },
            onPick = { date ->
                due = date.atTime(due?.toLocalTime() ?: LocalTime.of(23, 59))
                duePick = null
            },
        )
        MemoDuePick.Time -> EventTimePickerDialog(
            initialMinute = (due ?: LocalDateTime.now()).let { it.hour * 60 + it.minute },
            onDismiss = { duePick = null },
            onPick = { minute ->
                val date = due?.toLocalDate() ?: BeijingTime.nowDateTimeIn(zone).toLocalDate()
                due = date.atTime(minute / 60, minute % 60)
                duePick = null
            },
        )
        null -> Unit
    }

    fun save() {
        val notebook = notebooks.firstOrNull { it.key == courseKey }
        val now = System.currentTimeMillis()
        onSave(
            initial.copy(
                courseKey = courseKey,
                courseTitle = notebook?.takeIf { it.key != null }?.title ?: initial.courseTitle.takeIf { courseKey != null }.orEmpty(),
                title = title.trim(),
                body = body.trimEnd(),
                priority = priority,
                pinned = pinned,
                dueAt = due?.withSecond(0)?.withNano(0)?.toString(),
                createdAt = initial.createdAt.takeIf { it > 0 } ?: now,
                updatedAt = now,
            ),
        )
    }
    val canSave = title.isNotBlank() || body.isNotBlank()

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        MemoTapShieldHost(Modifier.fillMaxSize()) {
            Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .statusBarsPadding()
                        .imePadding(),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.schedule_action_cancel))
                        }
                        Text(
                            text = stringResource(if (isNew) R.string.memo_editor_new else R.string.memo_editor_edit),
                            style = MaterialTheme.typography.titleLarge,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = ::save, enabled = canSave) {
                            Text(stringResource(R.string.schedule_action_save), fontWeight = FontWeight.SemiBold)
                        }
                    }
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 16.dp)
                            .padding(bottom = 24.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        MemoEditorSection(stringResource(R.string.memo_editor_notebook)) {
                            MemoNotebookDropdown(
                                notebooks = notebooks,
                                openCounts = openCounts,
                                selectedKey = courseKey,
                                onSelect = { courseKey = it },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                        OutlinedTextField(
                            value = title,
                            onValueChange = { title = it },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            textStyle = MaterialTheme.typography.titleMedium,
                            placeholder = { Text(stringResource(R.string.memo_editor_title_hint)) },
                            shape = RoundedCornerShape(14.dp),
                        )

                        // 写的时候就是卡片上的样子：复选框能直接勾，列表、标题都画出来，不用再切「预览」
                        MemoBlockEditor(
                            initialBody = initial.body,
                            onBodyChange = { body = it },
                            modifier = Modifier.fillMaxWidth(),
                        )

                        MemoEditorSection(stringResource(R.string.memo_editor_priority)) {
                            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                                MemoPriority.entries.forEachIndexed { index, entry ->
                                    SegmentedButton(
                                        selected = priority == entry,
                                        onClick = { priority = entry },
                                        shape = SegmentedButtonDefaults.itemShape(index, MemoPriority.entries.size),
                                        icon = {
                                            if (entry != MemoPriority.None) {
                                                // 图标位会把子项撑到它的高度，用 requiredSize 定死，才是个正圆点
                                                Box(
                                                    modifier = Modifier
                                                        .requiredSize(10.dp)
                                                        .clip(CircleShape)
                                                        .background(memoPriorityColor(entry)),
                                                )
                                            }
                                        },
                                    ) { Text(memoPriorityLabel(entry)) }
                                }
                            }
                        }

                        MemoEditorSection(stringResource(R.string.memo_editor_due)) {
                            val current = due
                            if (current == null) {
                                AppOutlinedButton(onClick = {
                                    // 默认今天结束前；多数截止时间是「今晚」或者改个日期
                                    due = BeijingTime.nowDateTimeIn(zone).toLocalDate().atTime(23, 59)
                                    duePick = MemoDuePick.Date
                                }) { Text(stringResource(R.string.memo_editor_due_add)) }
                            } else {
                                AlarmValueRow(
                                    label = stringResource(R.string.memo_editor_due_date),
                                    value = formatPickerDate(current.toLocalDate()),
                                    onClick = { duePick = MemoDuePick.Date },
                                )
                                AlarmValueRow(
                                    label = stringResource(R.string.memo_editor_due_time),
                                    value = "%02d:%02d".format(current.hour, current.minute),
                                    onClick = { duePick = MemoDuePick.Time },
                                )
                                TextButton(onClick = { due = null }) { Text(stringResource(R.string.memo_editor_due_clear)) }
                            }
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Rounded.PushPin,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                            )
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(stringResource(R.string.memo_pin), fontWeight = FontWeight.SemiBold)
                                Text(
                                    text = stringResource(R.string.memo_editor_pin_desc),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Switch(checked = pinned, onCheckedChange = { pinned = it })
                        }

                        if (!isNew) {
                            TextButton(
                                onClick = onDelete,
                                modifier = Modifier.align(Alignment.CenterHorizontally),
                            ) {
                                Icon(Icons.Rounded.DeleteOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                                Spacer(Modifier.width(6.dp))
                                Text(stringResource(R.string.memo_editor_delete), color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MemoEditorSection(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.SemiBold,
        )
        content()
    }
}
