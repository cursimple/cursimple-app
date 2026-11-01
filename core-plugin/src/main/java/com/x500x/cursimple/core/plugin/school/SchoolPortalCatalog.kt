package com.x500x.cursimple.core.plugin.school

import android.content.Context
import com.x500x.cursimple.core.kernel.model.TermTimingProfile
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.net.URI

/**
 * One school in the fixed catalog. A school is either bound to a single plugin repository,
 * or it is opened directly through its educational administration portal.
 */
@Serializable
data class SchoolPortalEntry(
    @SerialName("id") val id: String,
    @SerialName("name") val name: String,
    @SerialName("portalUrl") val portalUrl: String? = null,
    @SerialName("pluginRepo") val pluginRepo: String? = null,
    /** Host-maintained binding for sharing saved logins with this school's plugin. */
    @SerialName("pluginId") val pluginId: String? = null,
    @SerialName("tiers") val tiers: List<String> = emptyList(),
    @SerialName("timingProfile") val timingProfile: TermTimingProfile? = null,
) {
    val usesPlugin: Boolean get() = !pluginRepo.isNullOrBlank()
}

object SchoolPortalCatalog {
    const val ASSET_PATH = "school-portal/schools.json"

    /** Synthetic owner of portal imports; it is not an installed plugin. */
    const val PLUGIN_ID = "builtin.school-portal"

    private val json = Json { ignoreUnknownKeys = true }

    fun parse(text: String): List<SchoolPortalEntry> = json.decodeFromString<List<SchoolPortalEntry>>(text)

    fun load(context: Context): List<SchoolPortalEntry> =
        context.assets.open(ASSET_PATH).bufferedReader().use { parse(it.readText()) }

    /** Case-insensitive substring match on the school name; blank query returns everything. */
    fun search(entries: List<SchoolPortalEntry>, query: String): List<SchoolPortalEntry> {
        val needle = query.trim().lowercase()
        if (needle.isEmpty()) return entries
        return entries.filter { it.name.lowercase().contains(needle) }
    }

    /**
     * Hosts a portal session may visit: the portal host plus its school domain, so single sign-on
     * pages on sibling subdomains work. Public suffixes such as edu.cn are never widened to.
     */
    fun allowedHostsFor(host: String): List<String> {
        val normalized = host.trim().lowercase()
        val labels = normalized.split('.')
        val hosts = mutableListOf(normalized)
        if (labels.size >= 3) {
            val parent = labels.drop(1).joinToString(".")
            val parentLabels = parent.split('.')
            val publicSuffixes = setOf("edu.cn", "com.cn", "ac.cn", "gov.cn", "net.cn", "org.cn", "edu", "com", "cn", "org", "net")
            if (parentLabels.size >= 2 && parent !in publicSuffixes) hosts += parent
        }
        return hosts
    }

    /** Portal addresses must be HTTPS with a host name; IP literals usually point at internal gateways. */
    fun isOpenablePortal(url: String?): Boolean {
        val raw = url?.trim().orEmpty()
        if (raw.isEmpty()) return false
        val uri = runCatching { URI(raw) }.getOrNull() ?: return false
        val host = uri.host.orEmpty()
        return uri.scheme.equals("https", ignoreCase = true) &&
            host.contains('.') &&
            !host.all { it.isDigit() || it == '.' || it == ':' }
    }
}
