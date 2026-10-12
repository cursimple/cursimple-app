package com.x500x.cursimple.feature.schedule

import androidx.compose.runtime.staticCompositionLocalOf
import com.x500x.cursimple.core.plugin.ui.PluginUiSchema

val LocalPluginTimetableRowPreferences = staticCompositionLocalOf<Map<String, Map<String, Boolean>>> { emptyMap() }
val LocalPluginTimetableRowDefinitions = staticCompositionLocalOf<Map<String, PluginUiSchema>> { emptyMap() }
internal val LocalHiddenTimetableRows = staticCompositionLocalOf<Set<Pair<Int, Int>>> { emptySet() }

/** Omit display rows only, leaving persisted periods and all reminder calculations untouched. */
internal fun visibleTimetableSlots(slots: List<DisplaySlot>, hidden: Set<Pair<Int, Int>>): List<DisplaySlot> =
    slots.filterNot { (it.startNode to it.endNode) in hidden }
