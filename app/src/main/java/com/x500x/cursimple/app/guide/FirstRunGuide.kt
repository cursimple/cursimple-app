package com.x500x.cursimple.app.guide

import androidx.annotation.RawRes
import androidx.annotation.StringRes
import com.x500x.cursimple.R

internal data class GuideStep(
    @StringRes val titleRes: Int,
    @StringRes val bodyRes: Int,
    @RawRes val animationRes: Int,
)

internal fun firstRunGuideSteps(): List<GuideStep> = listOf(
    GuideStep(R.string.guide_grid_title, R.string.guide_grid_body, R.raw.first_run_week),
    GuideStep(R.string.guide_school_import_title, R.string.guide_school_import_body, R.raw.first_run_school),
    GuideStep(R.string.guide_courses_title, R.string.guide_courses_body, R.raw.first_run_library),
    GuideStep(R.string.guide_reminders_title, R.string.guide_reminders_body, R.raw.first_run_reminder),
)

internal fun shouldReviewFirstRunPermissions(guideCompleted: Boolean, permissionsReviewed: Boolean): Boolean =
    !guideCompleted && !permissionsReviewed

internal fun shouldShowFirstRunGuide(
    loaded: Boolean,
    disclaimerAccepted: Boolean,
    guideCompleted: Boolean,
    blockingDialogVisible: Boolean,
): Boolean = loaded && disclaimerAccepted && !guideCompleted && !blockingDialogVisible

internal fun nextGuideIndex(current: Int, total: Int): Int? =
    (current + 1).takeIf { it in 0 until total }

internal fun previousGuideIndex(current: Int): Int = (current - 1).coerceAtLeast(0)
