package com.x500x.cursimple.app.guide

import androidx.annotation.StringRes
import com.x500x.cursimple.R

/**
 * 引导要指向的屏幕区域，用相对整屏的比例表示。
 *
 * 顶栏按钮的位置只随屏幕宽度变化，按比例圈出来就够指明位置，
 * 也不必让被指的控件反过来上报自己的坐标。
 */
data class GuideSpotlight(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
) {
    val centerY: Float get() = (top + bottom) / 2f
}

/** 引导的一步。 */
data class GuideStep(
    @StringRes val titleRes: Int,
    @StringRes val bodyRes: Int,
    val spotlight: GuideSpotlight,
)

/** 顶栏在整屏高度里占的位置，圈选按钮时按它取纵向范围。 */
private const val TOP_BAR_TOP = 0.055f
private const val TOP_BAR_BOTTOM = 0.115f

/** 首次进入时依次讲解的几处入口。 */
val FIRST_RUN_GUIDE_STEPS: List<GuideStep> = listOf(
    GuideStep(
        titleRes = R.string.guide_drawer_title,
        bodyRes = R.string.guide_drawer_body,
        spotlight = GuideSpotlight(0.01f, TOP_BAR_TOP, 0.17f, TOP_BAR_BOTTOM),
    ),
    GuideStep(
        titleRes = R.string.guide_sync_title,
        bodyRes = R.string.guide_sync_body,
        spotlight = GuideSpotlight(0.01f, TOP_BAR_TOP, 0.17f, TOP_BAR_BOTTOM),
    ),
    GuideStep(
        titleRes = R.string.guide_add_title,
        bodyRes = R.string.guide_add_body,
        spotlight = GuideSpotlight(0.87f, TOP_BAR_TOP, 0.99f, TOP_BAR_BOTTOM),
    ),
    GuideStep(
        titleRes = R.string.guide_week_title,
        bodyRes = R.string.guide_week_body,
        spotlight = GuideSpotlight(0.20f, TOP_BAR_TOP, 0.80f, TOP_BAR_BOTTOM),
    ),
    GuideStep(
        titleRes = R.string.guide_grid_title,
        bodyRes = R.string.guide_grid_body,
        spotlight = GuideSpotlight(0.10f, 0.30f, 0.95f, 0.60f),
    ),
)

/**
 * 是否该弹新手引导。
 *
 * 要等免责声明过了、偏好读出来了、引导没走过，
 * 并且此刻没有别的弹窗压在上面，免得两层盖在一起看不清指的是哪。
 */
fun shouldShowFirstRunGuide(
    loaded: Boolean,
    disclaimerAccepted: Boolean,
    guideCompleted: Boolean,
    blockingDialogVisible: Boolean,
): Boolean = loaded && disclaimerAccepted && !guideCompleted && !blockingDialogVisible

/** 点下一步之后停在哪一步，走到末尾返回 null 表示引导结束。 */
fun nextGuideIndex(current: Int, total: Int): Int? =
    (current + 1).takeIf { it in 0 until total }

/** 点上一步之后停在哪一步，第一步再往前仍是第一步。 */
fun previousGuideIndex(current: Int): Int = (current - 1).coerceAtLeast(0)
