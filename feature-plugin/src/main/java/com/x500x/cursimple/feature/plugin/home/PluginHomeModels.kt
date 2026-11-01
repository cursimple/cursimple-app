package com.x500x.cursimple.feature.plugin.home

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*

@Serializable
data class PluginHomeAccount(
    val id: String,
    val name: String = "",
    val number: String = "",
    val school: String = "",
)

@Serializable
data class PluginHomeField(val label: String, val value: String)

@Serializable
data class PluginHomeSection(
    val id: String,
    val columns: List<String> = emptyList(),
    val rows: List<List<String>> = emptyList(),
    val summary: List<PluginHomeField> = emptyList(),
    val message: String = "",
    val savedAt: Long = 0,
)

@Serializable
data class PluginHomeAccountCache(
    val account: PluginHomeAccount,
    val verifiedAt: Long = 0,
    val sections: Map<String, PluginHomeSection> = emptyMap(),
    val lastSectionId: String = "",
)

@Serializable
data class PluginHomeCacheState(
    val activeAccountId: String = "",
    val accounts: Map<String, PluginHomeAccountCache> = emptyMap(),
) {
    val active: PluginHomeAccountCache? get() = accounts[activeAccountId]
}

internal val homeJson = Json { ignoreUnknownKeys = true; encodeDefaults = true }

internal fun parseHomeAccount(result: JsonObject): PluginHomeAccount? {
    if ((result["loggedIn"] as? JsonPrimitive)?.booleanOrNull != true) return null
    val account = result["account"] as? JsonObject ?: return null
    val decoded = homeJson.decodeFromJsonElement<PluginHomeAccount>(account)
    require(decoded.id.isNotBlank() && decoded.id.length <= 256) { "Invalid account ID" }
    require(listOf(decoded.name, decoded.number, decoded.school).all { it.length <= 256 }) { "Invalid account details" }
    return decoded
}

internal fun parseHomeSection(result: JsonObject, expectedId: String, now: Long): PluginHomeSection {
    val value = result["section"] as? JsonObject ?: error("Missing section")
    val section = homeJson.decodeFromJsonElement<PluginHomeSection>(value)
    require(section.id == expectedId) { "Section mismatch" }
    require(section.columns.isNotEmpty() && section.columns.size <= 24) { "Invalid columns" }
    require(section.columns.all { it.isNotBlank() && it.length <= 160 }) { "Invalid column labels" }
    require(section.rows.size <= 3000) { "Too many rows" }
    require(section.rows.all { row ->
        row.size == section.columns.size && row.firstOrNull()?.isNotBlank() == true && row.all { it.length <= 4000 }
    }) { "Incomplete rows" }
    require(section.summary.size <= 16 && section.summary.all { it.label.length <= 160 && it.value.length <= 500 }) {
        "Invalid summary"
    }
    require(section.message.length <= 1000) { "Invalid message" }
    return section.copy(savedAt = now)
}

/** Failed refreshes never call this function; an account can update only its own section. */
internal fun PluginHomeCacheState.withAccount(account: PluginHomeAccount, verifiedAt: Long): PluginHomeCacheState {
    val previous = accounts[account.id]
    return copy(
        activeAccountId = account.id,
        accounts = accounts + (account.id to (previous ?: PluginHomeAccountCache(account)).copy(
            account = account,
            verifiedAt = verifiedAt,
        )),
    )
}

internal fun PluginHomeCacheState.withSection(
    account: PluginHomeAccount,
    section: PluginHomeSection,
    verifiedAt: Long? = section.savedAt,
): PluginHomeCacheState {
    val previous = accounts[account.id] ?: PluginHomeAccountCache(account)
    return copy(
        activeAccountId = account.id,
        accounts = accounts + (account.id to previous.copy(
            account = account, verifiedAt = verifiedAt ?: previous.verifiedAt,
            sections = previous.sections + (section.id to section),
            lastSectionId = section.id,
        )),
    )
}
