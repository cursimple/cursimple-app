package com.x500x.cursimple.feature.plugin.home

import android.content.Context
import com.x500x.cursimple.core.plugin.manifest.PluginManifest
import com.x500x.cursimple.core.plugin.manifest.PluginPermission
import com.x500x.cursimple.feature.plugin.extension.ExtensionFailureKind
import com.x500x.cursimple.feature.plugin.extension.ExtensionRunMode
import com.x500x.cursimple.feature.plugin.extension.ExtensionRunRequest
import com.x500x.cursimple.feature.plugin.extension.ExtensionRunResult
import com.x500x.cursimple.feature.plugin.extension.ExtensionRuntime
import com.x500x.cursimple.feature.plugin.extension.ExtensionUrls
import kotlinx.serialization.json.*

/** A page-script run the runtime could not complete. [kind] says which kind of failure it was. */
internal class PluginPageRunException(val kind: ExtensionFailureKind, message: String) : IllegalStateException(message)

/**
 * Runs one function of the plugin's page script ([mode]) with the page's [action], and returns what it returns.
 * [capturedPage] is the sign-in page the user saved, when the plugin asked for it; the host does not parse it.
 */
internal suspend fun runPluginPageScript(
    context: Context,
    manifest: PluginManifest,
    script: String,
    mode: ExtensionRunMode,
    action: JsonObject,
    language: String,
    capturedPage: String? = null,
): JsonObject {
    val home = requireNotNull(manifest.home)
    require(PluginPermission.NetworkFetch in manifest.permissions && PluginPermission.StoragePlugin in manifest.permissions)
    require(ExtensionUrls.isAllowed(home.runUrl, manifest.allowedHosts))
    require(capturedPage == null || capturedPage.toByteArray().size <= 512 * 1024)
    val input = if (capturedPage == null) action else JsonObject(action + ("pageHtml" to JsonPrimitive(capturedPage)))
    val result = ExtensionRuntime(context).run(ExtensionRunRequest(
        pluginId = manifest.id,
        mode = mode,
        url = home.runUrl,
        entrySource = script,
        allowedHosts = manifest.allowedHosts,
        permissions = manifest.permissions.map { it.id }.toSet(),
        userAgent = manifest.userAgent,
        settings = buildJsonObject { put("language", language) },
        state = JsonObject(emptyMap()),
        action = input,
        isolated = capturedPage != null,
        waitForRunUrl = capturedPage == null,
        timeoutMs = manifest.limits.timeoutMs.coerceIn(10_000, 90_000),
        maxOutputBytes = manifest.limits.maxOutputBytes.coerceIn(64 * 1024, 4 * 1024 * 1024),
    ))
    return when (result) {
        is ExtensionRunResult.Completed -> result.result
        is ExtensionRunResult.Failed -> throw PluginPageRunException(result.kind, result.message)
    }
}
