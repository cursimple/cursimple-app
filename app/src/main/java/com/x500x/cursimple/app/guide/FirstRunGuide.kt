package com.x500x.cursimple.app.guide

import androidx.annotation.StringRes
import com.x500x.cursimple.R

/**
 * 这一步要在哪个界面上讲。
 *
 * 只在主页讲的话，导课、全部课程这些页只能干说一句「在某某菜单里」，
 * 用户既看不见长什么样、也不知道从哪进；引导改成先跳过去再讲，
 * 说明卡片压在那一页上面，看完直接退回课表。
 */
enum class GuideDestination {
    /** 课表主页。 */
    Schedule,

    /** 「＋」菜单里的从教务系统导课。 */
    SchoolImport,

    /** 侧边栏里的全部课程。 */
    Courses,

    /** 侧边栏里的插件页。 */
    Plugins,
}

/**
 * 引导的一步。
 *
 * [anchor] 为空表示这一步讲的是整块界面而不是某个控件，只出说明不圈框；
 * 跳到别的界面去讲的步骤都不圈框——那边的控件位置没有上报给引导。
 */
data class GuideStep(
    @StringRes val titleRes: Int,
    @StringRes val bodyRes: Int,
    val anchor: GuideAnchor?,
    val destination: GuideDestination = GuideDestination.Schedule,
)

/** 首次进入时依次讲解的几处入口。 */
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
    // 下面两步会把界面切过去，让用户看见真页面而不是只听描述
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
    // 收尾回到课表，引导结束时用户就站在主页上
    GuideStep(
        titleRes = R.string.guide_done_title,
        bodyRes = R.string.guide_done_body,
        anchor = null,
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

/** 说明卡片的落点。 */
enum class GuideCardPlacement { Top, Bottom, Center }

/**
 * 说明卡片该放在哪。
 *
 * 圈出来的区域在上半屏时卡片放下边，在下半屏时放上边，两者不互相遮挡；
 * 这一步没有要圈的控件时居中，免得贴着边把顶栏压住。
 */
fun guideCardPlacement(anchorCenterY: Float?, containerHeight: Float): GuideCardPlacement = when {
    anchorCenterY == null || containerHeight <= 0f -> GuideCardPlacement.Center
    anchorCenterY < containerHeight / 2f -> GuideCardPlacement.Bottom
    else -> GuideCardPlacement.Top
}
