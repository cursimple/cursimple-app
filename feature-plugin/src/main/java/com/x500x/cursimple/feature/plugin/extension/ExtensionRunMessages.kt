package com.x500x.cursimple.feature.plugin.extension

import android.content.res.Resources
import com.x500x.cursimple.feature.plugin.R

/**
 * User-facing text for run failures, resolved in the caller's language. It is only displayed:
 * callers decide what went wrong from [ExtensionFailureKind], never by reading this text.
 */
internal enum class BridgeError {
    UnsupportedMode, BadRequest, ReceiptPermission, ReceiptUnavailable,
    SessionPermission, SessionUnavailable, UnsupportedCall, CallFailed,
}

internal interface ExtensionRunMessages {
    fun entryNotAllowed(url: String): String
    fun cannotOpen(url: String, reason: String?): String
    fun timeout(seconds: Long): String
    fun scriptError(): String
    fun noResult(): String
    fun tooLarge(bytes: Int): String
    fun notJson(): String

    /** Errors the native bridge hands back to a component's script. */
    fun bridge(error: BridgeError): String

    /**
     * Text the generated JS runtime reports itself. Templates use `{0}` as the placeholder, which the
     * script fills in, so every language keeps its own word order.
     */
    fun scriptTemplates(): Map<String, String>

    companion object {
        fun from(resources: Resources): ExtensionRunMessages = AndroidRunMessages(resources)

        /** Plain English for JVM unit tests and callers that have no Android resources. */
        val Plain: ExtensionRunMessages = object : ExtensionRunMessages {
            override fun entryNotAllowed(url: String) = "Entry address is outside the declared sites: $url"
            override fun cannotOpen(url: String, reason: String?) = "Could not open $url: ${reason ?: "network error"}"
            override fun timeout(seconds: Long) = "The component timed out after $seconds seconds"
            override fun scriptError() = "The component script failed"
            override fun noResult() = "The component returned nothing"
            override fun tooLarge(bytes: Int) = "The component returned too much data ($bytes bytes)"
            override fun notJson() = "The component did not return a JSON object"
            override fun bridge(error: BridgeError) = when (error) {
                BridgeError.UnsupportedMode -> "This run mode does not support host transport"
                BridgeError.BadRequest -> "Malformed request"
                BridgeError.ReceiptPermission -> "The component did not declare the notification receipt permission"
                BridgeError.ReceiptUnavailable -> "Notification receipts are unavailable"
                BridgeError.SessionPermission -> "The component did not declare the encrypted session permission"
                BridgeError.SessionUnavailable -> "Session storage is unavailable"
                BridgeError.UnsupportedCall -> "Unsupported runtime call"
                BridgeError.CallFailed -> "Runtime call failed"
            }
            override fun scriptTemplates() = mapOf(
                "transportUnavailable" to "The host transport is unavailable",
                "permissionMissing" to "The component did not declare the permission: {0}",
                "accessDenied" to "Access not allowed: {0}",
                "loadFailed" to "The component script failed to load: {0}",
                "functionMissing" to "The component does not export a {0} function",
                "tooLarge" to "The component returned too much data",
            )
        }
    }
}

private class AndroidRunMessages(private val resources: Resources) : ExtensionRunMessages {
    override fun entryNotAllowed(url: String) = resources.getString(R.string.extension_run_entry_not_allowed, url)
    override fun cannotOpen(url: String, reason: String?) = resources.getString(
        R.string.extension_run_cannot_open, url, reason ?: resources.getString(R.string.extension_run_network_error),
    )
    override fun timeout(seconds: Long) = resources.getString(R.string.extension_run_timeout, seconds)
    override fun scriptError() = resources.getString(R.string.extension_run_script_error)
    override fun noResult() = resources.getString(R.string.extension_run_no_result)
    override fun tooLarge(bytes: Int) = resources.getString(R.string.extension_run_too_large, bytes)
    override fun notJson() = resources.getString(R.string.extension_run_not_json)
    override fun bridge(error: BridgeError) = resources.getString(when (error) {
        BridgeError.UnsupportedMode -> R.string.extension_bridge_unsupported_mode
        BridgeError.BadRequest -> R.string.extension_bridge_bad_request
        BridgeError.ReceiptPermission -> R.string.extension_bridge_receipt_permission
        BridgeError.ReceiptUnavailable -> R.string.extension_bridge_receipt_unavailable
        BridgeError.SessionPermission -> R.string.extension_bridge_session_permission
        BridgeError.SessionUnavailable -> R.string.extension_bridge_session_unavailable
        BridgeError.UnsupportedCall -> R.string.extension_bridge_unsupported_call
        BridgeError.CallFailed -> R.string.extension_bridge_call_failed
    })
    override fun scriptTemplates() = mapOf(
        "transportUnavailable" to resources.getString(R.string.extension_js_transport_unavailable),
        "permissionMissing" to resources.getString(R.string.extension_js_permission_missing, "{0}"),
        "accessDenied" to resources.getString(R.string.extension_js_access_denied, "{0}"),
        "loadFailed" to resources.getString(R.string.extension_js_load_failed, "{0}"),
        "functionMissing" to resources.getString(R.string.extension_js_function_missing, "{0}"),
        "tooLarge" to resources.getString(R.string.extension_js_too_large),
    )
}
