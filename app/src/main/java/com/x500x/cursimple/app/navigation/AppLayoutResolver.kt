package com.x500x.cursimple.app.navigation

import com.x500x.cursimple.core.data.navigation.AppLayoutSelection
import com.x500x.cursimple.core.data.navigation.SHELL_DRAWER
import com.x500x.cursimple.core.data.navigation.SUPPORTED_SHELLS

/**
 * Returns the component layout that should be drawn, or null for the host drawer.
 * [declaredShells] maps installed, enabled and compatible component ids to the shells their
 * manifests declare, so a disabled, removed or downgraded owner falls back without losing the record.
 * Null [declaredShells] means manifests are still loading; the saved layout is kept meanwhile so a
 * cold start does not flash the drawer first.
 */
fun resolveEffectiveLayout(
    selection: AppLayoutSelection?,
    declaredShells: Map<String, Set<String>>?,
): AppLayoutSelection? = selection?.takeIf {
    it.shell != SHELL_DRAWER &&
        it.shell in SUPPORTED_SHELLS &&
        (declaredShells == null || it.shell in declaredShells[it.ownerId].orEmpty())
}
