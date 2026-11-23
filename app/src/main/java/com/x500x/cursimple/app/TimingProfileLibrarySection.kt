package com.x500x.cursimple.app

import com.x500x.cursimple.feature.plugin.ui.AppOutlinedTextField
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.x500x.cursimple.R
import com.x500x.cursimple.core.kernel.model.TimingProfileEntry
import com.x500x.cursimple.core.kernel.model.TimingProfileLibrary
import com.x500x.cursimple.core.kernel.model.active
import com.x500x.cursimple.feature.plugin.ui.AppDialogTone
import com.x500x.cursimple.feature.plugin.ui.AppDialogDangerButton
import com.x500x.cursimple.feature.plugin.ui.AppAlertDialog
import com.x500x.cursimple.feature.plugin.ui.AppDialogPrimaryButton
import com.x500x.cursimple.feature.plugin.ui.AppDialogSecondaryButton
import androidx.compose.material.icons.rounded.AccessTime
import androidx.compose.material.icons.rounded.DeleteOutline

@Composable
internal fun timingProfileDisplayName(entry: TimingProfileEntry): String {
    return entry.name.takeIf { it.isNotBlank() }
        ?: stringResource(R.string.settings_timing_profile_default_name)
}

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
        AppAlertDialog(
            onDismissRequest = { pendingDelete = null },
            tone = AppDialogTone.Danger,
            icon = { Icon(Icons.Rounded.DeleteOutline, contentDescription = null, modifier = Modifier.size(22.dp)) },
            title = { Text(stringResource(R.string.settings_timing_profile_delete_title)) },
            text = { Text(stringResource(R.string.settings_timing_profile_delete_body, name)) },
            confirmButton = {
                AppDialogDangerButton(onClick = {
                    onDelete(target)
                    pendingDelete = null
                }) { Text(stringResource(R.string.settings_timing_profile_delete)) }
            },
            dismissButton = {
                AppDialogSecondaryButton(onClick = { pendingDelete = null }) {
                    Text(stringResource(R.string.settings_cancel))
                }
            },
        )
    }

    SettingsSectionHeader(stringResource(R.string.settings_timing_profiles_title))

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SettingsHintText(stringResource(R.string.settings_timing_profiles_hint))

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

        NewTimingProfileButton(onClick = onCreate)
    }
}

/** Full-width outlined pill that adds another timing set, matching the reference's create action. */
@Composable
private fun NewTimingProfileButton(onClick: () -> Unit) {
    val accent = MaterialTheme.colorScheme.primary
    val shape = RoundedCornerShape(22.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface)
            .border(1.5.dp, accent.copy(alpha = 0.55f), shape)
            .clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = Icons.Rounded.Add,
            contentDescription = null,
            tint = accent,
            modifier = Modifier.size(16.dp),
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = stringResource(R.string.settings_timing_profile_create),
            style = MaterialTheme.typography.labelLarge.copy(fontSize = 14.sp, letterSpacing = 0.sp),
            fontWeight = FontWeight.Bold,
            color = accent,
            maxLines = 1,
        )
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
    val accent = MaterialTheme.colorScheme.primary
    val shape = RoundedCornerShape(20.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(
                if (selected) accent.copy(alpha = 0.10f) else MaterialTheme.colorScheme.surface,
            )
            .border(
                width = if (selected) 1.5.dp else 1.dp,
                color = if (selected) accent else MaterialTheme.colorScheme.outlineVariant,
                shape = shape,
            )
            .padding(start = 14.dp, end = 14.dp, top = 12.dp, bottom = 14.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = name,
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontSize = 14.sp,
                        lineHeight = 18.sp,
                        letterSpacing = 0.sp,
                    ),
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = if (entry.slotTimes.isEmpty()) {
                        stringResource(R.string.settings_timing_profile_empty)
                    } else {
                        pluralStringResource(R.plurals.settings_timing_profile_slot_count, entry.slotTimes.size, entry.slotTimes.size)
                    },
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, lineHeight = 17.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (selected) {
                Text(
                    text = stringResource(R.string.settings_timing_profile_active_badge),
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp, lineHeight = 15.sp),
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier
                        .background(accent, RoundedCornerShape(9.dp))
                        .padding(horizontal = 8.dp, vertical = 2.dp),
                )
            }
        }
        FlowRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (!selected) {
                TimingProfileChip(stringResource(R.string.settings_timing_profile_use), onActivate)
            }
            TimingProfileChip(stringResource(R.string.settings_timing_profile_rename), onRename)
            TimingProfileChip(stringResource(R.string.settings_timing_profile_duplicate), onDuplicate)
            if (deletable) {
                TimingProfileChip(stringResource(R.string.settings_timing_profile_delete), onDelete)
            }
        }
    }
}

/** 30dp outlined action pill used inside a timing-set card. */
@Composable
private fun TimingProfileChip(text: String, onClick: () -> Unit) {
    val accent = MaterialTheme.colorScheme.primary
    val shape = RoundedCornerShape(15.dp)
    Box(
        modifier = Modifier
            .height(30.dp)
            .clip(shape)
            .border(1.5.dp, accent.copy(alpha = 0.55f), shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 12.sp, lineHeight = 16.sp),
            fontWeight = FontWeight.Bold,
            color = accent,
            maxLines = 1,
        )
    }
}

@Composable
internal fun TimingProfileNameDialog(
    initial: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var name by remember { mutableStateOf(initial) }
    AppAlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.AccessTime, contentDescription = null, modifier = Modifier.size(22.dp)) },
        title = { Text(stringResource(R.string.settings_timing_profile_name_hint)) },
        text = {
            AppOutlinedTextField(
                value = name,
                onValueChange = { name = it },
                singleLine = true,
                label = { Text(stringResource(R.string.settings_timing_profile_name_label)) },
            )
        },
        confirmButton = {
            AppDialogPrimaryButton(
                onClick = { onConfirm(name) },
                enabled = name.isNotBlank(),
            ) { Text(stringResource(R.string.settings_confirm)) }
        },
        dismissButton = {
            AppDialogSecondaryButton(onClick = onDismiss) { Text(stringResource(R.string.settings_cancel)) }
        },
    )
}
