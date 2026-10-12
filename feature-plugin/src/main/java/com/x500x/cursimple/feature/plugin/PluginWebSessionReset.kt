package com.x500x.cursimple.feature.plugin

import android.content.Context
import android.webkit.CookieManager
import android.webkit.WebStorage
import android.webkit.WebView
import com.x500x.cursimple.core.plugin.logging.PluginLogger
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Sign a schedule plugin out of its sites. CookieManager and WebStorage are process-global, so a
 * later web session would otherwise reuse the previous account's portal and app sessions.
 *
 * Only cookies visible to the plugin's own allowed hosts are expired; other plugins and
 * extension components keep their logins.
 */
internal class PluginWebSessionReset(context: Context) {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** Remember cookie path scopes seen in a session so a later reset can reach path-limited cookies. */
    fun rememberScopes(pluginId: String, urls: Collection<String>) {
        if (urls.isEmpty()) return
        val merged = mergeCookieScopes(prefs.getStringSet(scopeKey(pluginId), emptySet()).orEmpty(), urls)
        prefs.edit().putStringSet(scopeKey(pluginId), merged).apply()
    }

    fun forget(pluginId: String) {
        prefs.edit().remove(scopeKey(pluginId)).apply()
    }

    /**
     * Best-effort server-side logout, then drop cookies and DOM storage for the
     * plugin. [keepHosts] protects the page that is currently submitting a login form.
     */
    suspend fun reset(
        pluginId: String,
        allowedHosts: List<String>,
        logoutUrls: List<String> = emptyList(),
        extraScopeUrls: Collection<String> = emptyList(),
        keepHosts: Set<String> = emptySet(),
        webView: WebView? = null,
    ): Int {
        val scopes = prefs.getStringSet(scopeKey(pluginId), emptySet()).orEmpty() + extraScopeUrls
        val targets = sanitizeLogoutUrls(logoutUrls, allowedHosts)
            .filter { url -> keepHosts.none { hostOf(url) == normalizeResetHost(it) } }
        if (targets.isNotEmpty()) {
            withContext(Dispatchers.IO) { targets.forEach(::requestLogout) }
        }
        return withContext(Dispatchers.Main) {
            val expired = expireCookies(allowedHosts, scopes, keepHosts)
            storageOrigins(allowedHosts, scopes)
                .filter { origin -> keepHosts.none { keep -> hostOf(origin) == normalizeResetHost(keep) } }
                .forEach { WebStorage.getInstance().deleteOrigin(it) }
            if (keepHosts.isEmpty()) {
                webView?.clearCache(true)
                webView?.clearFormData()
            }
            PluginLogger.info(
                "plugin.web_session.reset",
                mapOf(
                    "pluginId" to pluginId,
                    "cookies" to expired,
                    "logoutUrls" to targets.size,
                    "partial" to keepHosts.isNotEmpty(),
                ),
            )
            expired
        }
    }

    private suspend fun expireCookies(allowedHosts: List<String>, scopes: Collection<String>, keepHosts: Set<String>): Int {
        val manager = CookieManager.getInstance()
        var count = 0
        cookieScopeUrls(allowedHosts, scopes)
            .filter { url -> keepHosts.none { hostOf(url) == normalizeResetHost(it) } }
            .forEach { url ->
                val host = hostOf(url) ?: return@forEach
                val path = runCatching { URI(url).rawPath }.getOrNull().orEmpty().ifBlank { "/" }
                cookieNames(manager.getCookie(url)).forEach { name ->
                    expiredCookieHeaders(name, host, path, keepHosts).forEach { header ->
                        suspendCancellableCoroutine<Unit> { continuation ->
                            manager.setCookie(url, header) {
                                if (continuation.isActive) continuation.resume(Unit)
                            }
                        }
                    }
                    count++
                }
            }
        manager.flush()
        return count
    }

    private fun requestLogout(url: String) {
        val connection = runCatching { URL(url).openConnection() as HttpURLConnection }.getOrNull() ?: return
        runCatching {
            connection.connectTimeout = LOGOUT_TIMEOUT_MS
            connection.readTimeout = LOGOUT_TIMEOUT_MS
            connection.instanceFollowRedirects = false
            connection.useCaches = false
            CookieManager.getInstance().getCookie(url)?.takeIf(String::isNotBlank)?.let {
                connection.setRequestProperty("Cookie", it)
            }
            connection.responseCode
        }.onFailure { error ->
            PluginLogger.warn(
                "plugin.web_session.logout_failed",
                mapOf("url" to PluginLogger.sanitizeUrl(url), "error" to (error.message ?: error.javaClass.simpleName)),
            )
        }
        connection.disconnect()
    }

    private fun scopeKey(pluginId: String) = "scopes|$pluginId"

    companion object {
        /** Excluded from backups together with saved web credentials. */
        const val PREFS_NAME = "plugin_web_session_scopes"
        private const val LOGOUT_TIMEOUT_MS = 5_000
    }
}

private const val MAX_SCOPE_DEPTH = 8
private const val MAX_REMEMBERED_SCOPES = 64

/** Keep the origin and bounded directory scopes; null when unusable. */
internal fun cookieScopeOf(url: String): String? {
    val uri = runCatching { URI(url.trim()) }.getOrNull() ?: return null
    val scheme = uri.scheme?.lowercase(Locale.ROOT)?.takeIf { it == "https" || it == "http" } ?: return null
    val host = uri.host?.let(::normalizeResetHost)?.takeIf(String::isNotBlank) ?: return null
    val port = if (uri.port > 0) ":${uri.port}" else ""
    val segments = uri.rawPath.orEmpty().split('/').filter(String::isNotBlank)
    // Drop a trailing file-like segment such as courseTable.action.
    val directories = if (segments.isNotEmpty() && !uri.rawPath.endsWith('/')) segments.dropLast(1) else segments
    val path = directories.take(MAX_SCOPE_DEPTH).joinToString("") { "/$it" }
    return "$scheme://$host$port$path/"
}

internal fun mergeCookieScopes(existing: Set<String>, urls: Collection<String>): Set<String> {
    val merged = LinkedHashSet(existing)
    urls.mapNotNullTo(merged, ::cookieScopeOf)
    return merged.toList().takeLast(MAX_REMEMBERED_SCOPES).toSet()
}

/** Paths taken from capture rules, so path-limited app cookies are reachable before any visit is recorded. */
internal fun captureScopeUrls(hosts: List<String>, pathParts: List<String>): List<String> =
    hosts.flatMap { host ->
        pathParts.filter { it.startsWith("/") }.mapNotNull { cookieScopeOf("https://$host$it") }
    }

/**
 * URLs whose cookie headers cover every path prefix the plugin is known to use. CookieManager only
 * returns cookies whose Path matches the queried URL, so root URLs alone miss `Path=/app` cookies.
 */
internal fun cookieScopeUrls(allowedHosts: List<String>, scopes: Collection<String>): List<String> {
    val allowed = allowedHosts.map(::normalizeResetHost).filter(String::isNotBlank)
    val urls = LinkedHashSet<String>()
    allowed.forEach { urls += "https://$it/"; urls += "http://$it/" }
    scopes.forEach { scope ->
        val normalized = cookieScopeOf(scope) ?: return@forEach
        if (!isAllowedHost(normalized, allowed)) return@forEach
        val uri = URI(normalized)
        val origin = "${uri.scheme}://${uri.host}${if (uri.port > 0) ":${uri.port}" else ""}"
        var path = ""
        urls += "$origin/"
        uri.rawPath.split('/').filter(String::isNotBlank).forEach { segment ->
            path += "/$segment"
            urls += "$origin$path/"
        }
    }
    return urls.toList()
}

/** Origins whose localStorage/IndexedDB belong to the plugin's login. */
internal fun storageOrigins(allowedHosts: List<String>, scopes: Collection<String>): Set<String> {
    val allowed = allowedHosts.map(::normalizeResetHost).filter(String::isNotBlank)
    val origins = LinkedHashSet<String>()
    allowed.forEach { origins += "https://$it"; origins += "http://$it" }
    scopes.mapNotNull(::cookieScopeOf).filter { isAllowedHost(it, allowed) }.forEach { scope ->
        val uri = URI(scope)
        origins += "${uri.scheme}://${uri.host}${if (uri.port > 0) ":${uri.port}" else ""}"
    }
    return origins
}

internal fun cookieNames(header: String?): List<String> =
    header.orEmpty()
        .split(';')
        .mapNotNull { it.substringBefore('=').trim().takeIf(String::isNotBlank) }
        .distinct()

/**
 * Set-Cookie values that expire [name] whether it was host-only or set for this host or a parent
 * domain, at [path] and its trailing-slash twin. Domains covering a kept host are skipped.
 */
internal fun expiredCookieHeaders(name: String, host: String, path: String, keepHosts: Set<String> = emptySet()): List<String> {
    val normalizedHost = normalizeResetHost(host)
    val kept = keepHosts.map(::normalizeResetHost)
    val trimmed = path.trimEnd('/')
    val paths = listOf(trimmed.ifBlank { "/" }, "$trimmed/").distinct()
    val domains = parentDomains(normalizedHost).filter { domain ->
        kept.none { it == domain || it.endsWith(".$domain") }
    }
    return paths.flatMap { cookiePath ->
        listOf("$name=; Max-Age=0; Path=$cookiePath") +
            domains.map { "$name=; Max-Age=0; Path=$cookiePath; Domain=.$it" }
    }
}

/** The host itself and every parent with at least two labels. */
internal fun parentDomains(host: String): List<String> {
    val labels = normalizeResetHost(host).split('.').filter(String::isNotBlank)
    if (labels.size < 2 || labels.all { label -> label.all(Char::isDigit) }) return listOf(labels.joinToString("."))
    return (0..labels.size - 2).map { labels.drop(it).joinToString(".") }
}

/** Keep only HTTP(S) logout URLs on the plugin's own allowed hosts. */
internal fun sanitizeLogoutUrls(urls: List<String>, allowedHosts: List<String>): List<String> =
    urls.map(String::trim)
        .filter { url ->
            val scheme = runCatching { URI(url).scheme?.lowercase(Locale.ROOT) }.getOrNull()
            (scheme == "https" || scheme == "http") && isAllowedHost(url, allowedHosts)
        }
        .distinct()
        .take(8)

private fun hostOf(url: String): String? =
    runCatching { URI(url).host }.getOrNull()?.let(::normalizeResetHost)

internal fun normalizeResetHost(host: String): String = host.trim().trimEnd('.').lowercase(Locale.ROOT)
