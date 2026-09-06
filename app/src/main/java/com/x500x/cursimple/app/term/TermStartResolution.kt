package com.x500x.cursimple.app.term

import java.time.LocalDate

/**
 * 同步插件课表时该采用哪个开学日期。
 *
 * 用户自己定过之后就以用户的为准，主动清空也算一种决定，插件不再写回来；
 * 没定过时才从插件带来的作息里继承，省去手动输入。
 */
fun resolveCanonicalTermStart(
    userDecided: Boolean,
    termStart: LocalDate?,
    pluginTermStart: LocalDate?,
): LocalDate? = if (userDecided) termStart else termStart ?: pluginTermStart

/** 当前的开学日期是不是从插件继承来的，界面据此标注来源。 */
fun isTermStartFromPlugin(userDecided: Boolean, termStart: LocalDate?): Boolean =
    !userDecided && termStart != null
