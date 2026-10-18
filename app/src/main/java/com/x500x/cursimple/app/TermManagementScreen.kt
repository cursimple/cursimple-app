package com.x500x.cursimple.app

import com.x500x.cursimple.feature.plugin.ui.AppFilterChip
import com.x500x.cursimple.feature.plugin.ui.AppOutlinedButton
import com.x500x.cursimple.feature.plugin.ui.appItemMotion
import com.x500x.cursimple.feature.plugin.ui.pressFeedback
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Merge
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.x500x.cursimple.feature.schedule.CalendarMonthPicker
import com.x500x.cursimple.R
import com.x500x.cursimple.core.data.term.TermProfile
import com.x500x.cursimple.core.kernel.model.TermMergeAlignment
import com.x500x.cursimple.core.kernel.time.BeijingTime
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll

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
    onMerge: (ids: List<String>, name: String) -> Unit = { _, _ -> },
    onMergeWithAlignment: ((ids: List<String>, name: String, alignment: TermMergeAlignment, anchorTermId: String) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    var showCreateSheet by rememberSaveable { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<TermProfile?>(null) }
    var pickDateTarget by remember { mutableStateOf<TermProfile?>(null) }
    var deleteTarget by remember { mutableStateOf<TermProfile?>(null) }
    var selecting by rememberSaveable { mutableStateOf(false) }
    var selectedIds by rememberSaveable { mutableStateOf(listOf<String>()) }
    var showMergeDialog by rememberSaveable { mutableStateOf(false) }

    fun leaveSelection() {
        selecting = false
        selectedIds = emptyList()
    }

    BackHandler(enabled = selecting) { leaveSelection() }

    val selectedTerms = remember(state.terms, selectedIds) {
        state.terms.filter { it.id in selectedIds }
    }
    val selectedDates = remember(selectedTerms) {
        selectedTerms.mapNotNull { it.termStartDate }
            .mapNotNull { runCatching { LocalDate.parse(it) }.getOrNull() }
    }
    val minSelectedDate = remember(selectedDates) { selectedDates.minOrNull() }
    val maxSelectedDate = remember(selectedDates) { selectedDates.maxOrNull() }
    val maxSpanWeeks = remember(minSelectedDate, maxSelectedDate) {
        if (minSelectedDate != null && maxSelectedDate != null) {
            val minMon = minSelectedDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            val maxMon = maxSelectedDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            (ChronoUnit.DAYS.between(minMon, maxMon) / 7).toInt()
        } else 0
    }
    val isLongSpan = maxSpanWeeks > 16

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    AnimatedContent(
                        targetState = selecting,
                        transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(180)) },
                        label = "TermTopBarTitle",
                    ) { isSelecting ->
                        Text(
                            text = if (isSelecting) stringResource(R.string.term_merge_selecting_title)
                            else stringResource(R.string.term_mgmt_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { if (selecting) leaveSelection() else onBack() }) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.term_mgmt_back))
                    }
                },
                actions = {
                    if (selecting) {
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(end = 12.dp),
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Check,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    text = stringResource(R.string.term_merge_selected_count, selectedIds.size),
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    } else {
                        if (state.terms.size > 1) {
                            IconButton(onClick = { selecting = true }) {
                                Icon(Icons.Rounded.Merge, contentDescription = stringResource(R.string.term_merge_action))
                            }
                        }
                        IconButton(onClick = { showCreateSheet = true }) {
                            Icon(Icons.Rounded.Add, contentDescription = stringResource(R.string.term_mgmt_create))
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
        bottomBar = {
            AnimatedVisibility(
                visible = selecting,
                enter = slideInVertically { it } + fadeIn(),
                exit = slideOutVertically { it } + fadeOut(),
            ) {
                SelectionBar(
                    hint = stringResource(R.string.term_merge_select_hint),
                    canConfirm = selectedIds.size > 1,
                    isLongSpan = isLongSpan,
                    spanWeeks = maxSpanWeeks,
                    onConfirm = { showMergeDialog = true },
                    onCancel = { leaveSelection() },
                )
            }
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
                if (!selecting && state.terms.size > 1) {
                    item(key = "term_merge_hero") {
                        TermMergeHeroCard(
                            onStartMerge = { selecting = true },
                        )
                    }
                }
                items(state.terms, key = { it.id }) { term ->
                    Box(appItemMotion()) {
                        val termDate = term.termStartDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
                        val shiftWeeks = if (minSelectedDate != null && termDate != null) {
                            val minMon = minSelectedDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                            val termMon = termDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                            (ChronoUnit.DAYS.between(minMon, termMon) / 7).toInt()
                        } else 0

                        TermRow(
                            term = term,
                            active = term.id == state.activeTermId,
                            selecting = selecting,
                            selected = term.id in selectedIds,
                            shiftWeeks = shiftWeeks,
                            onToggle = {
                                selectedIds = if (term.id in selectedIds) selectedIds - term.id else selectedIds + term.id
                            },
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

    if (showMergeDialog) {
        val sources = state.terms.filter { it.id in selectedIds }
        MergeDialog(
            sources = sources,
            activeTermId = state.activeTermId,
            onDismiss = { showMergeDialog = false },
            onConfirm = { name, alignment, anchorTermId ->
                if (onMergeWithAlignment != null) {
                    onMergeWithAlignment(sources.map { it.id }, name, alignment, anchorTermId)
                } else {
                    onMerge(sources.map { it.id }, name)
                }
                showMergeDialog = false
                leaveSelection()
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
                ?: BeijingTime.today(),
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
            shape = RoundedCornerShape(28.dp),
            title = { Text(stringResource(R.string.term_delete_title)) },
            text = { Text(stringResource(R.string.term_delete_message, term.name)) },
            confirmButton = {
                AppOutlinedButton(
                    onClick = {
                        onDelete(term.id)
                        deleteTarget = null
                    },
                    modifier = Modifier.height(44.dp),
                ) { Text(stringResource(R.string.term_delete_confirm)) }
            },
            dismissButton = {
                AppOutlinedButton(
                    onClick = { deleteTarget = null },
                    modifier = Modifier.height(44.dp),
                ) { Text(stringResource(R.string.term_cancel)) }
            },
        )
    }
}

/** 课表合并引导卡片：醒目展示合并入口与业务说明。 */
@Composable
private fun TermMergeHeroCard(
    onStartMerge: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)),
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClick = onStartMerge),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Rounded.Merge,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(24.dp),
                )
            }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.term_merge_card_title),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = stringResource(R.string.term_merge_card_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 16.sp,
                )
            }
            Spacer(Modifier.width(8.dp))
            Surface(
                shape = RoundedCornerShape(50),
                color = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ) {
                Text(
                    text = stringResource(R.string.term_merge_card_btn),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                )
            }
        }
    }
}

@Composable
private fun TermRow(
    term: TermProfile,
    active: Boolean,
    selecting: Boolean,
    selected: Boolean,
    shiftWeeks: Int,
    canDelete: Boolean,
    onToggle: () -> Unit,
    onActivate: () -> Unit,
    onRename: () -> Unit,
    onPickDate: () -> Unit,
    onDelete: () -> Unit,
) {
    val isHighlighted = if (selecting) selected else active
    val containerColor by animateColorAsState(
        targetValue = when {
            selecting && selected -> MaterialTheme.colorScheme.primaryContainer
            selecting -> MaterialTheme.colorScheme.surface
            active -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
            else -> MaterialTheme.colorScheme.surface
        },
        animationSpec = tween(220),
        label = "TermRowContainerColor",
    )
    val borderColor by animateColorAsState(
        targetValue = when {
            selecting && selected -> MaterialTheme.colorScheme.primary
            selecting -> MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
            active -> MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
            else -> MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
        },
        animationSpec = tween(220),
        label = "TermRowBorderColor",
    )
    val onContainer = if (isHighlighted) MaterialTheme.colorScheme.onPrimaryContainer
    else MaterialTheme.colorScheme.onSurface

    val press = remember { MutableInteractionSource() }
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .pressFeedback(press)
            .clip(RoundedCornerShape(20.dp))
            .clickable(interactionSource = press, indication = LocalIndication.current) {
                if (selecting) onToggle() else if (!active) onActivate()
            },
        color = containerColor,
        border = BorderStroke(1.dp, borderColor),
        shape = RoundedCornerShape(20.dp),
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(
                            if (isHighlighted) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.surfaceVariant
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = when {
                            selecting -> if (selected) Icons.Rounded.Check else Icons.Rounded.CalendarMonth
                            active -> Icons.Rounded.Check
                            else -> Icons.Rounded.CalendarMonth
                        },
                        contentDescription = null,
                        tint = if (isHighlighted) MaterialTheme.colorScheme.onPrimary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp),
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
                if (selecting && selected && shiftWeeks > 16) {
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        shape = RoundedCornerShape(50),
                    ) {
                        Text(
                            text = stringResource(R.string.term_merge_tag_cross_year, shiftWeeks),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                        )
                    }
                } else if (active) {
                    Surface(
                        color = if (isHighlighted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(50),
                    ) {
                        Text(
                            text = stringResource(R.string.term_badge_current),
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isHighlighted) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                        )
                    }
                }
            }
            if (!selecting) {
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
}

/** 多选合并时的底部操作条：提示 + 跨学年提示 + 取消 + 合并。 */
@Composable
private fun SelectionBar(
    hint: String,
    canConfirm: Boolean,
    isLongSpan: Boolean,
    spanWeeks: Int,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 6.dp,
        shadowElevation = 8.dp,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 6.dp),
            ) {
                Icon(
                    imageVector = if (isLongSpan) Icons.Rounded.Warning else Icons.Rounded.Info,
                    contentDescription = null,
                    tint = if (isLongSpan) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = if (isLongSpan) stringResource(R.string.term_merge_span_warning_title, spanWeeks) else hint,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isLongSpan) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                AppOutlinedButton(
                    onClick = onCancel,
                    modifier = Modifier.weight(1f).height(48.dp),
                ) {
                    Text(stringResource(R.string.term_cancel))
                }
                Button(
                    onClick = onConfirm,
                    enabled = canConfirm,
                    shape = RoundedCornerShape(50),
                    modifier = Modifier.weight(1.2f).height(48.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Merge,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.term_merge_confirm))
                    }
                }
            }
        }
    }
}

/** 合并前确认：说明对齐方式、跨期智能对齐和新的开学日期，并允许改名。 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MergeDialog(
    sources: List<TermProfile>,
    activeTermId: String = "",
    onDismiss: () -> Unit,
    onConfirm: (name: String, alignment: TermMergeAlignment, anchorTermId: String) -> Unit,
) {
    val fallbackName = stringResource(R.string.term_merge_default_name)
    val defaultName = remember(sources, fallbackName) {
        sources.map { it.name.trim() }.filter { it.isNotEmpty() }.joinToString(" + ").ifEmpty { fallbackName }
    }
    var name by rememberSaveable(defaultName) { mutableStateOf(defaultName) }

    val initialAnchorId = remember(sources, activeTermId) {
        if (sources.any { it.id == activeTermId }) activeTermId
        else sources.maxByOrNull { it.termStartDate ?: "" }?.id ?: sources.firstOrNull()?.id.orEmpty()
    }
    var selectedAnchorId by rememberSaveable(initialAnchorId) { mutableStateOf(initialAnchorId) }
    val selectedAnchorTerm = sources.firstOrNull { it.id == selectedAnchorId } ?: sources.firstOrNull()

    val anchorDate = selectedAnchorTerm?.termStartDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
    val earliest = sources.mapNotNull { it.termStartDate }
        .mapNotNull { runCatching { LocalDate.parse(it) }.getOrNull() }
        .minOrNull()
    val latest = sources.mapNotNull { it.termStartDate }
        .mapNotNull { runCatching { LocalDate.parse(it) }.getOrNull() }
        .maxOrNull()
    val baseMonday = (anchorDate ?: latest ?: earliest)?.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

    val hasPastTerms = sources.any { source ->
        val sDate = source.termStartDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        if (baseMonday != null && sDate != null) {
            val sMonday = sDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            sMonday.isBefore(baseMonday)
        } else false
    }

    val maxSpanWeeks = if (earliest != null && latest != null) {
        val minMon = earliest.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val maxMon = latest.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        (ChronoUnit.DAYS.between(minMon, maxMon) / 7).toInt()
    } else 0
    val isCrossYear = maxSpanWeeks > 16 || hasPastTerms

    var alignment by rememberSaveable(isCrossYear) {
        mutableStateOf(if (isCrossYear) TermMergeAlignment.ByWeekNumber else TermMergeAlignment.ByRealDate)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(28.dp),
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Merge,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp),
                    )
                }
                Column {
                    Text(
                        text = stringResource(R.string.term_merge_title),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = stringResource(R.string.term_merge_dialog_subtitle),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        text = {
            // The dialog clips its text slot rather than scrolling it; long merges must scroll here.
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.verticalScroll(rememberScrollState()),
            ) {
                Text(
                    text = stringResource(
                        R.string.term_merge_message,
                        earliest?.let { DateTimeFormatter.ofPattern("yyyy/M/d").format(it) }
                            ?: stringResource(R.string.term_no_start_date),
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )

                // 基准学期选择
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = stringResource(R.string.term_merge_anchor_term_title),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        sources.forEach { source ->
                            val isSelected = source.id == selectedAnchorId
                            val isCurrent = source.id == activeTermId
                            AppFilterChip(
                                selected = isSelected,
                                onClick = { selectedAnchorId = source.id },
                                label = {
                                    Text(
                                        text = if (isCurrent) "${source.name} (${stringResource(R.string.term_merge_anchor_current_badge)})"
                                               else source.name,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                },
                            )
                        }
                    }
                    Text(
                        text = stringResource(R.string.term_merge_anchor_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                // 对齐模式选择
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = stringResource(R.string.term_merge_alignment_mode_title),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    // 英文标签更长，放不下时换行，别把胶囊压成圆形。
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        AppFilterChip(
                            selected = alignment == TermMergeAlignment.ByRealDate,
                            onClick = { alignment = TermMergeAlignment.ByRealDate },
                            label = { Text(stringResource(R.string.term_merge_align_by_date)) },
                        )
                        AppFilterChip(
                            selected = alignment == TermMergeAlignment.ByWeekNumber,
                            onClick = { alignment = TermMergeAlignment.ByWeekNumber },
                            label = { Text(stringResource(R.string.term_merge_align_by_week)) },
                        )
                    }
                    Text(
                        text = stringResource(
                            if (alignment == TermMergeAlignment.ByRealDate) R.string.term_merge_align_by_date_desc
                            else R.string.term_merge_align_by_week_desc
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                if ((isCrossYear || hasPastTerms) && alignment == TermMergeAlignment.ByRealDate) {
                    val pastWeeks = sources.mapNotNull { s ->
                        s.termStartDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() }?.let { sDate ->
                            if (baseMonday != null) {
                                val sMonday = sDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                                val weeks = (ChronoUnit.DAYS.between(sMonday, baseMonday) / 7).toInt()
                                if (weeks > 0) weeks else null
                            } else null
                        }
                    }.maxOrNull() ?: maxSpanWeeks

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.6f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.4f)),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.Top,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Warning,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(16.dp),
                            )
                            Text(
                                text = if (hasPastTerms) {
                                    stringResource(R.string.term_merge_past_year_warning_desc, pastWeeks)
                                } else {
                                    stringResource(R.string.term_merge_span_warning_desc, maxSpanWeeks)
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                lineHeight = 16.sp,
                            )
                        }
                    }
                }

                // 待合并学期预览
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.term_merge_source_terms),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        sources.forEach { source ->
                            val sourceDate = source.termStartDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
                            val shiftWeeks = if (baseMonday != null && sourceDate != null) {
                                val sourceMonday = sourceDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                                (ChronoUnit.DAYS.between(baseMonday, sourceMonday) / 7).toInt()
                            } else 0
                            val isBase = source.id == selectedAnchorId
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.CalendarMonth,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(16.dp),
                                )
                                Spacer(Modifier.width(8.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = source.name,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    val dateLabel = sourceDate?.let {
                                        stringResource(R.string.term_start_prefix, DateTimeFormatter.ofPattern("yyyy/M/d").format(it))
                                    } ?: stringResource(R.string.term_no_start_date)
                                    FlowRow(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalArrangement = Arrangement.spacedBy(4.dp),
                                        itemVerticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Text(
                                            text = dateLabel,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                        Surface(
                                            shape = RoundedCornerShape(50),
                                            color = when {
                                                alignment == TermMergeAlignment.ByWeekNumber -> MaterialTheme.colorScheme.primaryContainer
                                                sourceDate == null -> MaterialTheme.colorScheme.surfaceVariant
                                                isBase -> MaterialTheme.colorScheme.primaryContainer
                                                shiftWeeks < 0 || shiftWeeks > 16 -> MaterialTheme.colorScheme.errorContainer
                                                else -> MaterialTheme.colorScheme.secondaryContainer
                                            },
                                        ) {
                                            val tagText = when {
                                                alignment == TermMergeAlignment.ByWeekNumber -> stringResource(R.string.term_merge_alignment_week_direct)
                                                sourceDate == null -> stringResource(R.string.term_merge_alignment_no_date)
                                                isBase -> stringResource(R.string.term_merge_alignment_base)
                                                shiftWeeks < 0 -> stringResource(R.string.term_merge_tag_past_weeks, -shiftWeeks)
                                                shiftWeeks > 16 -> stringResource(R.string.term_merge_tag_cross_year, shiftWeeks)
                                                else -> stringResource(R.string.term_merge_alignment_shift, shiftWeeks)
                                            }
                                            Text(
                                                text = tagText,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = when {
                                                    alignment == TermMergeAlignment.ByWeekNumber -> MaterialTheme.colorScheme.onPrimaryContainer
                                                    sourceDate == null -> MaterialTheme.colorScheme.onSurfaceVariant
                                                    isBase -> MaterialTheme.colorScheme.onPrimaryContainer
                                                    shiftWeeks < 0 || shiftWeeks > 16 -> MaterialTheme.colorScheme.onErrorContainer
                                                    else -> MaterialTheme.colorScheme.onSecondaryContainer
                                                },
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.term_new_name_label)) },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    leadingIcon = {
                        Icon(Icons.Rounded.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                    },
                    trailingIcon = {
                        if (name.isNotEmpty()) {
                            IconButton(onClick = { name = "" }) {
                                Icon(Icons.Rounded.Clear, contentDescription = null, modifier = Modifier.size(18.dp))
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            Button(
                enabled = name.isNotBlank(),
                onClick = { onConfirm(name.trim(), alignment, selectedAnchorId) },
                shape = RoundedCornerShape(50),
                modifier = Modifier.height(44.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Merge,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.term_merge_confirm))
                }
            }
        },
        dismissButton = {
            AppOutlinedButton(
                onClick = onDismiss,
                modifier = Modifier.height(44.dp),
            ) {
                Text(stringResource(R.string.term_cancel))
            }
        },
    )
}

@Composable
private fun ActionChip(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    destructive: Boolean = false,
) {
    val container = if (destructive) MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)
    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
    val onContainer = if (destructive) MaterialTheme.colorScheme.onErrorContainer
    else MaterialTheme.colorScheme.onSurfaceVariant
    val borderStroke = if (destructive) {
        BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.3f))
    } else {
        BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
    }
    Surface(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .clickable(onClick = onClick),
        color = container,
        border = borderStroke,
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
    var date by remember { mutableStateOf<LocalDate?>(BeijingTime.today()) }
    var showDate by rememberSaveable { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(28.dp),
        title = { Text(stringResource(R.string.term_create_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.term_name_label)) },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    leadingIcon = {
                        Icon(Icons.Rounded.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .clickable { showDate = true },
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.Rounded.CalendarMonth,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(Modifier.width(10.dp))
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
            AppOutlinedButton(
                enabled = name.isNotBlank(),
                onClick = { onConfirm(name.trim(), date) },
                modifier = Modifier.height(44.dp),
            ) { Text(stringResource(R.string.term_create_confirm)) }
        },
        dismissButton = {
            AppOutlinedButton(
                onClick = onDismiss,
                modifier = Modifier.height(44.dp),
            ) { Text(stringResource(R.string.term_cancel)) }
        },
    )
    if (showDate) {
        TermDatePickerDialog(
            initial = date ?: BeijingTime.today(),
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
        shape = RoundedCornerShape(28.dp),
        title = { Text(stringResource(R.string.term_rename_title)) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                leadingIcon = {
                    Icon(Icons.Rounded.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                },
                label = { Text(stringResource(R.string.term_new_name_label)) },
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            AppOutlinedButton(
                enabled = name.isNotBlank(),
                onClick = { onConfirm(name.trim()) },
                modifier = Modifier.height(44.dp),
            ) { Text(stringResource(R.string.term_save)) }
        },
        dismissButton = {
            AppOutlinedButton(
                onClick = onDismiss,
                modifier = Modifier.height(44.dp),
            ) { Text(stringResource(R.string.term_cancel)) }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TermDatePickerDialog(
    initial: LocalDate,
    onDismiss: () -> Unit,
    onConfirm: (LocalDate) -> Unit,
) {
    var selectedDate by remember(initial) { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(28.dp),
        title = { Text(stringResource(R.string.term_action_date)) },
        confirmButton = {
            AppOutlinedButton(
                onClick = { onConfirm(selectedDate) },
                modifier = Modifier.height(44.dp),
            ) {
                Text(stringResource(R.string.term_date_confirm))
            }
        },
        dismissButton = {
            AppOutlinedButton(
                onClick = onDismiss,
                modifier = Modifier.height(44.dp),
            ) { Text(stringResource(R.string.term_cancel)) }
        },
        text = {
            CalendarMonthPicker(selected = selectedDate, onSelect = { selectedDate = it })
        },
    )
}
