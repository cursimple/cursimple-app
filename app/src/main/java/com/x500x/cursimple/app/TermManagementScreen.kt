package com.x500x.cursimple.app

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.x500x.cursimple.R
import com.x500x.cursimple.core.data.term.TermProfile
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import com.x500x.cursimple.core.kernel.time.datePickerMillisToLocalDate
import com.x500x.cursimple.core.kernel.time.toDatePickerMillis

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TermManagementScreen(
    state: TermProfileUiState,
    onBack: () -> Unit,
    onCreate: (name: String, startDate: LocalDate?) -> Unit,
    onRename: (id: String, name: String) -> Unit,
    onSetStartDate: (id: String, date: LocalDate?) -> Unit,
    onActivate: (id: String) -> Unit,
    onDelete: (id: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showCreateSheet by rememberSaveable { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<TermProfile?>(null) }
    var pickDateTarget by remember { mutableStateOf<TermProfile?>(null) }
    var deleteTarget by remember { mutableStateOf<TermProfile?>(null) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.term_mgmt_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.term_mgmt_back))
                    }
                },
                actions = {
                    IconButton(onClick = { showCreateSheet = true }) {
                        Icon(Icons.Rounded.Add, contentDescription = stringResource(R.string.term_mgmt_create))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        if (state.terms.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(stringResource(R.string.term_mgmt_empty_title), style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        stringResource(R.string.term_mgmt_empty_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 12.dp),
            ) {
                items(state.terms, key = { it.id }) { term ->
                    TermRow(
                        term = term,
                        active = term.id == state.activeTermId,
                        onActivate = { onActivate(term.id) },
                        onRename = { renameTarget = term },
                        onPickDate = { pickDateTarget = term },
                        onDelete = { deleteTarget = term },
                        canDelete = state.terms.size > 1,
                    )
                }
            }
        }
    }

    if (showCreateSheet) {
        CreateTermDialog(
            onDismiss = { showCreateSheet = false },
            onConfirm = { name, date ->
                onCreate(name, date)
                showCreateSheet = false
            },
        )
    }

    renameTarget?.let { term ->
        RenameDialog(
            initial = term.name,
            onDismiss = { renameTarget = null },
            onConfirm = { newName ->
                onRename(term.id, newName)
                renameTarget = null
            },
        )
    }

    pickDateTarget?.let { term ->
        TermDatePickerDialog(
            initial = term.termStartDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
                ?: LocalDate.now(),
            onDismiss = { pickDateTarget = null },
            onConfirm = { date ->
                onSetStartDate(term.id, date)
                pickDateTarget = null
            },
        )
    }

    deleteTarget?.let { term ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text(stringResource(R.string.term_delete_title)) },
            text = { Text(stringResource(R.string.term_delete_message, term.name)) },
            confirmButton = {
                TextButton(onClick = {
                    onDelete(term.id)
                    deleteTarget = null
                }) { Text(stringResource(R.string.term_delete_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text(stringResource(R.string.term_cancel)) }
            },
        )
    }
}

@Composable
private fun TermRow(
    term: TermProfile,
    active: Boolean,
    canDelete: Boolean,
    onActivate: () -> Unit,
    onRename: () -> Unit,
    onPickDate: () -> Unit,
    onDelete: () -> Unit,
) {
    val container = if (active) MaterialTheme.colorScheme.primaryContainer
    else MaterialTheme.colorScheme.surface
    val onContainer = if (active) MaterialTheme.colorScheme.onPrimaryContainer
    else MaterialTheme.colorScheme.onSurface

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .clickable(enabled = !active, onClick = onActivate),
        color = container,
        shape = RoundedCornerShape(20.dp),
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(
                            if (active) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.surfaceVariant
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = if (active) Icons.Rounded.Check else Icons.Rounded.CalendarMonth,
                        contentDescription = null,
                        tint = if (active) MaterialTheme.colorScheme.onPrimary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp),
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = term.name,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = onContainer,
                    )
                    val sub = term.termStartDate?.let { iso ->
                        runCatching { LocalDate.parse(iso) }.getOrNull()?.let { date ->
                            stringResource(R.string.term_start_prefix, DateTimeFormatter.ofPattern("yyyy/M/d").format(date))
                        }
                    } ?: stringResource(R.string.term_no_start_date)
                    Text(
                        text = sub,
                        style = MaterialTheme.typography.bodySmall,
                        color = onContainer.copy(alpha = 0.75f),
                    )
                }
                if (active) {
                    Surface(
                        color = MaterialTheme.colorScheme.primary,
                        shape = RoundedCornerShape(50),
                    ) {
                        Text(
                            text = stringResource(R.string.term_badge_current),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                        )
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                ActionChip(label = stringResource(R.string.term_action_rename), icon = Icons.Rounded.Edit, onClick = onRename)
                ActionChip(label = stringResource(R.string.term_action_date), icon = Icons.Rounded.CalendarMonth, onClick = onPickDate)
                if (canDelete) {
                    ActionChip(
                        label = stringResource(R.string.term_action_delete),
                        icon = Icons.Rounded.DeleteOutline,
                        onClick = onDelete,
                        destructive = true,
                    )
                }
            }
        }
    }
}

@Composable
private fun ActionChip(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    destructive: Boolean = false,
) {
    val container = if (destructive) MaterialTheme.colorScheme.errorContainer
    else MaterialTheme.colorScheme.surfaceVariant
    val onContainer = if (destructive) MaterialTheme.colorScheme.onErrorContainer
    else MaterialTheme.colorScheme.onSurfaceVariant
    Surface(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .clickable(onClick = onClick),
        color = container,
        shape = RoundedCornerShape(50),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, contentDescription = null, tint = onContainer, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(4.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = onContainer,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun CreateTermDialog(
    onDismiss: () -> Unit,
    onConfirm: (String, LocalDate?) -> Unit,
) {
    var name by rememberSaveable { mutableStateOf("") }
    var date by remember { mutableStateOf<LocalDate?>(LocalDate.now()) }
    var showDate by rememberSaveable { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.term_create_title)) },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.term_name_label)) },
                    singleLine = true,
                )
                Spacer(Modifier.height(12.dp))
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { showDate = true },
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.Rounded.CalendarMonth,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = date?.let {
                                stringResource(R.string.term_start_prefix, DateTimeFormatter.ofPattern("yyyy/M/d").format(it))
                            } ?: stringResource(R.string.term_no_start_date_optional),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank(),
                onClick = { onConfirm(name.trim(), date) },
            ) { Text(stringResource(R.string.term_create_confirm)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.term_cancel)) } },
    )
    if (showDate) {
        TermDatePickerDialog(
            initial = date ?: LocalDate.now(),
            onDismiss = { showDate = false },
            onConfirm = {
                date = it
                showDate = false
            },
        )
    }
}

@Composable
private fun RenameDialog(
    initial: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var name by rememberSaveable { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.term_rename_title)) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                singleLine = true,
                label = { Text(stringResource(R.string.term_new_name_label)) },
            )
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank(),
                onClick = { onConfirm(name.trim()) },
            ) { Text(stringResource(R.string.term_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.term_cancel)) } },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TermDatePickerDialog(
    initial: LocalDate,
    onDismiss: () -> Unit,
    onConfirm: (LocalDate) -> Unit,
) {
    val initialMillis = initial.toDatePickerMillis()
    val state = rememberDatePickerState(initialSelectedDateMillis = initialMillis)
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                state.selectedDateMillis?.let { millis ->
                    onConfirm(datePickerMillisToLocalDate(millis))
                }
            }) { Text(stringResource(R.string.term_date_confirm)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.term_cancel)) } },
    ) {
        DatePicker(state = state)
    }
}
