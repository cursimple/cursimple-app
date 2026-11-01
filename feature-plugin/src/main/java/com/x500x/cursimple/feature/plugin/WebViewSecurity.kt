package com.x500x.cursimple.feature.plugin

import android.os.Build
import android.webkit.WebSettings
import android.webkit.WebView

/** Apply before attaching native bridges or loading third-party content. */
@Suppress("DEPRECATION")
internal fun WebView.hardenLocalAccess() {
    settings.allowFileAccess = false
    settings.allowContentAccess = false
    settings.allowFileAccessFromFileURLs = false
    settings.allowUniversalAccessFromFileURLs = false
    settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
    settings.setGeolocationEnabled(false)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) settings.safeBrowsingEnabled = true
}
