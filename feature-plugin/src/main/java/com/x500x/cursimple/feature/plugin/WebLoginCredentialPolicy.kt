package com.x500x.cursimple.feature.plugin

import com.x500x.cursimple.core.plugin.school.SchoolPortalCatalog
import com.x500x.cursimple.core.plugin.school.SchoolPortalEntry
import java.net.URI
import java.util.Locale

/** Only catalog-bound school plugins may share the built-in importer's exact-host credentials. */
internal class WebLoginCredentialPolicy(schools: List<SchoolPortalEntry>) {
    private data class School(val owner: String, val pluginId: String, val hosts: List<String>) {
        fun accepts(host: String) = hosts.any { host == it || host.endsWith(".$it") }
        val aliases get() = listOf(owner, pluginId, SchoolPortalCatalog.PLUGIN_ID)
    }

    private val sharedSchools = schools.mapNotNull { entry ->
        val pluginId = entry.pluginId?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
        if (entry.pluginRepo.isNullOrBlank()) return@mapNotNull null
        val uri = runCatching { URI(entry.portalUrl.orEmpty()) }.getOrNull() ?: return@mapNotNull null
        val host = uri.host?.let(::normalizeCredentialHost) ?: return@mapNotNull null
        if (!uri.scheme.equals("https", true)) return@mapNotNull null
        School("school:${entry.id}", pluginId, SchoolPortalCatalog.allowedHostsFor(host))
    }

    /** Shared owner first, followed by exact-host legacy owners to migrate. */
    fun owners(pluginId: String, host: String): List<String> {
        val normalized = normalizeCredentialHost(host)
        val school = sharedSchools.singleOrNull {
            (pluginId == it.pluginId || pluginId == SchoolPortalCatalog.PLUGIN_ID) && it.accepts(normalized)
        } ?: return listOf(pluginId)
        return listOf(school.owner, pluginId) + school.aliases.filter { it != school.owner && it != pluginId }
    }

    fun owns(pluginId: String, owner: String, host: String): Boolean =
        owner == pluginId || sharedSchools.any {
            (pluginId == it.pluginId || pluginId == SchoolPortalCatalog.PLUGIN_ID) &&
                owner in it.aliases && it.accepts(normalizeCredentialHost(host))
        }
}

internal fun normalizeCredentialHost(host: String): String = host.trim().trimEnd('.').lowercase(Locale.ROOT)
