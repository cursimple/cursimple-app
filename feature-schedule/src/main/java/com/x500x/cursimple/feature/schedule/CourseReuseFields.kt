package com.x500x.cursimple.feature.schedule

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.ArrowDropUp
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

internal fun filterCourseReuseOptions(options: List<CourseReuseOption>, query: String): List<CourseReuseOption> =
    query.trim().let { text -> options.filter { it.value.contains(text, true) || it.detail.contains(text, true) } }

@Composable
internal fun CourseTitleField(value: String, onChange: (String) -> Unit, courses: List<CourseItem>, onReuse: (CourseItem) -> Unit) {
    val options = remember(courses) { courses.map { course ->
        CourseReuseOption(course.title.trim(), listOf(course.teacher.trim(), course.location.trim()).filter(String::isNotEmpty).joinToString(" · "), course)
    } }
    CourseReuseTextField(value, onChange, stringResource(R.string.schedule_course_name_label), options,
        onSelect = { option -> option.course?.let(onReuse) }, tag = "course-title")
}

/** Editable autocomplete; explicitly opening the arrow always lists all saved values. */
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
    var filtering by remember { mutableStateOf(false) }
    val filtered = remember(options, filtering, value) { if (filtering) filterCourseReuseOptions(options, value) else options }
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val scroll = rememberScrollState()
    ExposedDropdownMenuBox(expanded = expanded && filtered.isNotEmpty() && enabled,
        onExpandedChange = { filtering = false; expanded = it && enabled && options.isNotEmpty() },
        modifier = modifier.fillMaxWidth()) {
        OutlinedTextField(value, onValueChange = {
            onChange(it)
            filtering = true
            expanded = options.isNotEmpty()
        }, enabled = enabled, singleLine = true,
            label = { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            placeholder = placeholder?.let { { Text(it, maxLines = 1, overflow = TextOverflow.Ellipsis) } },
            supportingText = supportingText, keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            trailingIcon = if (options.isNotEmpty() || extraAction != null) ({
                Row {
                    if (options.isNotEmpty()) AppCircleIconButton(
                        onClick = { filtering = false; expanded = !expanded }, enabled = enabled,
                        modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.SecondaryEditable)
                            .testTag("$tag-reuse"),
                    ) {
                        Icon(if (expanded) Icons.Rounded.ArrowDropUp else Icons.Rounded.ArrowDropDown,
                            stringResource(R.string.schedule_reuse_values))
                    }
                    extraAction?.invoke()
                }
            }) else null,
            modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryEditable)
                .testTag("$tag-field"),
        )
        ExposedDropdownMenu(expanded = expanded && filtered.isNotEmpty() && enabled,
            onDismissRequest = { expanded = false }, scrollState = scroll,
            modifier = Modifier.heightIn(max = 240.dp).testTag("$tag-reuse-menu")) {
            filtered.forEachIndexed { index, option ->
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
