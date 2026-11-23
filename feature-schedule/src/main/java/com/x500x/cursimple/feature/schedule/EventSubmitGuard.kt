package com.x500x.cursimple.feature.schedule

internal fun canSubmitEvent(isSaving: Boolean, isValid: Boolean): Boolean = !isSaving && isValid
