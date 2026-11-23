package com.x500x.cursimple.feature.plugin

import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Top bar model exposed by plugin catalog screens to the hosting scaffold.
 */
data class PluginTopBar(
    val title: String,
    val onBack: () -> Unit,
)

/**
 * True when the hosting scaffold draws [PluginTopBar] itself, so nested pages skip their own
 * title row and the screen keeps a single top bar. Standalone use keeps the inner bar.
 */
val LocalPluginHostTopBar = staticCompositionLocalOf { false }
