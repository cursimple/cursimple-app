package com.x500x.cursimple.feature.plugin

import android.content.res.Configuration
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.x500x.cursimple.feature.plugin.extension.BridgeError
import com.x500x.cursimple.feature.plugin.extension.ExtensionRunMessages
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Every run-failure message must resolve in each language with its placeholders filled. */
@RunWith(AndroidJUnit4::class)
class ExtensionRunMessagesLocaleTest {
    private fun messages(tag: String): ExtensionRunMessages {
        val base = InstrumentationRegistry.getInstrumentation().targetContext
        val configuration = Configuration(base.resources.configuration).apply { setLocale(Locale.forLanguageTag(tag)) }
        return ExtensionRunMessages.from(base.createConfigurationContext(configuration).resources)
    }

    private fun all(m: ExtensionRunMessages): List<String> = buildList {
        add(m.entryNotAllowed("https://school.example.edu/x"))
        add(m.cannotOpen("https://school.example.edu/x", "ERR_TIMED_OUT"))
        add(m.cannotOpen("https://school.example.edu/x", null))
        add(m.timeout(90))
        add(m.scriptError()); add(m.noResult()); add(m.tooLarge(2048)); add(m.notJson())
        BridgeError.values().forEach { add(m.bridge(it)) }
        addAll(m.scriptTemplates().values)
    }

    @Test fun everyMessageResolvesInEveryLanguageWithoutLeftoverPlaceholders() {
        listOf("zh-CN", "zh-TW", "en").forEach { tag ->
            all(messages(tag)).forEach {
                assertTrue("$tag: blank message", it.isNotBlank())
                assertFalse("$tag: unfilled placeholder in \"$it\"", Regex("%\\d?\\$?[sd]").containsMatchIn(it))
            }
        }
    }

    @Test fun placeholdersCarryTheirValuesAndTemplatesKeepTheirMarker() {
        listOf("zh-CN", "zh-TW", "en").forEach { tag ->
            val m = messages(tag)
            assertTrue(m.timeout(90).contains("90"))
            assertTrue(m.tooLarge(2048).contains("2048"))
            assertTrue(m.entryNotAllowed("https://school.example.edu/x").contains("school.example.edu"))
            assertTrue(m.cannotOpen("https://school.example.edu/x", "ERR_TIMED_OUT").contains("ERR_TIMED_OUT"))
            // The script fills {0} itself, so these templates must still contain it.
            listOf("permissionMissing", "accessDenied", "loadFailed", "functionMissing").forEach {
                assertTrue("$tag/$it lost its {0}", m.scriptTemplates().getValue(it).contains("{0}"))
            }
        }
    }

    @Test fun languagesDifferSoNothingFallsBackToTheDefaultByAccident() {
        val zh = messages("zh-CN").scriptError()
        assertTrue(messages("en").scriptError() != zh)
        assertTrue(messages("zh-TW").scriptError() != zh)
        assertEquals("The component script failed", messages("en").scriptError())
    }
}
