package com.x500x.cursimple.feature.schedule

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.ArrowDropUp
import androidx.compose.material.icons.rounded.EditCalendar
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.x500x.cursimple.core.kernel.model.CourseItem
import com.x500x.cursimple.feature.plugin.ui.AppCircleIconButton

val LocalCourseReuseCatalog = staticCompositionLocalOf<List<CourseItem>> { emptyList() }

internal data class CourseReuseValues(val courses: List<CourseItem>, val teachers: List<String>, val locations: List<String>)

internal fun courseReuseValues(courses: List<CourseItem>): CourseReuseValues {
    val visible = courses.filterNot { it.hidden || it.reminderOnly }
    fun values(selector: (CourseItem) -> List<String>) = visible.flatMap(selector)
        .map(String::trim).filter(String::isNotEmpty).distinct().sortedWith(String.CASE_INSENSITIVE_ORDER)
    return CourseReuseValues(
        courses = visible.filter { it.title.isNotBlank() }
            .distinctBy { listOf(it.title.trim(), it.teacher.trim(), it.location.trim(), it.category.name) },
        teachers = values { listOf(it.teacher) + it.weekTeachers.toSortedMap().values },
        locations = values { listOf(it.location) + it.weekLocations.toSortedMap().values },
    )
}

@Composable
internal fun rememberCourseReuseValues(existing: List<CourseItem>, initial: CourseItem? = null): CourseReuseValues {
    val history = LocalCourseReuseCatalog.current
    return remember(existing, initial, history) { courseReuseValues(listOfNotNull(initial) + existing + history) }
}

internal data class CourseReuseOption(val value: String, val detail: String = "", val course: CourseItem? = null)

@Composable
internal fun CourseTitleField(
    value: String,
    onChange: (String) -> Unit,
    courses: List<CourseItem>,
    onReuse: (CourseItem) -> Unit,
    weekTitles: Map<Int, String> = emptyMap(),
    onPickWeekTitles: (() -> Unit)? = null,
) {
    val options = remember(courses) { courses.map { course ->
        CourseReuseOption(course.title.trim(), listOf(course.teacher.trim(), course.location.trim()).filter(String::isNotEmpty).joinToString(" · "), course)
    } }
    CourseReuseTextField(
        value = value,
        onChange = onChange,
        label = stringResource(R.string.schedule_course_name_label),
        options = options,
        onSelect = { option -> option.course?.let(onReuse) },
        tag = "course-title",
        extraAction = onPickWeekTitles?.let { onClick ->
            {
                AppCircleIconButton(
                    onClick = onClick,
                    modifier = Modifier.testTag("course-title-weeks"),
                    size = FieldActionSize,
                ) {
                    Icon(
                        Icons.Rounded.EditCalendar,
                        stringResource(R.string.schedule_week_title_dialog_title),
                        Modifier.size(FieldActionIconSize),
                        tint = if (weekTitles.isNotEmpty()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        supportingText = weekTitles.size.takeIf { it > 0 }?.let { count -> {
            Text(stringResource(R.string.schedule_week_title_summary, count), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
        } },
    )
}

/** Free text with saved values behind the arrow; typing or tapping the field never opens the list. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CourseReuseTextField(
    value: String,
    onChange: (String) -> Unit,
    label: String,
    options: List<CourseReuseOption>,
    modifier: Modifier = Modifier,
    tag: String,
    enabled: Boolean = true,
    placeholder: String? = null,
    supportingText: (@Composable () -> Unit)? = null,
    extraAction: (@Composable () -> Unit)? = null,
    onSelect: (CourseReuseOption) -> Unit = { onChange(it.value) },
) {
    var expanded by remember { mutableStateOf(false) }
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val scroll = rememberScrollState()
    ExposedDropdownMenuBox(expanded = expanded && options.isNotEmpty() && enabled,
        onExpandedChange = { expanded = it && enabled && options.isNotEmpty() },
        modifier = modifier.fillMaxWidth()) {
        OutlinedTextField(value, onValueChange = onChange, enabled = enabled, singleLine = true,
            label = { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            placeholder = placeholder?.let { { Text(it, maxLines = 1, overflow = TextOverflow.Ellipsis) } },
            supportingText = supportingText, keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            trailingIcon = if (options.isNotEmpty() || extraAction != null) ({
                Row {
                    if (options.isNotEmpty()) AppCircleIconButton(
                        onClick = { expanded = !expanded }, enabled = enabled, size = FieldActionSize,
                        modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.SecondaryEditable)
                            .testTag("$tag-reuse"),
                    ) {
                        Icon(if (expanded) Icons.Rounded.ArrowDropUp else Icons.Rounded.ArrowDropDown,
                            stringResource(R.string.schedule_reuse_values), Modifier.size(FieldActionIconSize))
                    }
                    extraAction?.invoke()
                }
            }) else null,
            // The field only positions the menu; its taps place the cursor.
            modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryEditable, enabled = false)
                .testTag("$tag-field"),
        )
        ExposedDropdownMenu(expanded = expanded && options.isNotEmpty() && enabled,
            onDismissRequest = { expanded = false }, scrollState = scroll,
            modifier = Modifier.heightIn(max = 240.dp).testTag("$tag-reuse-menu")) {
            options.forEachIndexed { index, option ->
                DropdownMenuItem(
                    text = {
                        androidx.compose.foundation.layout.Column {
                            Text(option.value, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            if (option.detail.isNotEmpty()) Text(option.detail, style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    },
                    onClick = {
                        expanded = false
                        onSelect(option)
                        focus.clearFocus()
                        keyboard?.hide()
                    },
                    modifier = Modifier.testTag("$tag-reuse-option-$index"),
                )
            }
        }
    }
}

/** Field actions draw smaller than toolbar buttons so the value keeps the room. */
internal val FieldActionSize = 36.dp
internal val FieldActionIconSize = 20.dp
