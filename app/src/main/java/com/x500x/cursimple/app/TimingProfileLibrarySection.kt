package com.x500x.cursimple.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.x500x.cursimple.R
import com.x500x.cursimple.core.kernel.model.TimingProfileEntry
import com.x500x.cursimple.core.kernel.model.TimingProfileLibrary
import com.x500x.cursimple.core.kernel.model.active

/** 名称为空的是内置那一套，按当前语言显示默认名。 */
@Composable
internal fun timingProfileDisplayName(entry: TimingProfileEntry): String {
    return entry.name.takeIf { it.isNotBlank() }
        ?: stringResource(R.string.settings_timing_profile_default_name)
}

/** 作息套数的选择与增删改。编辑节次内容仍在下方的编辑区里进行。 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun TimingProfileLibrarySection(
    library: TimingProfileLibrary,
    onActivate: (TimingProfileEntry) -> Unit,
    onCreate: () -> Unit,
    onRename: (TimingProfileEntry, String) -> Unit,
    onDuplicate: (TimingProfileEntry, String) -> Unit,
    onDelete: (TimingProfileEntry) -> Unit,
) {
    var renaming by remember { mutableStateOf<TimingProfileEntry?>(null) }
    var pendingDelete by remember { mutableStateOf<TimingProfileEntry?>(null) }
    val activeId = library.active?.id

    renaming?.let { target ->
        val current = timingProfileDisplayName(target)
        TimingProfileNameDialog(
            initial = current,
            onDismiss = { renaming = null },
            onConfirm = { name ->
                onRename(target, name)
                renaming = null
            },
        )
    }

    pendingDelete?.let { target ->
        val name = timingProfileDisplayName(target)
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text(stringResource(R.string.settings_timing_profile_delete_title)) },
            text = { Text(stringResource(R.string.settings_timing_profile_delete_body, name)) },
            confirmButton = {
                TextButton(onClick = {
                    onDelete(target)
                    pendingDelete = null
                }) { Text(stringResource(R.string.settings_timing_profile_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) {
                    Text(stringResource(R.string.settings_cancel))
                }
            },
        )
    }

    Text(
        text = stringResource(R.string.settings_timing_profiles_title),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.SemiBold,
    )
    Text(
        text = stringResource(R.string.settings_timing_profiles_hint),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    library.profiles.forEach { entry ->
        val name = timingProfileDisplayName(entry)
        val copyName = stringResource(R.string.settings_timing_profile_copy_suffix, name)
        TimingProfileRow(
            entry = entry,
            name = name,
            selected = entry.id == activeId,
            deletable = library.profiles.size > 1,
            onActivate = { onActivate(entry) },
            onRename = { renaming = entry },
            onDuplicate = { onDuplicate(entry, copyName) },
            onDelete = { pendingDelete = entry },
        )
    }

    OutlinedButton(onClick = onCreate, modifier = Modifier.fillMaxWidth()) {
        Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(modifier = Modifier.width(6.dp))
        Text(stringResource(R.string.settings_timing_profile_create))
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TimingProfileRow(
    entry: TimingProfileEntry,
    name: String,
    selected: Boolean,
    deletable: Boolean,
    onActivate: () -> Unit,
    onRename: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit,
) {
    val container = if (selected) {
        MaterialTheme.colorScheme.secondaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = container),
    ) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (selected) {
                    Icon(
                        imageVector = Icons.Rounded.Check,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = name,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = if (entry.slotTimes.isEmpty()) {
                            stringResource(R.string.settings_timing_profile_empty)
                        } else {
                            stringResource(R.string.settings_timing_profile_slot_count, entry.slotTimes.size)
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (!selected) {
                    OutlinedButton(onClick = onActivate) {
                        Text(stringResource(R.string.settings_timing_profile_use), maxLines = 1)
                    }
                }
                OutlinedButton(onClick = onRename) {
                    Text(stringResource(R.string.settings_timing_profile_rename), maxLines = 1)
                }
                OutlinedButton(onClick = onDuplicate) {
                    Text(stringResource(R.string.settings_timing_profile_duplicate), maxLines = 1)
                }
                if (deletable) {
                    OutlinedButton(onClick = onDelete) {
                        Text(stringResource(R.string.settings_timing_profile_delete), maxLines = 1)
                    }
                }
            }
        }
    }
}

@Composable
internal fun TimingProfileNameDialog(
    initial: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var name by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_timing_profile_name_hint)) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                singleLine = true,
                label = { Text(stringResource(R.string.settings_timing_profile_name_hint)) },
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(name) },
                enabled = name.isNotBlank(),
            ) { Text(stringResource(R.string.settings_confirm)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.settings_cancel)) }
        },
    )
}
