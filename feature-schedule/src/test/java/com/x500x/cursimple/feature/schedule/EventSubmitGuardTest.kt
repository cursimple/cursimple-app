package com.x500x.cursimple.feature.schedule

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EventSubmitGuardTest {
    @Test
    fun validEventCanSubmitOnlyOnce() {
        assertTrue(canSubmitEvent(isSaving = false, isValid = true))
        assertFalse(canSubmitEvent(isSaving = true, isValid = true))
        assertFalse(canSubmitEvent(isSaving = false, isValid = false))
    }
}
