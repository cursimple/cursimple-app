package com.x500x.cursimple.feature.plugin

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.x500x.cursimple.feature.plugin.extension.*
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test

class ExtensionNoticeActionsTest {
    @get:Rule val compose = createComposeRule()
    @Test fun transactionNoticeHasIgnoreAndNoReadAction() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("extensionActionsQa") == "true")
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        var ignored = 0
        compose.setContent {
            MaterialTheme {
                FeedItemDetailSheet(ExtensionFeedItem("notice", "announcement", "Class notice", kind = "notice"),
                    "Component", FeedPalette(emptyList(), listOf(MaterialTheme.colorScheme.primary)),
                    0, {}, scheduleLabel = "2026-10-08 18:49", onIgnore = { ignored++ })
            }
        }
        compose.onNodeWithText(context.getString(R.string.extension_mark_read)).assertDoesNotExist()
        compose.onNodeWithText(context.getString(R.string.extension_ignore_item)).assertIsDisplayed().performClick()
        assertEquals(1, ignored)
    }
}
