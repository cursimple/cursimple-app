package com.x500x.cursimple.feature.plugin

import android.webkit.WebView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewFeature
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * WebView adds `X-Requested-With: <package>` to every request, and legacy academic servers answer such
 * requests with a head-less fragment (a blank page). Plugin web views must not send it.
 */
@RunWith(AndroidJUnit4::class)
class PluginWebRequestedWithTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()

    @Test fun pluginBrowserSettingsStopTheAppPackageHeader() {
        assumeTrue(WebViewFeature.isFeatureSupported(WebViewFeature.REQUESTED_WITH_HEADER_ALLOW_LIST))
        var allowed: Set<String>? = null
        instrumentation.runOnMainSync {
            val view = WebView(instrumentation.targetContext)
            view.applyPluginBrowserSettings("")
            allowed = WebSettingsCompat.getRequestedWithHeaderOriginAllowList(view.settings)
            view.destroy()
        }
        assertTrue("no origin may receive X-Requested-With, got $allowed", allowed?.isEmpty() == true)
    }
}
