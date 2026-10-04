package com.x500x.cursimple.feature.widget

/**
 * 小组件点进应用时带的 extra。主界面按同一组常量接收，两边不会对不上。
 */
object WidgetDeepLinks {
    /** 值为组件 id：打开那个组件的页面 */
    const val EXTRA_OPEN_COMPONENT_PAGE = "com.x500x.cursimple.extra.OPEN_EXTENSION_FEED"

    /** 值为 ISO 日期：切到日视图并停在那一天 */
    const val EXTRA_OPEN_SCHEDULE_DATE = "com.x500x.cursimple.extra.OPEN_SCHEDULE_DATE"
}
