package com.x500x.cursimple.feature.plugin

import android.content.Context
import androidx.test.platform.app.InstrumentationRegistry
import com.x500x.cursimple.core.plugin.school.SchoolPortalCatalog
import com.x500x.cursimple.core.plugin.school.SchoolPortalEntry
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class WebLoginCredentialStoreTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val general = SchoolPortalCatalog.PLUGIN_ID
    private val policy = WebLoginCredentialPolicy(listOf(
        SchoolPortalEntry("a", "A", "https://portal.a.edu.cn", "org/a", pluginId = "plugin-a"),
        SchoolPortalEntry("b", "B", "https://portal.b.edu.cn", "org/b", pluginId = "plugin-b"),
    ))
    private fun store() = WebLoginCredentialStore(context, policy)
    private fun credential(host: String = "portal.a.edu.cn", password: String = "test-password") =
        WebLoginCredential(host, "fixture-student", password)

    @Before fun prepare() {
        // This is a self-targeting test APK; never clear the user's application preferences.
        check(context.packageName == "com.x500x.cursimple.feature.plugin.test")
        context.getSharedPreferences(WebLoginCredentialStore.PREFS_NAME, Context.MODE_PRIVATE).edit().clear().commit()
    }

    @After fun clean() {
        context.getSharedPreferences(WebLoginCredentialStore.PREFS_NAME, Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test fun legacyGeneralLoginMigratesAndUpdatesInBothDirections() {
        val old = WebLoginCredentialStore(context, WebLoginCredentialPolicy(emptyList()))
        assertTrue(old.save(general, credential()))
        assertEquals(credential(), store().find("plugin-a", "portal.a.edu.cn"))
        assertNull(old.find(general, "portal.a.edu.cn"))
        assertTrue(store().save("plugin-a", credential(password = "updated-password")))
        assertEquals(credential(password = "updated-password"), store().find(general, "portal.a.edu.cn"))
        assertTrue(store().hasSavedCredentials("plugin-a"))
        assertTrue(store().hasSavedCredentials(general))
        val raw = context.getSharedPreferences(WebLoginCredentialStore.PREFS_NAME, Context.MODE_PRIVATE).all.toString()
        assertFalse(raw.contains("updated-password"))
        assertFalse(raw.contains("fixture-student"))
    }

    @Test fun clearRemovesSharedLegacyCredentialsAndNeverSaveWithoutTouchingOtherSchools() {
        val old = WebLoginCredentialStore(context, WebLoginCredentialPolicy(emptyList()))
        old.save(general, credential())
        old.save("plugin-a", credential(password = "legacy-plugin-password"))
        old.rememberAccount(general, "portal.a.edu.cn", "fixture-student")
        old.setNeverSave(general, "sso.a.edu.cn")
        store().save("plugin-b", credential("portal.b.edu.cn"))
        store().clear("plugin-a")
        assertNull(store().find(general, "portal.a.edu.cn"))
        assertNull(store().find("plugin-a", "portal.a.edu.cn"))
        assertFalse(store().isNeverSave(general, "sso.a.edu.cn"))
        assertFalse(store().hasAny("plugin-a"))
        assertEquals(credential("portal.b.edu.cn"), store().find("plugin-b", "portal.b.edu.cn"))
        val keys = context.getSharedPreferences(WebLoginCredentialStore.PREFS_NAME, Context.MODE_PRIVATE).all.keys
        assertFalse(keys.any { it.endsWith("|portal.a.edu.cn") })
    }

    @Test fun arbitraryPluginAndSiblingHostNeverReceiveSavedPassword() {
        store().save(general, credential())
        assertNull(store().find("arbitrary-plugin", "portal.a.edu.cn"))
        assertNull(store().find("plugin-b", "portal.a.edu.cn"))
        assertNull(store().find("plugin-a", "sso.a.edu.cn"))
        assertNull(store().find("plugin-a", "portal.a.edu.cn.attacker.test"))
    }

    @Test fun olderPlaintextAccountNameIsMigratedWithoutLosingItsPassword() {
        val old = WebLoginCredentialStore(context, WebLoginCredentialPolicy(emptyList()))
        old.save(general, credential())
        val preferences = context.getSharedPreferences(WebLoginCredentialStore.PREFS_NAME, Context.MODE_PRIVATE)
        val key = "cred|$general|portal.a.edu.cn"
        val legacy = Json.parseToJsonElement(preferences.getString(key, null)!!).jsonObject.toMutableMap()
        legacy.remove("encryptedUsername")
        legacy["username"] = JsonPrimitive("fixture-student")
        preferences.edit().putString(key, JsonObject(legacy).toString()).commit()
        assertEquals(credential(), store().find("plugin-a", "portal.a.edu.cn"))
        assertFalse(preferences.all.toString().contains("fixture-student"))
    }

    @Test fun accountSwitchMarkerIsSharedAndNeverSaveCanBeReset() {
        val saved = store()
        assertTrue(saved.rememberAccount(general, "portal.a.edu.cn", "fixture-student"))
        assertFalse(saved.rememberAccount("plugin-a", "portal.a.edu.cn", "fixture-student"))
        assertTrue(saved.rememberAccount("plugin-a", "portal.a.edu.cn", "different-student"))
        saved.setNeverSave("plugin-a", "portal.a.edu.cn")
        assertTrue(saved.isNeverSave(general, "portal.a.edu.cn"))
        saved.save(general, credential())
        assertFalse(saved.isNeverSave("plugin-a", "portal.a.edu.cn"))
    }
}
