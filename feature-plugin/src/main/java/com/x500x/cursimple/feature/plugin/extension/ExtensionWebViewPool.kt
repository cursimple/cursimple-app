package com.x500x.cursimple.feature.plugin.extension

import android.annotation.SuppressLint
import android.content.Context
import android.view.ContextThemeWrapper
import android.view.MotionEvent
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient

/**
 * Reuse one warm WebView, reloading owned HTML so component data does not leak between pages.
 */
object ExtensionWebViewPool {
    private var pooled: WebView? = null

    @SuppressLint("SetJavaScriptEnabled")
    fun prewarm(context: Context) {
        if (pooled != null) return
        pooled = newWebView(context).apply {
            loadDataWithBaseURL("https://cursimple-extension.invalid/", "<html></html>", "text/html", "UTF-8", null)
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    fun acquire(context: Context): WebView = (pooled.also { pooled = null } ?: newWebView(context)).also {
        (it.parent as? ViewGroup)?.removeView(it)
        it.layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
    }

    fun release(view: WebView) {
        view.parent?.requestDisallowInterceptTouchEvent(false)
        (view.parent as? ViewGroup)?.removeView(view)
        view.stopLoading()
        view.removeJavascriptInterface("CurSimpleExtensionUi")
        view.loadDataWithBaseURL("https://cursimple-extension.invalid/", "<html></html>", "text/html", "UTF-8", null)
        if (pooled == null) pooled = view else view.destroy()
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun newWebView(context: Context): WebView = ComponentWebView(
        ContextThemeWrapper(context.applicationContext, context.theme),
    ).apply {
        // Fill the assigned region so WebView percentage and vh heights remain nonzero.
        layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        settings.useWideViewPort = true
        settings.loadWithOverviewMode = false
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.loadsImagesAutomatically = true
        settings.blockNetworkImage = false
        webViewClient = WebViewClient()
        webChromeClient = WebChromeClient()
    }
}

/**
 * Give WebView the full touch sequence from Down to prevent parent cancellation of diagonal
 * scroll.
 */
private class ComponentWebView(context: Context) : WebView(context) {
    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_DOWN) {
            parent?.requestDisallowInterceptTouchEvent(true)
        }
        return try {
            super.dispatchTouchEvent(event)
        } finally {
            if (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL) {
                parent?.requestDisallowInterceptTouchEvent(false)
            }
        }
    }
}
