package com.x500x.cursimple.app.update

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

private const val CURRENT = 9

class ShouldShowUpdateBadgeTest {
    @Test
    fun `no check result yet means no badge`() {
        assertFalse(shouldShowUpdateBadge(UpdateNoticeState(), CURRENT))
    }

    @Test
    fun `a newer version shows the badge`() {
        assertTrue(shouldShowUpdateBadge(UpdateNoticeState(versionCode = 10), CURRENT))
    }

    @Test
    fun `the installed version or an older one shows nothing`() {
        assertFalse(shouldShowUpdateBadge(UpdateNoticeState(versionCode = CURRENT), CURRENT))
        assertFalse(shouldShowUpdateBadge(UpdateNoticeState(versionCode = 8), CURRENT))
    }

    @Test
    fun `ignoring the version clears the badge`() {
        val state = UpdateNoticeState(versionCode = 10, ignoredVersionCode = 10)

        assertFalse(shouldShowUpdateBadge(state, CURRENT))
    }

    @Test
    fun `muting keeps the badge`() {
        val state = UpdateNoticeState(versionCode = 10, mutedVersionCode = 10)

        assertTrue(shouldShowUpdateBadge(state, CURRENT))
    }

    @Test
    fun `a version newer than the ignored one brings the badge back`() {
        val state = UpdateNoticeState(versionCode = 11, ignoredVersionCode = 10)

        assertTrue(shouldShowUpdateBadge(state, CURRENT))
    }
}

class ShouldPromptUpdateTest {
    @Test
    fun `a newly found version prompts once`() {
        val state = UpdateNoticeState(versionCode = 10)

        assertTrue(shouldPromptUpdate(state, CURRENT, promptedThisSession = false))
        assertFalse(shouldPromptUpdate(state, CURRENT, promptedThisSession = true))
    }

    @Test
    fun `muting stops the prompt while the badge stays`() {
        val state = UpdateNoticeState(versionCode = 10, mutedVersionCode = 10)

        assertFalse(shouldPromptUpdate(state, CURRENT, promptedThisSession = false))
        assertTrue(shouldShowUpdateBadge(state, CURRENT))
    }

    @Test
    fun `ignoring stops the prompt too`() {
        val state = UpdateNoticeState(versionCode = 10, ignoredVersionCode = 10)

        assertFalse(shouldPromptUpdate(state, CURRENT, promptedThisSession = false))
    }

    @Test
    fun `a version newer than the muted one prompts again`() {
        val state = UpdateNoticeState(versionCode = 11, mutedVersionCode = 10)

        assertTrue(shouldPromptUpdate(state, CURRENT, promptedThisSession = false))
    }

    @Test
    fun `being up to date never prompts`() {
        assertFalse(shouldPromptUpdate(UpdateNoticeState(), CURRENT, promptedThisSession = false))
    }
}
