package com.x500x.cursimple.feature.schedule

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.x500x.cursimple.core.kernel.model.MemoPriority
import com.x500x.cursimple.core.kernel.time.BeijingTime
import com.x500x.cursimple.feature.plugin.ui.AppOutlinedButton
import com.x500x.cursimple.feature.schedule.time.LocalAppZone
import java.time.LocalDateTime

@Composable
fun MemoNotebookSelector(notebooks: List<MemoNotebook>, selectedKey: String?, modifier: Modifier = Modifier,
    enabled: Boolean = true, openCounts: Map<String?, Int> = emptyMap(), onSelect: (MemoNotebook) -> Unit) {
    if (enabled) MemoNotebookDropdown(notebooks, openCounts, selectedKey,
        onSelect = { key -> notebooks.firstOrNull { it.key == key }?.let(onSelect) }, modifier = modifier)
    else OutlinedTextField(value = notebooks.firstOrNull { it.key == selectedKey }?.title.orEmpty(), onValueChange = {},
        modifier = modifier, enabled = false, readOnly = true, singleLine = true, shape = RoundedCornerShape(16.dp))
}

/** Both editors use the same labels, coloured markers and segmented choices. */
@Composable
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
fun MemoPrioritySelector(priority: MemoPriority, onChange: (MemoPriority) -> Unit, modifier: Modifier = Modifier,
    enabled: Boolean = true, compact: Boolean = false) {
    if (compact) {
        FlowRow(modifier = modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)) {
            MemoPriority.entries.forEach { entry ->
                FilterChip(selected = priority == entry, onClick = { onChange(entry) }, enabled = enabled,
                    label = { Text(memoPriorityLabel(entry), maxLines = 1) }, leadingIcon = if (entry == MemoPriority.None) null else {
                        { Box(Modifier.size(10.dp).clip(CircleShape).background(memoPriorityColor(entry))) }
                    })
            }
        }
        return
    }
    SingleChoiceSegmentedButtonRow(modifier = modifier.fillMaxWidth()) {
        MemoPriority.entries.forEachIndexed { index, entry ->
            SegmentedButton(selected = priority == entry, onClick = { onChange(entry) }, enabled = enabled,
                shape = SegmentedButtonDefaults.itemShape(index, MemoPriority.entries.size), icon = {
                    if (entry != MemoPriority.None) Box(Modifier.requiredSize(10.dp).clip(CircleShape).background(memoPriorityColor(entry)))
                }) { Text(memoPriorityLabel(entry), maxLines = 1) }
        }
    }
}

/** Native date/time dialogs are the same ones used inside the app. */
@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun MemoDeadlineFields(due: LocalDateTime?, onChange: (LocalDateTime?) -> Unit, enabled: Boolean = true) {
    val zone = LocalAppZone.current
    var pickDate by remember { mutableStateOf(false) }
    var pickTime by remember { mutableStateOf(false) }
    if (pickDate) AlarmDatePickerDialog(initial = (due ?: BeijingTime.nowDateTimeIn(zone)).toLocalDate(),
        onDismiss = { pickDate = false }, onPick = { date ->
            onChange(date.atTime(due?.toLocalTime() ?: java.time.LocalTime.of(23, 59))); pickDate = false
        })
    if (pickTime) EventTimePickerDialog(initialMinute = (due ?: BeijingTime.nowDateTimeIn(zone)).let { it.hour * 60 + it.minute },
        onDismiss = { pickTime = false }, onPick = { minute ->
            onChange((due?.toLocalDate() ?: BeijingTime.nowDateTimeIn(zone).toLocalDate()).atTime(minute / 60, minute % 60)); pickTime = false
        })
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (due == null) AppOutlinedButton(onClick = { pickDate = true }, enabled = enabled) {
            Text(stringResource(R.string.memo_editor_due_add))
        } else {
            AlarmValueRow(stringResource(R.string.memo_editor_due_date), formatPickerDate(due.toLocalDate())) { if (enabled) pickDate = true }
            AlarmValueRow(stringResource(R.string.memo_editor_due_time), "%02d:%02d".format(due.hour, due.minute)) { if (enabled) pickTime = true }
            AppOutlinedButton(onClick = { onChange(null) }, enabled = enabled) { Text(stringResource(R.string.memo_editor_due_clear)) }
        }
    }
}
