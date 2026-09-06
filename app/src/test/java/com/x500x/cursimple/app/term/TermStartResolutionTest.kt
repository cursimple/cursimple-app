package com.x500x.cursimple.app.term

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

private val USER_DATE: LocalDate = LocalDate.of(2026, 9, 1)
private val PLUGIN_DATE: LocalDate = LocalDate.of(2026, 2, 23)

class ResolveCanonicalTermStartTest {
    @Test
    fun `a first-time user inherits the date the plugin brought`() {
        val resolved = resolveCanonicalTermStart(
            userDecided = false,
            termStart = null,
            pluginTermStart = PLUGIN_DATE,
        )

        assertEquals(PLUGIN_DATE, resolved)
    }

    @Test
    fun `the user's own date wins over the plugin's`() {
        val resolved = resolveCanonicalTermStart(
            userDecided = true,
            termStart = USER_DATE,
            pluginTermStart = PLUGIN_DATE,
        )

        assertEquals(USER_DATE, resolved)
    }

    @Test
    fun `clearing the date on purpose keeps it cleared across syncs`() {
        val resolved = resolveCanonicalTermStart(
            userDecided = true,
            termStart = null,
            pluginTermStart = PLUGIN_DATE,
        )

        assertNull(resolved)
    }

    @Test
    fun `an existing date is kept when the plugin brings none`() {
        val resolved = resolveCanonicalTermStart(
            userDecided = false,
            termStart = USER_DATE,
            pluginTermStart = null,
        )

        assertEquals(USER_DATE, resolved)
    }

    @Test
    fun `nothing anywhere stays empty`() {
        assertNull(resolveCanonicalTermStart(userDecided = false, termStart = null, pluginTermStart = null))
    }
}

class IsTermStartFromPluginTest {
    @Test
    fun `a date the user never set is labelled as coming from the plugin`() {
        assertTrue(isTermStartFromPlugin(userDecided = false, termStart = PLUGIN_DATE))
    }

    @Test
    fun `a date the user set is not labelled`() {
        assertFalse(isTermStartFromPlugin(userDecided = true, termStart = USER_DATE))
    }

    @Test
    fun `no date means nothing to label`() {
        assertFalse(isTermStartFromPlugin(userDecided = false, termStart = null))
    }
}
