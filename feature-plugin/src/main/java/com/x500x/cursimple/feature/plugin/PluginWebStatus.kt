package com.x500x.cursimple.feature.plugin

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource

/**
 * 网页会话的当前状态。
 *
 * 只描述状态本身不带文案，文案由界面按当前语言渲染，
 * 应用内切换语言时这一行才会跟着变。
 */
internal sealed interface PluginWebStatus {
    data class Uploading(@StringRes val stageLabel: Int, val step: Int) : PluginWebStatus

    data class PageError(val message: String?) : PluginWebStatus

    data class Blocked(val url: String?) : PluginWebStatus

    data class Popup(val url: String?) : PluginWebStatus

    data object PendingCompletion : PluginWebStatus

    data class ConsoleError(val message: String?) : PluginWebStatus

    data class Packets(
        val captured: Int,
        val required: Int,
        val cookies: Int,
        val localStorage: Int,
        val sessionStorage: Int,
    ) : PluginWebStatus

    data object WaitingData : PluginWebStatus

    data class Loading(val progress: Int) : PluginWebStatus

    data class Opened(val title: String) : PluginWebStatus

    data class Visiting(val url: String) : PluginWebStatus

    data object WaitingPage : PluginWebStatus
}

@Composable
internal fun pluginWebStatusText(status: PluginWebStatus): String = when (status) {
    is PluginWebStatus.Uploading -> stringResource(
        R.string.plugin_web_status_uploading,
        stringResource(status.stageLabel),
        status.step,
    )
    is PluginWebStatus.PageError ->
        stringResource(R.string.plugin_web_status_page_error, status.message.orEmpty())
    is PluginWebStatus.Blocked ->
        stringResource(R.string.plugin_web_status_blocked, status.url.orEmpty())
    is PluginWebStatus.Popup ->
        stringResource(R.string.plugin_web_status_popup, status.url.orEmpty())
    PluginWebStatus.PendingCompletion ->
        stringResource(R.string.plugin_web_status_pending_completion)
    is PluginWebStatus.ConsoleError ->
        stringResource(R.string.plugin_web_status_console_error, status.message.orEmpty())
    is PluginWebStatus.Packets -> buildString {
        append(stringResource(R.string.plugin_web_status_packets, status.captured, status.required))
        if (status.cookies > 0) append(" · cookies ${status.cookies}")
        if (status.localStorage > 0) append(" · localStorage ${status.localStorage}")
        if (status.sessionStorage > 0) append(" · sessionStorage ${status.sessionStorage}")
    }
    PluginWebStatus.WaitingData -> stringResource(R.string.plugin_web_status_waiting_data)
    is PluginWebStatus.Loading -> stringResource(R.string.plugin_web_status_loading, status.progress)
    is PluginWebStatus.Opened -> stringResource(R.string.plugin_web_status_opened, status.title)
    is PluginWebStatus.Visiting -> stringResource(R.string.plugin_web_status_visiting, status.url)
    PluginWebStatus.WaitingPage -> stringResource(R.string.plugin_web_status_waiting_page)
}
