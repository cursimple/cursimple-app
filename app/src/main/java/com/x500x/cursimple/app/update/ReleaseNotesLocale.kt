package com.x500x.cursimple.app.update

import android.content.Context
import com.x500x.cursimple.core.data.AppLanguage
import com.x500x.cursimple.core.data.AppLocale

internal fun announcementLocale(context: Context): String = when (AppLocale.current(context)) {
    AppLanguage.Chinese -> "zh-CN"
    AppLanguage.TraditionalChinese -> "zh-TW"
    AppLanguage.English -> "en"
    AppLanguage.System -> releaseNotesLanguage(context.resources.configuration.locales[0].toLanguageTag())
}

internal fun releaseNotesLanguage(tag: String): String {
    val normalized = tag.lowercase().replace('_', '-')
    return when {
        normalized.startsWith("zh") && ("hant" in normalized || "-tw" in normalized || "-hk" in normalized || "-mo" in normalized) -> "zh-TW"
        normalized.startsWith("zh") -> "zh-CN"
        else -> "en"
    }
}

internal fun localizedReleasePaths(tag: String, language: String): List<String> {
    val suffix = when (releaseNotesLanguage(language)) { "en" -> ".en"; "zh-TW" -> ".zh-TW"; else -> "" }
    return listOf("docs/release-notes/$tag$suffix.md", "site/release-notes/$tag$suffix.md", "docs/release-notes/$tag.md").distinct()
}
