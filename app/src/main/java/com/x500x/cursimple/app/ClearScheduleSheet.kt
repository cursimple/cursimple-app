package com.x500x.cursimple.app

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CleaningServices
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.x500x.cursimple.core.kernel.model.CourseItem
import kotlinx.coroutines.launch

enum class ClearScope { ManualOnly, ImportedOnly, Everything }

private data class ClearOption(
    val scope: ClearScope,
    val title: String,
    val description: String,
    val icon: ImageVector,
    val accent: Boolean = false,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClearScheduleSheet(
    manualCourses: List<CourseItem>,
    importedCourses: List<CourseItem>,
    onDismiss: () -> Unit,
    onConfirm: (ClearScope) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    var pendingScope by remember { mutableStateOf<ClearScope?>(null) }

    val options = listOf(
        ClearOption(
            scope = ClearScope.ManualOnly,
            title = "清空手动添加的课程",
            description = "仅删除你手动添加的 ${manualCourses.size} 门课程",
            icon = Icons.Rounded.Edit,
        ),
        ClearOption(
            scope = ClearScope.ImportedOnly,
            title = "清空导入的课程",
            description = "仅删除插件同步的 ${importedCourses.size} 门课程",
            icon = Icons.Rounded.CloudDownload,
        ),
        ClearOption(
            scope = ClearScope.Everything,
            title = "全部清空",
            description = "手动课程、导入课程，以及所有提醒规则",
            icon = Icons.Rounded.CleaningServices,
            accent = true,
        ),
    )

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = "清空课表",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "选择要清除的范围。所有清除操作都不可恢复，会再次确认。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.size(2.dp))
            options.forEach { opt ->
                ClearOptionRow(opt = opt, onClick = { pendingScope = opt.scope })
            }
            Spacer(Modifier.size(4.dp))
        }
    }

    pendingScope?.let { selected ->
        ClearConfirmDialog(
            scope = selected,
            manualCourses = manualCourses,
            importedCourses = importedCourses,
            onDismiss = { pendingScope = null },
            onConfirm = {
                pendingScope = null
                scope.launch {
                    sheetState.hide()
                    onConfirm(selected)
                }
            },
        )
    }
}

@Composable
private fun ClearOptionRow(opt: ClearOption, onClick: () -> Unit) {
    val container = if (opt.accent) MaterialTheme.colorScheme.errorContainer
    else MaterialTheme.colorScheme.surfaceVariant
    val onContainer = if (opt.accent) MaterialTheme.colorScheme.onErrorContainer
    else MaterialTheme.colorScheme.onSurfaceVariant
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick),
        color = container,
        shape = RoundedCornerShape(16.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(
                        if (opt.accent) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.surface
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = opt.icon,
                    contentDescription = null,
                    tint = if (opt.accent) MaterialTheme.colorScheme.onError
                    else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(18.dp),
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = opt.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = if (opt.accent) MaterialTheme.colorScheme.onErrorContainer
                    else MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = opt.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = onContainer,
                )
            }
        }
    }
}

@Composable
private fun ClearConfirmDialog(
    scope: ClearScope,
    manualCourses: List<CourseItem>,
    importedCourses: List<CourseItem>,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    val affected: List<CourseItem> = when (scope) {
        ClearScope.ManualOnly -> manualCourses
        ClearScope.ImportedOnly -> importedCourses
        ClearScope.Everything -> manualCourses + importedCourses
    }
    val title = when (scope) {
        ClearScope.ManualOnly -> "清空手动课程？"
        ClearScope.ImportedOnly -> "清空导入课程？"
        ClearScope.Everything -> "清空全部课表？"
    }
    val description = when (scope) {
        ClearScope.ManualOnly -> "将删除以下 ${affected.size} 门手动课程，操作不可恢复："
        ClearScope.ImportedOnly -> "将删除以下 ${affected.size} 门导入课程，操作不可恢复："
        ClearScope.Everything -> "将删除以下 ${affected.size} 门课程，并清除所有提醒规则，操作不可恢复："
    }

    var expanded by remember { mutableStateOf(false) }
    val previewLimit = 5
    val needsFold = affected.size > previewLimit

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.size(8.dp))
                if (affected.isEmpty()) {
                    Text(
                        text = "暂无对应课程，操作仅会清除关联记录。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    val visible = if (expanded || !needsFold) affected
                    else affected.take(previewLimit)
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Column(
                            modifier = Modifier
                                .heightIn(max = 240.dp)
                                .verticalScroll(rememberScrollState())
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                        ) {
                            visible.forEachIndexed { idx, course ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(6.dp)
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.primary),
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        text = course.title,
                                        style = MaterialTheme.typography.bodySmall,
                                        modifier = Modifier.fillMaxWidth(),
                                    )
                                }
                                if (idx < visible.lastIndex) Spacer(Modifier.size(4.dp))
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
                                    text = if (expanded) "收起列表"
                                    else "展开剩余 ${affected.size - previewLimit} 门课程",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(
                    text = "确认清空",
                    color = MaterialTheme.colorScheme.error,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}
