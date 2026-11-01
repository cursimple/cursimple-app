package com.x500x.cursimple.feature.plugin

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.x500x.cursimple.core.plugin.school.SchoolPortalCatalog
import com.x500x.cursimple.core.plugin.web.WebSessionRequest
import java.net.URI

/** URL-only guidance never reads the page or starts a capture. */
@StringRes
internal fun schoolPortalGuidance(request: WebSessionRequest, url: String): Int? {
    val page = runCatching { URI(url) }.getOrNull() ?: return null
    if (page.scheme !in listOf("https", "http") || !isAllowedHost(url, request.allowedHosts)) return null
    val host = page.host.orEmpty()
    val path = page.path.orEmpty().trimEnd('/')
    val route = page.fragment.orEmpty().substringBefore('?').trimEnd('/')
    if (host.equals("atrust.yangtzeu.edu.cn", ignoreCase = true) &&
        path == "/portal" && route == "/app_center"
    ) return R.string.plugin_web_yangtze_workbench_hint

    val isYangtzeHost = host.equals("yangtzeu.edu.cn", ignoreCase = true) ||
        host.endsWith(".yangtzeu.edu.cn", ignoreCase = true)
    if (request.pluginId == SchoolPortalCatalog.PLUGIN_ID && isYangtzeHost &&
        path.matches(Regex("/eams[^/]*/home\\.action", RegexOption.IGNORE_CASE))
    ) return R.string.plugin_web_yangtze_timetable_hint
    return null
}

/** Keep import failures distinct from page loading failures, including after navigation. */
internal fun pluginWebFailureStatus(importFailure: String?, pageError: String?): PluginWebStatus? = when {
    importFailure != null -> PluginWebStatus.ImportError(importFailure)
    pageError != null -> PluginWebStatus.PageError(pageError)
    else -> null
}

internal fun PluginWebStatus.showsFullMessage(): Boolean =
    this is PluginWebStatus.ImportError || this is PluginWebStatus.PageError ||
        this is PluginWebStatus.ConsoleError || this is PluginWebStatus.Blocked

/** Typed session state localized by the UI. */
internal sealed interface PluginWebStatus {
    data class Uploading(@StringRes val stageLabel: Int, val step: Int) : PluginWebStatus

    data class PageError(val message: String?) : PluginWebStatus

    data class ImportError(val message: String) : PluginWebStatus

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
    is PluginWebStatus.ImportError ->
        stringResource(R.string.plugin_web_status_import_error, status.message)
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
