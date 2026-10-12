package com.x500x.cursimple.feature.plugin

import com.x500x.cursimple.core.plugin.web.WebSessionRequest
import java.net.URI

/** A plugin opts in; wait on its capture pages, never on login pages or unrelated hosts. */
internal fun shouldShowManualUploadHint(request: WebSessionRequest, url: String, startedAt: Long, now: Long, busy: Boolean): Boolean {
    val delay = request.manualUploadHintDelayMs?.coerceIn(1_000, 60_000) ?: return false
    if (busy || !request.autoCompleteOnScheduleDraft || request.entryScript.isBlank() || now - startedAt < delay) return false
    val parsed = runCatching { URI(url) }.getOrNull() ?: return false
    if (parsed.scheme != "https" && parsed.scheme != "http") return false
    val host = parsed.host ?: return false
    if (request.allowedHosts.none { host.equals(it, true) || host.endsWith(".$it", true) }) return false
    return request.networkCaptures.any { it.urlPathContains?.takeIf(String::isNotBlank)?.let { path -> parsed.path.orEmpty().contains(path) } == true }
}
