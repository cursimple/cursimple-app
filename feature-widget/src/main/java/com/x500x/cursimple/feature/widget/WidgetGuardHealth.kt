package com.x500x.cursimple.feature.widget

/** 守护链当前的完整程度。 */
enum class WidgetGuardHealth {
    /** 全部槽位都在。 */
    Healthy,

    /** 掉了一部分，剩下的仍能把链续下去。 */
    Degraded,

    /** 一条都不剩，链已断，只能靠外部事件重新排布。 */
    Broken,
}

/**
 * 按仍然注册着的槽位数判断守护链状态。
 *
 * 守护链靠每次触发时重排自身延续，只要还剩一条就能自愈；
 * 一条不剩时链条彻底断开，此后没有任何自身事件能把它拉起来。
 */
fun widgetGuardHealth(registeredSlotCount: Int, expectedSlotCount: Int): WidgetGuardHealth = when {
    expectedSlotCount <= 0 -> WidgetGuardHealth.Healthy
    registeredSlotCount <= 0 -> WidgetGuardHealth.Broken
    registeredSlotCount >= expectedSlotCount -> WidgetGuardHealth.Healthy
    else -> WidgetGuardHealth.Degraded
}
