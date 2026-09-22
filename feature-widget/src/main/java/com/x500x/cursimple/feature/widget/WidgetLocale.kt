package com.x500x.cursimple.feature.widget

import android.content.Context
import com.x500x.cursimple.core.data.AppLocale

/**
 * 小组件文字的取值上下文。
 *
 * 小组件画在启动器进程里，但文案是本进程解析好再发过去的。应用语言存在设置里，
 * Application 只在进程启动那一刻按语言包过一次上下文，用户改完语言进程通常不会重启，
 * 于是 applicationContext 还停在旧语言上。每次刷新都重新包一层，小组件才跟得上设置。
 *
 * 跟随系统时 [AppLocale.wrap] 原样返回，小组件照常跟系统语言走。
 */
internal fun Context.widgetLocaleContext(): Context = AppLocale.wrap(applicationContext)
