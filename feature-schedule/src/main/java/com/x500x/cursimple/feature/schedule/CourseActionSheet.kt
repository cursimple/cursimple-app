package com.x500x.cursimple.feature.schedule

import com.x500x.cursimple.feature.plugin.ui.AppFilterChip
import com.x500x.cursimple.feature.plugin.ui.AppOutlinedButton
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Checklist
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.OpenWith
import androidx.compose.material.icons.rounded.SettingsBackupRestore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.x500x.cursimple.core.kernel.model.CourseItem
import com.x500x.cursimple.core.kernel.model.CourseTimeSlot

/**
 * 长按课程弹出的操作面板。
 *
 * 长按以前只能进多选设提醒，改课得先点开详情再找编辑；这里把编辑、移动、删除
 * 直接摆出来，多选仍留一个入口，批量设提醒的老路子不丢。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CourseActionSheet(
    course: CourseItem,
    manual: Boolean,
    pluginOverride: Boolean,
    onDismiss: () -> Unit,
    onEdit: () -> Unit,
    onMove: () -> Unit,
    onSetReminder: () -> Unit,
    onMultiSelect: () -> Unit,
    onDelete: () -> Unit,
    onRestorePlugin: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var confirmDelete by remember { mutableStateOf(false) }
    var confirmRestore by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = course.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
            )
            Text(
                text = courseActionSubtitle(course),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.size(8.dp))

            CourseActionRow(
                icon = Icons.Rounded.Edit,
                title = stringResource(R.string.schedule_action_edit),
                subtitle = stringResource(
                    if (manual) {
                        R.string.schedule_course_action_edit_desc
                    } else {
                        R.string.schedule_course_action_edit_plugin_desc
                    },
                ),
                onClick = onEdit,
            )
            CourseActionRow(
                icon = Icons.Rounded.OpenWith,
                title = stringResource(R.string.schedule_course_action_move),
                subtitle = stringResource(R.string.schedule_course_action_move_desc),
                onClick = onMove,
            )
            CourseActionRow(
                icon = Icons.Rounded.NotificationsActive,
                title = stringResource(R.string.schedule_course_detail_set_reminder),
                subtitle = stringResource(R.string.schedule_course_action_reminder_desc),
                onClick = onSetReminder,
            )
            CourseActionRow(
                icon = Icons.Rounded.Checklist,
                title = stringResource(R.string.schedule_course_action_multi_select),
                subtitle = stringResource(R.string.schedule_course_action_multi_select_desc),
                onClick = onMultiSelect,
            )
            if (pluginOverride) {
                CourseActionRow(
                    icon = Icons.Rounded.SettingsBackupRestore,
                    title = stringResource(R.string.schedule_action_restore_plugin),
                    subtitle = stringResource(R.string.schedule_course_action_restore_desc),
                    onClick = { confirmRestore = true },
                )
            }
            if (manual && !pluginOverride) {
                CourseActionRow(
                    icon = Icons.Rounded.DeleteOutline,
                    title = stringResource(R.string.schedule_action_delete),
                    subtitle = stringResource(R.string.schedule_course_action_delete_desc),
                    tint = MaterialTheme.colorScheme.error,
                    onClick = { confirmDelete = true },
                )
            }
            Spacer(Modifier.size(8.dp))
        }
    }

    // 删除与还原都不可撤销，落库前再问一次，防手误
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.schedule_library_delete_title)) },
            text = { Text(stringResource(R.string.schedule_library_delete_body, course.title)) },
            confirmButton = {
                AppOutlinedButton(onClick = {
                    confirmDelete = false
                    onDelete()
                }) { Text(stringResource(R.string.schedule_library_delete_confirm)) }
            },
            dismissButton = {
                AppOutlinedButton(onClick = { confirmDelete = false }) {
                    Text(stringResource(R.string.schedule_action_cancel))
                }
            },
        )
    }

    if (confirmRestore) {
        AlertDialog(
            onDismissRequest = { confirmRestore = false },
            title = { Text(stringResource(R.string.schedule_course_detail_restore_plugin_title)) },
            text = { Text(stringResource(R.string.schedule_course_detail_restore_plugin_body, course.title)) },
            confirmButton = {
                AppOutlinedButton(onClick = {
                    confirmRestore = false
                    onRestorePlugin()
                }) { Text(stringResource(R.string.schedule_action_restore_plugin)) }
            },
            dismissButton = {
                AppOutlinedButton(onClick = { confirmRestore = false }) {
                    Text(stringResource(R.string.schedule_action_cancel))
                }
            },
        )
    }
}

@Composable
private fun CourseActionRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    tint: Color = MaterialTheme.colorScheme.onSurface,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
    ) {
        Row(
            modifier = Modifier
                .clickable(onClick = onClick)
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(22.dp),
            )
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = tint,
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun courseActionSubtitle(course: CourseItem): String {
    val weekday = stringResource(scheduleWeekdayFullRes(course.time.dayOfWeek))
    val nodes = if (course.time.startNode == course.time.endNode) {
        stringResource(R.string.schedule_node_single, course.time.startNode)
    } else {
        stringResource(R.string.schedule_node_range, course.time.startNode, course.time.endNode)
    }
    return listOfNotNull(
        "$weekday · $nodes",
        course.location.takeIf { it.isNotBlank() },
    ).joinToString(" · ")
}

/**
 * 「移动到哪」的选择器。
 *
 * 星期与起始节次分开挑，课程本身的节数保持不变，所以只需要定起点。
 * 落点被别的课占了也照样允许——课表里本来就有同一格多门课的情况，
 * 这里只把重叠的课名提示出来，由用户自己判断。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun MoveCourseDialog(
    course: CourseItem,
    maxNodeCount: Int,
    existingCourses: List<CourseItem>,
    onDismiss: () -> Unit,
    onConfirm: (CourseTimeSlot) -> Unit,
) {
    val span = (course.time.endNode - course.time.startNode + 1).coerceAtLeast(1)
    // 课程本来就排在作息表之外时不能把它夹回来，否则一打开选择器就等于改了位置
    val nodeCeiling = maxOf(maxNodeCount, course.time.endNode)
    val maxStartNode = (nodeCeiling - span + 1).coerceAtLeast(1)
    var dayOfWeek by remember(course.id) { mutableIntStateOf(course.time.dayOfWeek.coerceIn(1, 7)) }
    var startNode by remember(course.id) {
        mutableIntStateOf(course.time.startNode.coerceIn(1, maxStartNode))
    }
    val endNode = startNode + span - 1
    val target = CourseTimeSlot(dayOfWeek = dayOfWeek, startNode = startNode, endNode = endNode)
    val unchanged = target == course.time

    // 同一格上已有的课：只提示，不拦截
    val overlapping = remember(existingCourses, course.id, dayOfWeek, startNode, endNode) {
        existingCourses.filter {
            it.id != course.id &&
                it.time.dayOfWeek == dayOfWeek &&
                it.time.startNode <= endNode &&
                it.time.endNode >= startNode
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.schedule_move_dialog_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = course.title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                )

                Text(
                    text = stringResource(R.string.schedule_move_dialog_weekday),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    (1..7).forEach { day ->
                        AppFilterChip(
                            selected = dayOfWeek == day,
                            onClick = { dayOfWeek = day },
                            label = { Text(stringResource(scheduleWeekdayFullRes(day)), maxLines = 1) },
                        )
                    }
                }

                Text(
                    text = stringResource(R.string.schedule_move_dialog_start_node),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    IconButton(
                        onClick = { startNode = (startNode - 1).coerceAtLeast(1) },
                        enabled = startNode > 1,
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Remove,
                            contentDescription = stringResource(R.string.schedule_move_dialog_earlier),
                        )
                    }
                    Surface(
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                    ) {
                        Box(
                            modifier = Modifier.padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = if (span == 1) {
                                    stringResource(R.string.schedule_node_single, startNode)
                                } else {
                                    stringResource(R.string.schedule_node_range, startNode, endNode)
                                },
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                    IconButton(
                        onClick = { startNode = (startNode + 1).coerceAtMost(maxStartNode) },
                        enabled = startNode < maxStartNode,
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Add,
                            contentDescription = stringResource(R.string.schedule_move_dialog_later),
                        )
                    }
                }

                if (overlapping.isNotEmpty()) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.tertiaryContainer,
                    ) {
                        Text(
                            text = stringResource(
                                R.string.schedule_move_dialog_overlap,
                                overlapping.joinToString("、") { it.title },
                            ),
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                        )
                    }
                }
            }
        },
        confirmButton = {
            AppOutlinedButton(onClick = { onConfirm(target) }, enabled = !unchanged) {
                Text(stringResource(R.string.schedule_move_dialog_confirm))
            }
        },
        dismissButton = {
            AppOutlinedButton(onClick = onDismiss) { Text(stringResource(R.string.schedule_action_cancel)) }
        },
    )
}
