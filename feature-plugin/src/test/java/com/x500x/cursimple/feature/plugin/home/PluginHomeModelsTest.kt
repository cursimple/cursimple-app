package com.x500x.cursimple.feature.plugin.home

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class PluginHomeModelsTest {
    private val alpha = PluginHomeAccount("alpha", "Alpha", "2099000001")
    private val beta = PluginHomeAccount("beta", "Beta", "2099000002")
    private fun section(value: String) = PluginHomeSection(
        "grades", listOf("Course", "Grade"), listOf(listOf(value, "90")), savedAt = 1000,
    )

    @Test fun cachedResultsHaveNoExpiryAndSurviveSerialization() {
        val saved = PluginHomeCacheState().withSection(alpha, section("Databases"))
        val reopened = homeJson.decodeFromString<PluginHomeCacheState>(homeJson.encodeToString(saved))
        assertEquals(saved, reopened)
        assertEquals("Databases", reopened.active!!.sections["grades"]!!.rows.single().first())
        assertEquals("grades", reopened.active!!.lastSectionId)
    }

    @Test fun accountChecksAndSwitchesDoNotReplaceAnotherAccountsResults() {
        val saved = PluginHomeCacheState().withSection(alpha, section("Alpha course"))
        val checked = saved.withAccount(beta, 2000)
        assertEquals("Alpha course", checked.accounts["alpha"]!!.sections["grades"]!!.rows[0][0])
        assertTrue(checked.active!!.sections.isEmpty())
        val refreshed = checked.withSection(beta, section("Beta course"))
        assertEquals("Alpha course", refreshed.accounts["alpha"]!!.sections["grades"]!!.rows[0][0])
        assertEquals("Beta course", refreshed.active!!.sections["grades"]!!.rows[0][0])
    }

    @Test fun invalidOrMissingLoginCannotCreateAnAccount() {
        assertNull(parseHomeAccount(buildJsonObject { put("loggedIn", false) }))
        assertNull(parseHomeAccount(buildJsonObject { put("loggedIn", true) }))
        assertThrows(IllegalArgumentException::class.java) {
            parseHomeAccount(buildJsonObject {
                put("loggedIn", true)
                put("account", buildJsonObject { put("id", " ") })
            })
        }
    }

    @Test fun incompleteOrUnexpectedSectionCannotReplaceSavedData() {
        val good = homeJson.encodeToJsonElement(section("Valid")).jsonObject
        val parsed = parseHomeSection(buildJsonObject { put("section", good) }, "grades", 9999)
        assertEquals(9999L, parsed.savedAt)
        assertThrows(IllegalArgumentException::class.java) {
            parseHomeSection(buildJsonObject { put("section", good) }, "credits", 9999)
        }
        val broken = good.toMutableMap().apply { put("rows", buildJsonArray { add(buildJsonArray { add("Missing grade") }) }) }
        assertThrows(IllegalArgumentException::class.java) {
            parseHomeSection(buildJsonObject { put("section", JsonObject(broken)) }, "grades", 9999)
        }
        assertEquals("Valid", section("Valid").rows[0][0])
    }

    @Test fun aVerifiedEmptyListIsStoredAsAnEmptyList() {
        val empty = section("Previous").copy(rows = emptyList())
        val result = parseHomeSection(buildJsonObject {
            put("section", homeJson.encodeToJsonElement(empty))
        }, "grades", 100)
        assertTrue(result.rows.isEmpty())
    }

    @Test fun savingRenderedDataDoesNotPretendToVerifyAnOnlineSession() {
        val saved = PluginHomeCacheState().withAccount(alpha, 200)
            .withSection(alpha, section("Saved page").copy(savedAt = 1000), verifiedAt = null)
        assertEquals(200L, saved.active!!.verifiedAt)
        assertEquals(1000L, saved.active!!.sections["grades"]!!.savedAt)
    }
}
