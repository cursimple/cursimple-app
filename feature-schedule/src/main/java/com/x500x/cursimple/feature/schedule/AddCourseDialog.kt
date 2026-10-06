package com.x500x.cursimple.feature.schedule

import com.x500x.cursimple.core.kernel.model.DEFAULT_TERM_WEEK_COUNT
import com.x500x.cursimple.feature.plugin.ui.AppOutlinedButton
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.x500x.cursimple.core.kernel.model.CourseItem

@Composable
fun AddCourseDialog(
    onDismiss: () -> Unit,
    onConfirm: (CourseItem) -> Unit,
    existingCourses: List<CourseItem> = emptyList(),
    maxNodeCount: Int = 12,
    maxWeekCount: Int = DEFAULT_TERM_WEEK_COUNT,
    initial: CourseItem? = null,
) {
    // Null drafts indicate input that cannot be saved.
    var draft by remember(initial) { mutableStateOf<CourseItem?>(null) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 24.dp)
                .heightIn(max = 640.dp),
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 4.dp,
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    text = stringResource(
                        if (initial == null) {
                            R.string.schedule_add_course_title
                        } else {
                            R.string.schedule_edit_course_title
                        },
                    ),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                )

                Spacer(modifier = Modifier.height(12.dp))

                CourseEditFormFields(
                    initial = initial,
                    existingCourses = existingCourses,
                    maxNodeCount = maxNodeCount,
                    maxWeekCount = maxWeekCount,
                    onDraftChange = { draft = it },
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState()),
                )

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    AppOutlinedButton(onClick = onDismiss) { Text(stringResource(R.string.schedule_action_cancel)) }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = { draft?.let(onConfirm) },
                        enabled = draft != null,
                    ) { Text(stringResource(R.string.schedule_action_save)) }
                }
            }
        }
    }
}

/**
 * Conflicts are advisory, using tertiary rather than error styling; saving remains available.
 */
@Composable
internal fun CourseConflictWarning(warning: AddCourseConflictWarning) {
    val text = LocalContext.current.addCourseConflictWarningText(warning)
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.tertiaryContainer,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                imageVector = Icons.Rounded.Warning,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onTertiaryContainer,
                modifier = Modifier.size(18.dp),
            )
            Text(
                text = text,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onTertiaryContainer,
            )
        }
    }
}
