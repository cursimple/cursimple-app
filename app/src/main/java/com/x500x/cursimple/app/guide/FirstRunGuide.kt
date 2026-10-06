package com.x500x.cursimple.app.guide

import androidx.annotation.StringRes
import com.x500x.cursimple.R

/**
 * Navigate to the relevant screen before explaining its controls, then return to the timetable.
 */
enum class GuideDestination {
    Schedule,

    SchoolImport,

    Courses,

    Plugins,
}

/**
 * Null [anchor] describes the whole screen; destinations without reported bounds do not receive
 * a highlight.
 */
data class GuideStep(
    @StringRes val titleRes: Int,
    @StringRes val bodyRes: Int,
    val anchor: GuideAnchor?,
    val destination: GuideDestination = GuideDestination.Schedule,
)

val FIRST_RUN_GUIDE_STEPS: List<GuideStep> = listOf(
    GuideStep(
        titleRes = R.string.guide_drawer_title,
        bodyRes = R.string.guide_drawer_body,
        anchor = GuideAnchor.Drawer,
    ),
    GuideStep(
        titleRes = R.string.guide_week_title,
        bodyRes = R.string.guide_week_body,
        anchor = GuideAnchor.WeekTitle,
    ),
    GuideStep(
        titleRes = R.string.guide_grid_title,
        bodyRes = R.string.guide_grid_body,
        anchor = null,
    ),
    GuideStep(
        titleRes = R.string.guide_add_title,
        bodyRes = R.string.guide_add_body,
        anchor = GuideAnchor.Add,
    ),
    // Navigate before explaining these destination screens.
    GuideStep(
        titleRes = R.string.guide_school_import_title,
        bodyRes = R.string.guide_school_import_body,
        anchor = null,
        destination = GuideDestination.SchoolImport,
    ),
    GuideStep(
        titleRes = R.string.guide_courses_title,
        bodyRes = R.string.guide_courses_body,
        anchor = null,
        destination = GuideDestination.Courses,
    ),
    GuideStep(
        titleRes = R.string.guide_plugins_title,
        bodyRes = R.string.guide_plugins_body,
        anchor = null,
        destination = GuideDestination.Plugins,
    ),
    GuideStep(
        titleRes = R.string.guide_done_title,
        bodyRes = R.string.guide_done_body,
        anchor = null,
    ),
)

/** Wait for acceptance and loaded preferences; do not overlap existing dialogs. */
fun shouldShowFirstRunGuide(
    loaded: Boolean,
    disclaimerAccepted: Boolean,
    guideCompleted: Boolean,
    blockingDialogVisible: Boolean,
): Boolean = loaded && disclaimerAccepted && !guideCompleted && !blockingDialogVisible

fun nextGuideIndex(current: Int, total: Int): Int? =
    (current + 1).takeIf { it in 0 until total }

fun previousGuideIndex(current: Int): Int = (current - 1).coerceAtLeast(0)

enum class GuideCardPlacement { Top, Bottom, Center }

/** Place the guide opposite its highlight, or center it without an anchor. */
fun guideCardPlacement(anchorCenterY: Float?, containerHeight: Float): GuideCardPlacement = when {
    anchorCenterY == null || containerHeight <= 0f -> GuideCardPlacement.Center
    anchorCenterY < containerHeight / 2f -> GuideCardPlacement.Bottom
    else -> GuideCardPlacement.Top
}
