package com.x500x.cursimple.app.navigation

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DragIndicator
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material.icons.rounded.ViewSidebar
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.x500x.cursimple.R
import com.x500x.cursimple.app.SettingsCardGroup
import com.x500x.cursimple.app.SettingsSwitchRow
import com.x500x.cursimple.core.data.navigation.DrawerLayout
import com.x500x.cursimple.core.data.navigation.NavDestination
import com.x500x.cursimple.feature.plugin.ui.AppOutlinedButton

/** Host sidebar customization; works without any layout component installed. */
@Composable
fun SidebarSettingsPage(
    layout: DrawerLayout,
    componentLayoutActive: Boolean,
    onChange: (DrawerLayout) -> Unit,
    onRestoreDrawer: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val currentLayout by rememberUpdatedState(layout)
    val currentOnChange by rememberUpdatedState(onChange)
    // Local order while dragging; persisted once on release so storage writes cannot race the gesture.
    var dragOrder by remember { mutableStateOf<List<NavDestination>?>(null) }
    var dragged by remember { mutableStateOf<NavDestination?>(null) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    var rowHeight by remember { mutableIntStateOf(1) }
    val order = dragOrder ?: layout.order

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        if (componentLayoutActive) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.secondaryContainer,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        text = stringResource(R.string.settings_sidebar_component_layout),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                    AppOutlinedButton(
                        onClick = onRestoreDrawer,
                        modifier = Modifier.testTag("sidebar-restore-drawer"),
                    ) {
                        Icon(Icons.Rounded.ViewSidebar, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.settings_sidebar_restore_drawer))
                    }
                }
            }
        }

        Text(
            text = stringResource(R.string.settings_sidebar_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 4.dp),
        )

        SettingsCardGroup(stringResource(R.string.settings_sidebar_items)) {
            order.forEachIndexed { index, destination ->
                key(destination) {
                    val isDragged = dragged == destination
                    SidebarItemRow(
                        destination = destination,
                        visible = destination !in layout.hidden,
                        canMoveUp = index > 0,
                        canMoveDown = index < order.lastIndex,
                        onToggle = { onChange(layout.toggled(destination)) },
                        onMove = { delta -> onChange(layout.moved(index, index + delta)) },
                        dragHandle = Modifier.pointerInput(destination) {
                            detectDragGestures(
                                onDragStart = {
                                    dragOrder = currentLayout.order
                                    dragged = destination
                                    dragOffset = 0f
                                },
                                onDrag = { change, amount ->
                                    change.consume()
                                    dragOffset += amount.y
                                    val working = dragOrder ?: return@detectDragGestures
                                    val at = working.indexOf(destination)
                                    val step = when {
                                        dragOffset > rowHeight / 2f && at < working.lastIndex -> 1
                                        dragOffset < -rowHeight / 2f && at > 0 -> -1
                                        else -> 0
                                    }
                                    if (step != 0) {
                                        dragOrder = working.toMutableList().apply { add(at + step, removeAt(at)) }
                                        dragOffset -= step * rowHeight
                                    }
                                },
                                onDragEnd = {
                                    dragOrder?.let { currentOnChange(currentLayout.copy(order = it)) }
                                    dragOrder = null
                                    dragged = null
                                    dragOffset = 0f
                                },
                                onDragCancel = {
                                    dragOrder = null
                                    dragged = null
                                    dragOffset = 0f
                                },
                            )
                        },
                        modifier = Modifier
                            .onSizeChanged { if (it.height > 0) rowHeight = it.height }
                            .zIndex(if (isDragged) 1f else 0f)
                            .graphicsLayer { translationY = if (isDragged) dragOffset else 0f }
                            .then(if (isDragged) Modifier.shadow(6.dp, RoundedCornerShape(12.dp)) else Modifier),
                    )
                }
            }
        }

        SettingsCardGroup(stringResource(R.string.settings_sidebar_shortcuts)) {
            SettingsSwitchRow(
                icon = Icons.Rounded.TouchApp,
                title = stringResource(R.string.settings_sidebar_shortcuts),
                subtitle = stringResource(R.string.settings_sidebar_shortcuts_subtitle),
                checked = layout.shortcutsVisible,
                onCheckedChange = { onChange(layout.copy(shortcutsVisible = it)) },
            )
        }
    }
}

@Composable
private fun SidebarItemRow(
    destination: NavDestination,
    visible: Boolean,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onToggle: () -> Unit,
    onMove: (Int) -> Unit,
    dragHandle: Modifier,
    modifier: Modifier = Modifier,
) {
    val label = stringResource(destination.labelRes)
    val moveUp = stringResource(R.string.settings_sidebar_move_up)
    val moveDown = stringResource(R.string.settings_sidebar_move_down)
    val actions = buildList {
        if (canMoveUp) add(CustomAccessibilityAction(moveUp) { onMove(-1); true })
        if (canMoveDown) add(CustomAccessibilityAction(moveDown) { onMove(1); true })
    }
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = modifier
            .fillMaxWidth()
            .testTag("sidebar-item-${destination.id}"),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(48.dp)
                    .then(dragHandle),
            ) {
                Icon(
                    imageVector = Icons.Rounded.DragIndicator,
                    contentDescription = stringResource(R.string.settings_sidebar_drag),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
            }
            val toggle = if (destination.locked) {
                Modifier.semantics { customActions = actions }
            } else {
                Modifier
                    .toggleable(value = visible, role = Role.Switch, onValueChange = { onToggle() })
                    .semantics { customActions = actions }
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .weight(1f)
                    .then(toggle)
                    .padding(end = 14.dp, top = 12.dp, bottom = 12.dp),
            ) {
                Icon(
                    imageVector = destination.icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = label, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                    if (destination.locked) {
                        Text(
                            text = stringResource(R.string.settings_sidebar_locked),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                if (destination.locked) {
                    Icon(
                        imageVector = Icons.Rounded.Lock,
                        contentDescription = stringResource(R.string.settings_sidebar_locked),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp),
                    )
                } else {
                    Switch(checked = visible, onCheckedChange = null)
                }
            }
        }
    }
}
