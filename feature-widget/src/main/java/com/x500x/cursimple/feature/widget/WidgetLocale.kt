package com.x500x.cursimple.feature.widget

import android.content.Context
import com.x500x.cursimple.core.data.AppLocale

/**
 * Wrap locale on every refresh because applicationContext can retain the pre-change language.
 */
internal fun Context.widgetLocaleContext(): Context = AppLocale.wrap(applicationContext)
