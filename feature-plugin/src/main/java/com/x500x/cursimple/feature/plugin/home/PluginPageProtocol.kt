package com.x500x.cursimple.feature.plugin.home

import com.x500x.cursimple.feature.plugin.isAllowedHost
import java.net.URI
import kotlinx.serialization.json.*

/** Name of the object a plugin page calls into. Only its `call` method is exposed. */
internal const val PAGE_BRIDGE_NAME = "CurSimplePageHost"

/** Limits on what crosses between a plugin page and the host. Anything over a limit is refused. */
internal const val MAX_PAGE_REQUEST_CHARS = 5_000_000
internal const val MAX_ACTION_CHARS = 16_384
internal const val MAX_CAPTURED_PAGE_CHARS = 512 * 1024
internal const val MAX_STORAGE_VALUE_CHARS = 4_000_000
internal const val MAX_STORAGE_TOTAL_BYTES = 8L * 1024 * 1024
internal const val MAX_LOGIN_ACTIONS = 3

private val storageKeyPattern = Regex("[A-Za-z0-9_.-]{1,64}")
private val actionIdPattern = Regex("[A-Za-z0-9_-]{1,32}")

/** A refusal the page can show. [code] is stable; the page maps it to its own wording. */
internal class PluginPageCodeException(val code: String, val detail: String = "") : IllegalStateException(code)

internal fun validStorageKey(key: String): Boolean = storageKeyPattern.matches(key)

internal fun validActionId(id: String): Boolean = actionIdPattern.matches(id)

/** HTML capture limits are byte-based, just like the runtime input limit. */
internal fun acceptedCapturedPage(html: String?): String? =
    html?.takeIf { it.length <= MAX_CAPTURED_PAGE_CHARS && it.toByteArray(Charsets.UTF_8).size <= MAX_CAPTURED_PAGE_CHARS }

/** Sign-in pages are reachable only over https, on the plugin's declared hosts, without credentials in the address. */
internal fun validLoginUrl(url: String, allowedHosts: List<String>): Boolean {
    val uri = runCatching { URI(url) }.getOrNull() ?: return false
    return uri.scheme == "https" && uri.rawUserInfo == null && isAllowedHost(url, allowedHosts)
}

/** A button on the sign-in sheet. [capture] asks the host to keep the page the sheet shows, for the plugin to parse. */
internal data class PluginPageLoginAction(val id: String, val label: String, val capture: Boolean)

/** The sign-in sheet a plugin page asks for. Its title, help text and buttons are the plugin's own words. */
internal data class PluginPageLoginRequest(
    val url: String,
    val helpText: String,
    val actions: List<PluginPageLoginAction>,
    val title: String = "",
    /** Press sign-in once by itself after filling the saved account. */
    val autoSubmit: Boolean = false,
)

internal fun parseLoginActions(value: JsonElement?): List<PluginPageLoginAction> {
    val array = value as? JsonArray ?: return emptyList()
    if (array.size > MAX_LOGIN_ACTIONS) throw PluginPageCodeException("invalid_argument")
    return array.map { element ->
        val item = element as? JsonObject ?: throw PluginPageCodeException("invalid_argument")
        val id = item.stringField("id")?.takeIf(::validActionId) ?: throw PluginPageCodeException("invalid_argument")
        val label = item.stringField("label")?.takeIf { it.isNotBlank() && it.length <= 64 }
            ?: throw PluginPageCodeException("invalid_argument")
        PluginPageLoginAction(id, label, (item["capture"] as? JsonPrimitive)?.booleanOrNull == true)
    }
}

internal fun JsonObject.stringField(key: String): String? =
    (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content

/** A working overlay the plugin page asks the sign-in sheet to show. */
internal data class PluginPageLoginBusy(val title: String, val text: String)
