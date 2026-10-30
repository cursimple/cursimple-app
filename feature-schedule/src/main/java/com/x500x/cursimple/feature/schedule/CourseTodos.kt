package com.x500x.cursimple.feature.schedule

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.x500x.cursimple.core.kernel.model.CourseItem
import com.x500x.cursimple.core.kernel.model.MemoNote
import com.x500x.cursimple.core.kernel.model.memoCourseKey
import com.x500x.cursimple.feature.plugin.ui.AppFilterChip
import com.x500x.cursimple.feature.plugin.ui.AppOutlinedButton
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.UUID

/** Open to-do counts by course notebook key, provided once by the schedule screen. */
internal val LocalPendingTodoCounts = androidx.compose.runtime.compositionLocalOf<Map<String, Int>> { emptyMap() }

/** To-dos filed under the course's notebook; open ones first, soonest deadline first. */
internal fun courseTodosFor(memos: List<MemoNote>, course: CourseItem): List<MemoNote> =
    memos.filter { it.courseKey == memoCourseKey(course.title) }
        .sortedWith(compareBy<MemoNote>({ it.completed }, { it.dueDateTime ?: LocalDateTime.MAX }, { -it.updatedAt }))

/** Open to-do count per course notebook key, for the small number on each course card. */
internal fun pendingTodoCountsByCourse(memos: List<MemoNote>): Map<String, Int> =
    memos.filter { !it.completed && it.courseKey != null }.groupingBy { it.courseKey!! }.eachCount()

/** A new to-do in the course's notebook; [due] null means no deadline. */
internal fun newCourseTodo(course: CourseItem, title: String, due: LocalDate?, nowMillis: Long = System.currentTimeMillis()): MemoNote =
    MemoNote(
        id = "memo-${UUID.randomUUID()}",
        courseKey = memoCourseKey(course.title),
        courseTitle = course.title.trim(),
        title = title.trim(),
        dueAt = due?.atTime(LocalTime.of(23, 59))?.toString(),
        createdAt = nowMillis,
        updatedAt = nowMillis,
    )

/**
 * Homework and to-dos of one course inside its detail: tick them off, add a new one with a quick deadline.
 * They are ordinary notes in the course's notebook, so they also show up in Notes and on the to-do widget.
 */
@Composable
internal fun CourseTodosSection(
    course: CourseItem,
    todos: List<MemoNote>,
    onSave: (MemoNote) -> Unit,
    /** The day of the class being viewed; "next class" deadlines count from here. */
    classDate: LocalDate?,
    readOnly: Boolean,
    today: LocalDate,
) {
    var adding by rememberSaveable(course.id) { mutableStateOf(false) }
    val open = todos.filter { !it.completed }
    val doneCount = todos.size - open.size
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
        modifier = Modifier.fillMaxWidth().testTag("course-todos-section"),
    ) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.course_todos_title),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                if (open.isNotEmpty()) {
                    Text(
                        text = stringResource(R.string.course_todos_open_count, open.size),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.testTag("course-todos-count"),
                    )
                }
            }
            if (open.isEmpty()) {
                Text(
                    text = stringResource(R.string.course_todos_none),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            open.take(MAX_SHOWN_TODOS).forEach { todo ->
                CourseTodoRow(todo, today, readOnly) { onSave(todo.copy(completed = true, updatedAt = System.currentTimeMillis())) }
            }
            if (open.size > MAX_SHOWN_TODOS) {
                Text(
                    text = stringResource(R.string.course_todos_more, open.size - MAX_SHOWN_TODOS),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (doneCount > 0) {
                Text(
                    text = stringResource(R.string.course_todos_done_count, doneCount),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (!readOnly) {
                AppOutlinedButton(onClick = { adding = true }, modifier = Modifier.testTag("course-todo-add")) {
                    Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text(stringResource(R.string.course_todos_add), modifier = Modifier.padding(start = 6.dp))
                }
            }
        }
    }
    if (adding) {
        CourseTodoDialog(
            courseTitle = course.title,
            classDate = classDate ?: today,
            today = today,
            onDismiss = { adding = false },
            onConfirm = { title, due ->
                onSave(newCourseTodo(course, title, due))
                adding = false
            },
        )
    }
}

private const val MAX_SHOWN_TODOS = 4

@Composable
private fun CourseTodoRow(todo: MemoNote, today: LocalDate, readOnly: Boolean, onComplete: () -> Unit) {
    val due = todo.dueDateTime
    val overdue = due != null && due.toLocalDate().isBefore(today)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .let { if (readOnly) it else it.clickable(onClick = onComplete) }
            .testTag("course-todo-row-${todo.id}"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            imageVector = Icons.Rounded.RadioButtonUnchecked,
            contentDescription = stringResource(R.string.course_todos_mark_done),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(22.dp),
        )
        Column(modifier = Modifier.weight(1f).padding(vertical = 6.dp)) {
            Text(
                text = todo.title.ifBlank { todo.body.lineSequence().firstOrNull().orEmpty() },
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
            )
            if (due != null) {
                Text(
                    text = stringResource(R.string.course_todos_due, due.monthValue, due.dayOfMonth),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (overdue) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CourseTodoDialog(
    courseTitle: String,
    classDate: LocalDate,
    today: LocalDate,
    onDismiss: () -> Unit,
    onConfirm: (title: String, due: LocalDate?) -> Unit,
) {
    var title by rememberSaveable { mutableStateOf("") }
    // Offsets from today; -1 stands for "no deadline" and 0 for the next class below.
    var choice by rememberSaveable { mutableStateOf(DueChoice.NextClass) }
    var picked by rememberSaveable { mutableStateOf<String?>(null) }
    var pickingDate by remember { mutableStateOf(false) }
    val nextClass = classDate.plusDays(7).takeIf { !it.isBefore(today) } ?: today
    val due: LocalDate? = when (choice) {
        DueChoice.None -> null
        DueChoice.Tomorrow -> today.plusDays(1)
        DueChoice.NextClass -> nextClass
        DueChoice.Custom -> picked?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
    }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.course_todos_dialog_title, courseTitle)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text(stringResource(R.string.course_todos_field)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("course-todo-title"),
                )
                Text(stringResource(R.string.course_todos_deadline), style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    AppFilterChip(
                        selected = choice == DueChoice.NextClass,
                        onClick = { choice = DueChoice.NextClass },
                        label = { Text(stringResource(R.string.course_todos_due_next_class)) },
                        modifier = Modifier.testTag("course-todo-due-next"),
                    )
                    AppFilterChip(
                        selected = choice == DueChoice.Tomorrow,
                        onClick = { choice = DueChoice.Tomorrow },
                        label = { Text(stringResource(R.string.course_todos_due_tomorrow)) },
                        modifier = Modifier.testTag("course-todo-due-tomorrow"),
                    )
                    AppFilterChip(
                        selected = choice == DueChoice.Custom,
                        onClick = { choice = DueChoice.Custom; pickingDate = true },
                        label = {
                            Text(picked?.takeIf { choice == DueChoice.Custom } ?: stringResource(R.string.course_todos_due_pick))
                        },
                        modifier = Modifier.testTag("course-todo-due-custom"),
                    )
                    AppFilterChip(
                        selected = choice == DueChoice.None,
                        onClick = { choice = DueChoice.None },
                        label = { Text(stringResource(R.string.course_todos_due_none)) },
                        modifier = Modifier.testTag("course-todo-due-none"),
                    )
                }
                if (due != null) {
                    Text(
                        text = stringResource(R.string.course_todos_due, due.monthValue, due.dayOfMonth),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = {
            AppOutlinedButton(
                onClick = { onConfirm(title, due) },
                enabled = title.isNotBlank() && (choice != DueChoice.Custom || due != null),
                modifier = Modifier.testTag("course-todo-save"),
            ) { Text(stringResource(R.string.schedule_action_save)) }
        },
        dismissButton = { AppOutlinedButton(onClick = onDismiss) { Text(stringResource(R.string.schedule_action_cancel)) } },
    )
    if (pickingDate) {
        AlarmDatePickerDialog(
            initial = picked?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: nextClass,
            onDismiss = { pickingDate = false },
            onPick = { date -> picked = date.toString(); pickingDate = false },
        )
    }
}

private enum class DueChoice { NextClass, Tomorrow, Custom, None }
