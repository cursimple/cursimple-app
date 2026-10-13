package com.x500x.cursimple.app.navigation

import com.x500x.cursimple.core.data.navigation.AppLayoutSelection
import com.x500x.cursimple.core.data.navigation.NavDestination
import com.x500x.cursimple.core.data.navigation.SHELL_BOTTOM_BAR
import com.x500x.cursimple.core.data.navigation.SHELL_DRAWER
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AppLayoutResolverTest {
    private val bar = AppLayoutSelection(
        ownerId = "layout",
        shell = SHELL_BOTTOM_BAR,
        items = listOf(NavDestination.Schedule, NavDestination.Memos, NavDestination.Components),
    )

    @Test fun `active owner with declared bottom bar wins`() {
        assertEquals(bar, resolveEffectiveLayout(bar, mapOf("layout" to setOf(SHELL_BOTTOM_BAR))))
    }

    @Test fun `disabled or missing owner falls back`() {
        assertNull(resolveEffectiveLayout(bar, mapOf("other" to setOf(SHELL_BOTTOM_BAR))))
        assertNull(resolveEffectiveLayout(bar, emptyMap()))
    }

    @Test fun `owner no longer declaring the shell falls back`() {
        assertNull(resolveEffectiveLayout(bar, mapOf("layout" to setOf("rail"))))
    }

    @Test fun `unsupported shell falls back`() {
        val rail = bar.copy(shell = "rail")
        assertNull(resolveEffectiveLayout(rail, mapOf("layout" to setOf("rail"))))
    }

    @Test fun `unknown declarations keep the saved bar to avoid a startup flash`() {
        assertEquals(bar, resolveEffectiveLayout(bar, null))
        assertNull(resolveEffectiveLayout(bar.copy(shell = "rail"), null))
        assertNull(resolveEffectiveLayout(null, null))
    }

    @Test fun `null selection and drawer shell are drawer`() {
        assertNull(resolveEffectiveLayout(null, mapOf("layout" to setOf(SHELL_BOTTOM_BAR))))
        assertNull(resolveEffectiveLayout(bar.copy(shell = SHELL_DRAWER), mapOf("layout" to setOf(SHELL_DRAWER))))
    }
}
