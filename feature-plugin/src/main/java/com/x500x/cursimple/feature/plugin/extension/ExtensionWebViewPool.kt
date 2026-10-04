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
 * 组件界面的 WebView 只保留一个热实例。页面离开时回收到池里，下次打开直接复用 Chromium
 * 渲染进程；组件自己的 HTML 仍会重新加载，数据不会串到另一个组件。
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
        // WRAP_CONTENT 使部分 WebView 将 CSS vh / 百分比高度算成 0，
        // 列表虽有 DOM 却被零高度滚动区裁掉。容器明确占满 Compose 给定的区域。
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

/** 从按下开始就让 WebView 拥有完整手势，避免斜向滚动被 Compose 父级取消。 */
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
