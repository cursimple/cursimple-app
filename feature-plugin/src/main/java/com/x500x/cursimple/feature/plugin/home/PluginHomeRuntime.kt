package com.x500x.cursimple.feature.plugin.home

import android.content.Context
import com.x500x.cursimple.core.plugin.manifest.PluginManifest
import com.x500x.cursimple.core.plugin.manifest.PluginPermission
import com.x500x.cursimple.feature.plugin.extension.ExtensionRunMode
import com.x500x.cursimple.feature.plugin.extension.ExtensionRunRequest
import com.x500x.cursimple.feature.plugin.extension.ExtensionRunResult
import com.x500x.cursimple.feature.plugin.extension.ExtensionRuntime
import com.x500x.cursimple.feature.plugin.extension.ExtensionUrls
import kotlinx.serialization.json.*

internal suspend fun runPluginHomeQuery(
    context: Context,
    manifest: PluginManifest,
    script: String,
    sectionId: String?,
    language: String,
    renderedHtml: String? = null,
): JsonObject {
    val home = requireNotNull(manifest.home)
    require(PluginPermission.NetworkFetch in manifest.permissions && PluginPermission.StoragePlugin in manifest.permissions)
    require(ExtensionUrls.isAllowed(home.runUrl, manifest.allowedHosts))
    require(sectionId == null || home.sections.any { it.id == sectionId })
    require(renderedHtml == null || renderedHtml.toByteArray().size <= 512 * 1024)
    val result = ExtensionRuntime(context).run(ExtensionRunRequest(
        pluginId = manifest.id,
        mode = if (sectionId == null) ExtensionRunMode.CheckLogin else ExtensionRunMode.Sync,
        url = home.runUrl,
        entrySource = script,
        allowedHosts = manifest.allowedHosts,
        permissions = manifest.permissions.map { it.id }.toSet(),
        userAgent = manifest.userAgent,
        settings = buildJsonObject { put("language", language) },
        state = JsonObject(emptyMap()),
        action = buildJsonObject {
            sectionId?.let { put("sectionId", it) }
            renderedHtml?.let { put("pageHtml", it) }
        },
        isolated = renderedHtml != null,
        waitForRunUrl = renderedHtml == null,
        timeoutMs = manifest.limits.timeoutMs.coerceIn(10_000, 90_000),
        maxOutputBytes = manifest.limits.maxOutputBytes.coerceIn(64 * 1024, 4 * 1024 * 1024),
    ))
    return when (result) {
        is ExtensionRunResult.Completed -> result.result
        is ExtensionRunResult.Failed -> error(result.message)
    }
}
