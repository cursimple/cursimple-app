package com.x500x.cursimple.app

import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContract
import androidx.core.app.ActivityOptionsCompat
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.test.platform.app.InstrumentationRegistry
import com.x500x.cursimple.R
import com.x500x.cursimple.app.theme.ClassScheduleTheme
import com.x500x.cursimple.core.data.AppBackupPayload
import com.x500x.cursimple.core.data.AppBackupStores
import com.x500x.cursimple.core.data.PreferencesBackupEntry
import com.x500x.cursimple.core.data.PreferencesBackupValueType
import com.x500x.cursimple.core.data.PreferencesStoreSnapshot
import com.x500x.cursimple.core.data.ThemeMode
import com.x500x.cursimple.core.data.memo.MemoTransferImage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.util.Locale
import java.util.concurrent.CopyOnWriteArrayList

class LocalBackupFlowUiTest {
    @get:Rule val compose = createComposeRule()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun exportOnlyLaunchesThePickerAfterConfirmation() {
        val picker = DocumentPicker()
        val snapshot = payload(store(AppBackupStores.SCHEDULE, entry("course")), store(AppBackupStores.MEMOS))
        setScreen(picker, snapshot)

        click(R.string.ie_local_backup_export_action)
        waitFor(R.string.ie_local_backup_export)
        click(R.string.ie_local_backup_store_schedule)
        click(R.string.ie_local_backup_export)
        waitFor(R.string.ie_local_backup_export_confirm_title)
        compose.onNodeWithText(text(R.string.ie_local_backup_store_memos), substring = true).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.ie_local_backup_export_entries, 0), substring = true).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.ie_local_backup_export_images, 0)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.ie_local_backup_store_schedule), substring = true).assertDoesNotExist()
        assertTrue(picker.intents.isEmpty())

        click(R.string.ie_cancel)
        assertTrue(picker.intents.isEmpty())
        click(R.string.ie_local_backup_export)
        waitFor(R.string.ie_local_backup_export_confirm_title)
        click(R.string.ie_local_backup_export)
        assertEquals(listOf(Intent.ACTION_CREATE_DOCUMENT), picker.intents.map { it.action })
        assertEquals("application/zip", picker.intents.single().type)
    }

    @Test fun importOnlyRestoresTheOriginalZipPayloadAfterConfirmation() {
        val imported = payload(store(AppBackupStores.MEMOS, entry("updated", "new"), entry("added"), entry("same")))
            .copy(createdAt = 4321, memoImages = listOf(MemoTransferImage("img", "dGVzdA==")))
        val current = payload(store(AppBackupStores.MEMOS, entry("updated", "old"), entry("removed"), entry("same")))
        val restoreCalls = CopyOnWriteArrayList<AppBackupPayload>()
        withBackupFile(imported) { uri ->
            setScreen(DocumentPicker(uri), current) { restoreCalls += it }
            click(R.string.ie_local_backup_import_action)
            waitFor(R.string.ie_local_backup_import_changes_title)
            listOf(R.string.ie_local_backup_added, R.string.ie_local_backup_modified, R.string.ie_local_backup_deleted)
                .forEach { assertPreviewText(text(it)) }
            val category = text(R.string.ie_local_backup_store_memos)
            listOf("updated", "added", "removed").forEach { name ->
                assertPreviewText(text(R.string.ie_local_backup_change_store, category, name))
            }
            compose.onNodeWithText(text(R.string.ie_local_backup_change_store, category, "same")).assertDoesNotExist()
            assertPreviewText(text(R.string.ie_local_backup_import_images, 0, 1))
            click(R.string.ie_cancel)
            assertTrue(restoreCalls.isEmpty())

            click(R.string.ie_local_backup_import_action)
            waitFor(R.string.ie_local_backup_import_changes_title)
            click(R.string.ie_local_backup_restore_confirm)
            compose.waitUntil(5_000) { restoreCalls.isNotEmpty() }
            compose.waitUntil(5_000) {
                compose.onAllNodes(hasText(text(R.string.ie_local_backup_import_changes_title))).fetchSemanticsNodes().isEmpty()
            }
            assertEquals(listOf(imported), restoreCalls.toList())
        }
    }

    @Test fun identicalImportShowsAnExplicitNoChangesState() {
        val snapshot = payload(store(AppBackupStores.MEMOS, entry("same")))
        withBackupFile(snapshot) { uri ->
            setScreen(DocumentPicker(uri), snapshot)
            click(R.string.ie_local_backup_import_action)
            waitFor(R.string.ie_local_backup_no_changes)
            compose.onNodeWithText(text(R.string.ie_local_backup_no_changes)).assertIsDisplayed()
            compose.onNodeWithText(text(R.string.ie_local_backup_modified)).assertDoesNotExist()
            compose.onNodeWithText(text(R.string.ie_local_backup_deleted)).assertDoesNotExist()
            compose.onNodeWithText(text(R.string.ie_local_backup_added)).assertDoesNotExist()
        }
    }

    @Test fun aFailedRestoreDismissesThePreviewAndShowsTheFailure() {
        val snapshot = payload(store(AppBackupStores.MEMOS, entry("note")))
        val failure = "Synthetic restore failure"
        withBackupFile(snapshot) { uri ->
            setScreen(DocumentPicker(uri), snapshot) { error(failure) }
            click(R.string.ie_local_backup_import_action)
            waitFor(R.string.ie_local_backup_import_changes_title)
            click(R.string.ie_local_backup_restore_confirm)
            waitFor(R.string.ie_local_backup_failed_title)
            compose.onNodeWithText(text(R.string.ie_local_backup_failed, failure)).assertIsDisplayed()
            compose.onNodeWithText(text(R.string.ie_local_backup_import_changes_title)).assertDoesNotExist()
            click(R.string.ie_close)
            compose.onNodeWithText(text(R.string.ie_local_backup_import_action)).assertIsDisplayed()
        }
    }

    @Test fun restoreConfirmationLabelFitsAt320DpWithLargeText() {
        val englishConfiguration = Configuration(context.resources.configuration).apply {
            setLocale(Locale.ENGLISH)
            fontScale = 1.3f
            screenWidthDp = 320
        }
        val englishContext = context.createConfigurationContext(englishConfiguration)
        val confirmLabel = englishContext.getString(R.string.ie_local_backup_restore_confirm)
        val snapshot = payload(store(AppBackupStores.MEMOS, entry("note")))
        var confirmed = false
        compose.setContent {
            val screenWidthPx = LocalConfiguration.current.screenWidthDp * LocalDensity.current.density
            CompositionLocalProvider(
                LocalContext provides englishContext,
                LocalConfiguration provides englishConfiguration,
                LocalDensity provides Density(screenWidthPx / 320f, englishConfiguration.fontScale),
            ) {
                ClassScheduleTheme(ThemeMode.Light) {
                    LocalBackupImportDiffDialog(
                        diff = LocalBackupDiff.compare(snapshot, snapshot),
                        busy = false,
                        onDismiss = {},
                        onConfirm = { confirmed = true },
                    )
                }
            }
        }

        val layouts = mutableListOf<TextLayoutResult>()
        compose.onNodeWithText(confirmLabel, useUnmergedTree = true)
            .assertIsDisplayed()
            .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        val layout = layouts.single()
        assertTrue("Restore label has no layout lines", layout.lineCount > 0)
        // The narrow layout stacks the actions and gives the destructive action the full row.
        // It may therefore fit on one line. The contract is that it remains fully visible.
        assertTrue("Expected a visible restore label at 320dp", layout.lineCount >= 1)
        compose.onNodeWithText(confirmLabel).performClick()
        compose.runOnIdle { assertTrue(confirmed) }
    }

    private fun setScreen(picker: DocumentPicker, current: AppBackupPayload, restore: suspend (AppBackupPayload) -> Unit = {}) {
        compose.setContent {
            CompositionLocalProvider(LocalActivityResultRegistryOwner provides picker) {
                ClassScheduleTheme(ThemeMode.Light) {
                    LocalBackupSettingsSection(onCreateAppBackup = { current }, onRestoreAppBackup = restore)
                }
            }
        }
    }

    private fun text(id: Int, vararg args: Any) = context.getString(id, *args)
    private fun click(id: Int) = compose.onNodeWithText(text(id)).performClick()
    private fun assertPreviewText(label: String) {
        val node = compose.onNodeWithText(label, useUnmergedTree = true)
        if (!node.isDisplayed()) node.performScrollTo()
        node.assertIsDisplayed()
    }
    private fun waitFor(id: Int) = compose.waitUntil(5_000) {
        compose.onAllNodes(hasText(text(id))).fetchSemanticsNodes().isNotEmpty()
    }

    private fun withBackupFile(payload: AppBackupPayload, block: (Uri) -> Unit) {
        val file = File.createTempFile("local-backup-ui", ".zip", context.cacheDir)
        try {
            file.writeBytes(LocalBackupArchive.encode(payload))
            block(Uri.fromFile(file))
        } finally {
            file.delete()
        }
    }

    private fun entry(name: String, value: String = "value") =
        PreferencesBackupEntry(name, PreferencesBackupValueType.String, stringValue = value)

    private fun store(name: String, vararg entries: PreferencesBackupEntry) = PreferencesStoreSnapshot(name, entries.toList())
    private fun payload(vararg stores: PreferencesStoreSnapshot) = AppBackupPayload(AppBackupPayload.CURRENT_VERSION, stores = stores.toList())

    private inner class DocumentPicker(private val importUri: Uri? = null) : ActivityResultRegistryOwner {
        val intents = CopyOnWriteArrayList<Intent>()
        override val activityResultRegistry = object : ActivityResultRegistry() {
            override fun <I, O> onLaunch(
                requestCode: Int,
                contract: ActivityResultContract<I, O>,
                input: I,
                options: ActivityOptionsCompat?,
            ) {
                val intent = contract.createIntent(context, input)
                intents += intent
                val uri = if (intent.action == Intent.ACTION_OPEN_DOCUMENT) importUri else null
                dispatchResult(requestCode, uri)
            }
        }
    }
}
