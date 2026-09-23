package com.x500x.cursimple.app

import com.x500x.cursimple.feature.plugin.ui.AppOutlinedButton
import androidx.compose.foundation.background
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.x500x.cursimple.R
import com.x500x.cursimple.core.kernel.model.CourseItem
import com.x500x.cursimple.feature.schedule.ScheduleImportDiff

/**
 * 导入前的变更确认。
 *
 * 课表盖掉就没了，而教务系统每学期的表都可能大改。这里把增减摆出来，
 * 再让用户选是覆盖当前这份，还是另存成一个新课表（原来那份留在旧学期里，
 * 从右上角加号能切回去）。折叠样式沿用清空课表那个确认框，两处观感一致。
 */
@Composable
fun ImportDiffDialog(
    diff: ScheduleImportDiff,
    suggestedTermName: String,
    onOverwrite: () -> Unit,
    onCreateNewTerm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var namingNewTerm by remember { mutableStateOf(false) }
    var termName by remember(suggestedTermName) { mutableStateOf(suggestedTermName) }

    if (namingNewTerm) {
        AlertDialog(
            onDismissRequest = { namingNewTerm = false },
            title = { Text(stringResource(R.string.import_diff_new_term_title)) },
            text = {
                OutlinedTextField(
                    value = termName,
                    onValueChange = { termName = it },
                    singleLine = true,
                    label = { Text(stringResource(R.string.import_diff_new_term_label)) },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                )
            },
            confirmButton = {
                AppOutlinedButton(
                    enabled = termName.isNotBlank(),
                    onClick = {
                        namingNewTerm = false
                        onCreateNewTerm(termName.trim())
                    },
                ) { Text(stringResource(R.string.import_diff_new_term_confirm)) }
            },
            dismissButton = {
                AppOutlinedButton(onClick = { namingNewTerm = false }) {
                    Text(stringResource(R.string.import_diff_cancel))
                }
            },
        )
        return
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.import_diff_title)) },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = stringResource(R.string.import_diff_intro),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (diff.added.isNotEmpty()) {
                    CourseChangeSection(
                        header = stringResource(R.string.import_diff_added_header, diff.added.size),
                        courses = diff.added,
                        dotColor = MaterialTheme.colorScheme.primary,
                    )
                }
                if (diff.removed.isNotEmpty()) {
                    CourseChangeSection(
                        header = stringResource(R.string.import_diff_removed_header, diff.removed.size),
                        courses = diff.removed,
                        dotColor = MaterialTheme.colorScheme.error,
                    )
                }
                if (diff.keptCount > 0) {
                    Text(
                        text = stringResource(R.string.import_diff_kept, diff.keptCount),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = {
            AppOutlinedButton(onClick = { namingNewTerm = true }) {
                Text(stringResource(R.string.import_diff_new_term))
            }
        },
        dismissButton = {
            Row {
                AppOutlinedButton(onClick = onDismiss) {
                    Text(stringResource(R.string.import_diff_cancel))
                }
                AppOutlinedButton(onClick = onOverwrite) {
                    Text(
                        text = stringResource(R.string.import_diff_overwrite),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
    )
}

/** 一组变动的课，超过 5 门折起来。 */
@Composable
private fun CourseChangeSection(
    header: String,
    courses: List<CourseItem>,
    dotColor: Color,
) {
    var expanded by remember { mutableStateOf(false) }
    val previewLimit = 5
    val needsFold = courses.size > previewLimit
    val visible = if (expanded || !needsFold) courses else courses.take(previewLimit)

    Column {
        Text(
            text = header,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.size(6.dp))
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = RoundedCornerShape(12.dp),
        ) {
            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                visible.forEachIndexed { index, course ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(dotColor),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = course.changeLineText(),
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    if (index < visible.lastIndex) Spacer(Modifier.size(4.dp))
                }
            }
        }
        if (needsFold) {
            Spacer(Modifier.size(6.dp))
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .clickable { expanded = !expanded },
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(10.dp),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = if (expanded) {
                            stringResource(R.string.clear_collapse_list)
                        } else {
                            androidx.compose.ui.res.pluralStringResource(
                                R.plurals.clear_expand_more,
                                courses.size - previewLimit,
                                courses.size - previewLimit,
                            )
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }
}

@Composable
private fun CourseItem.changeLineText(): String {
    val nodes = if (time.startNode == time.endNode) {
        time.startNode.toString()
    } else {
        "${time.startNode}-${time.endNode}"
    }
    val place = location.ifBlank { stringResource(R.string.import_diff_course_no_place) }
    return stringResource(
        R.string.import_diff_course_line,
        title,
        weekdayShortText(time.dayOfWeek),
        nodes,
        place,
    )
}

@Composable
private fun weekdayShortText(dayOfWeek: Int): String = stringResource(
    com.x500x.cursimple.core.kernel.model.weekdayNameRes(dayOfWeek),
)
