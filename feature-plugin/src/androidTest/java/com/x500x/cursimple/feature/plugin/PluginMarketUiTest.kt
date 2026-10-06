package com.x500x.cursimple.feature.plugin

import android.text.format.Formatter
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.text.TextLayoutResult
import androidx.test.espresso.Espresso
import androidx.test.platform.app.InstrumentationRegistry
import com.x500x.cursimple.core.plugin.install.InstalledPluginRecord
import com.x500x.cursimple.core.plugin.install.PluginInstallSource
import com.x500x.cursimple.core.plugin.install.PluginInstallPreview
import com.x500x.cursimple.core.plugin.manifest.PluginManifest
import com.x500x.cursimple.core.plugin.market.github.DefaultMarketSources
import com.x500x.cursimple.core.plugin.market.github.GitHubReleaseAsset
import com.x500x.cursimple.core.plugin.market.github.GitHubRepoSummary
import com.x500x.cursimple.core.plugin.market.github.MarketSourceCheck
import com.x500x.cursimple.core.plugin.market.github.MarketSourceKind
import com.x500x.cursimple.feature.plugin.extension.ExtensionHostActions
import com.x500x.cursimple.feature.plugin.extension.ExtensionSyncOutcome
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class PluginMarketUiTest {
    @get:Rule val compose = createComposeRule()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun updateSettingsAndDotsWorkForBothKindsAndClearAfterUpgrade() {
        val plugin = repo("qa/plugin").copy(latestRelease = release().copy(tagName = "v2.0.0"))
        val component = repo("qa/component", extension = true).copy(latestRelease = release().copy(tagName = "v2.0.0"))
        val state = mutableStateOf(PluginMarketUiState(marketRepos = listOf(plugin), componentRepos = listOf(component),
            allMarketRepos = listOf(plugin, component), installedPlugins = listOf(record(plugin), record(component))))
        var checks = 0
        show(state = { state.value }, onUpdateOptions = { auto, badge, hours ->
            state.value = state.value.copy(autoCheckUpdates = auto, showUpdateBadge = badge, updateIntervalHours = hours)
        }, onCheckUpdates = { checks++ })
        compose.onAllNodesWithTag("plugin-update-dot", useUnmergedTree = true).assertCountEquals(2)
        compose.onNodeWithTag("plugin-update-settings").performClick()
        compose.onNodeWithTag("plugin-auto-check-switch").assertIsOn().performClick().assertIsOff()
        compose.onNodeWithTag("plugin-update-badge-switch").assertIsOn().performClick().assertIsOff()
        compose.onAllNodesWithTag("plugin-update-dot", useUnmergedTree = true).assertCountEquals(0)
        compose.onNodeWithTag("plugin-update-badge-switch").performClick().assertIsOn()
        compose.onNodeWithText(context.getString(R.string.plugin_update_interval_hours, 12)).performClick().assertIsSelected()
        compose.onNodeWithTag("plugin-check-updates").performClick()
        compose.runOnIdle {
            assertEquals(1, checks)
            assertEquals(false, state.value.autoCheckUpdates)
            assertEquals(12, state.value.updateIntervalHours)
        }
        compose.onNodeWithText(context.getString(R.string.plugin_update_settings_title)).assertIsDisplayed()
        val image = checkNotNull(InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot())
        java.io.File(context.filesDir, "plugin-update-settings.png").outputStream().use { image.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        Espresso.pressBack()
        compose.runOnIdle { state.value = state.value.copy(installedPlugins = listOf(record(plugin, "2.0.0"), record(component, "2.0.0"))) }
        compose.onAllNodesWithTag("plugin-update-dot", useUnmergedTree = true).assertCountEquals(0)
    }

    @Test
    fun marketSearchIsInlineAndSharedWithInstalledTab() {
        val repo = repo("school/importer").copy(schoolAliases = listOf("示例理工大学"))
        show(PluginMarketUiState(marketRepos = listOf(repo), installedPlugins = listOf(record(repo))))
        compose.onNodeWithTag("catalog-search").performTextInput("示例理工")
        compose.onNodeWithTag("catalog-list").performScrollToNode(hasTestTag("installed:school/importer:remote"))
        compose.onNodeWithTag("installed:school/importer:remote").assertExists()
        compose.onNodeWithTag("catalog-list").performScrollToNode(hasTestTag("catalog-market"))
        compose.onNodeWithTag("catalog-market").performClick()
        compose.onNodeWithTag("catalog-search").assertTextContains("示例理工")
        compose.onNodeWithTag("catalog-list").performScrollToNode(hasTestTag("repo:school/importer"))
        compose.onNodeWithTag("repo:school/importer").assertExists()
    }

    @Test
    fun fullMarketCanSearchBeyondTheOldSixItemPreview() {
        val repos = (0..8).map { repo("school/plugin-$it") }
        show(PluginMarketUiState(marketRepos = repos))
        compose.onNodeWithTag("catalog-market").performClick()
        compose.onNodeWithTag("catalog-search").performTextInput("plugin-8")
        compose.onNodeWithTag("catalog-list").performScrollToNode(hasTestTag("install:school/plugin-8"))
        compose.onNodeWithTag("install:school/plugin-8").assertIsEnabled()
        compose.onNodeWithTag("repo:school/plugin-0").assertDoesNotExist()
    }

    @Test
    fun marketInstallAndUpdateCallInstallerDirectlyAndInstalledVersionIsDisabled() {
        val repo = repo("school/importer")
        val state = mutableStateOf(PluginMarketUiState(marketRepos = listOf(repo)))
        var installs = 0
        show(state = { state.value }, onInstall = { installs++ })
        compose.onNodeWithTag("catalog-market").performClick()
        compose.onNodeWithTag("catalog-list").performScrollToNode(hasTestTag("install:${repo.fullName}"))
        compose.onNodeWithTag("install:${repo.fullName}").performClick()
        compose.runOnIdle { assertEquals(1, installs); state.value = state.value.copy(installedPlugins = listOf(record(repo, "0.9.0"))) }
        compose.onNodeWithTag("install:${repo.fullName}").performClick()
        compose.runOnIdle { assertEquals(2, installs); state.value = state.value.copy(installedPlugins = listOf(record(repo))) }
        compose.onNodeWithTag("install:${repo.fullName}").assertIsNotEnabled()
    }

    @Test
    fun componentCatalogUsesComponentReposAndKeepsItsSearchWhenSwitchingPages() {
        val school = repo("school/importer")
        val component = repo("vendor/notices", extension = true)
        show(PluginMarketUiState(marketRepos = listOf(school), componentRepos = listOf(component)))
        compose.onNodeWithTag("catalog-search").performTextInput("school")
        compose.onNodeWithTag("platform-extensions").performClick()
        compose.onNodeWithTag("catalog-market").performClick()
        compose.onNodeWithTag("catalog-search").performTextInput("notices")
        compose.onNodeWithTag("catalog-list").performScrollToNode(hasTestTag("repo:vendor/notices"))
        compose.onNodeWithTag("repo:vendor/notices").assertExists()
        compose.onNodeWithTag("repo:school/importer").assertDoesNotExist()
        compose.onNodeWithTag("platform-plugins").performClick()
        compose.onNodeWithTag("catalog-search").assertTextContains("school")
        compose.onNodeWithTag("platform-extensions").performClick()
        compose.onNodeWithTag("catalog-market").assertIsSelected()
        compose.onNodeWithTag("catalog-list").performScrollToNode(hasTestTag("catalog-search"))
        compose.onNodeWithTag("catalog-search").assertTextContains("notices")
    }

    @Test
    fun componentUpdateDoesNotTriggerSchoolImport() {
        val component = repo("vendor/notices", extension = true)
        var installs = 0
        var schoolImports = 0
        show(
            PluginMarketUiState(componentRepos = listOf(component), installedPlugins = listOf(record(component, "0.9.0"))),
            initialTab = PluginPlatformTab.Extensions,
            onInstall = { installs++ },
            onSchoolImport = { schoolImports++ },
        )
        val updateLabel = context.getString(R.string.plugin_repo_action_update, "v1.0.0")
        compose.onNodeWithTag("catalog-list").performScrollToNode(androidx.compose.ui.test.hasText(updateLabel))
        compose.onNodeWithText(updateLabel).performClick()
        compose.runOnIdle { assertEquals(1, installs); assertEquals(0, schoolImports) }
    }

    @Test
    fun failedPrivateSourceDoesNotHideSuccessfulPublicResultsAndCanRetry() {
        val repo = repo("school/importer")
        var retries = 0
        show(
            PluginMarketUiState(
                marketRepos = listOf(repo),
                sourceErrors = listOf(PluginMarketSourceError("team/private", MarketSourceKind.Plugin, IllegalStateException("unavailable"), MarketSourceCheck.NotFoundOrPrivate)),
            ),
            onRefresh = { retries++ },
        )
        compose.onNodeWithTag("catalog-market").performClick()
        compose.onNodeWithTag("catalog-list").performScrollToNode(hasTestTag("source-error:team/private"))
        compose.onNodeWithText(context.getString(R.string.plugin_catalog_source_retry)).performClick()
        compose.runOnIdle { assertEquals(1, retries) }
        compose.onNodeWithTag("catalog-list").performScrollToNode(hasTestTag("repo:${repo.fullName}"))
        compose.onNodeWithTag("repo:${repo.fullName}").assertExists()
    }

    @Test
    fun missingReleaseAndBusyInstallerDisableInstallAndSearchCanBeCleared() {
        val repo = repo("school/importer").copy(latestRelease = null)
        val state = mutableStateOf(PluginMarketUiState(marketRepos = listOf(repo)))
        show(state = { state.value })
        compose.onNodeWithTag("catalog-market").performClick()
        compose.onNodeWithTag("catalog-search").performTextInput("absent-school")
        compose.onNodeWithTag("catalog-clear").performClick()
        compose.onNodeWithTag("catalog-list").performScrollToNode(hasTestTag("install:${repo.fullName}"))
        compose.onNodeWithTag("install:${repo.fullName}").assertIsNotEnabled()
        compose.runOnIdle { state.value = state.value.copy(marketRepos = listOf(repo.copy(latestRelease = release())), isLoading = true) }
        compose.onNodeWithTag("install:${repo.fullName}").assertIsNotEnabled()
    }

    @Test
    fun marketDetailsHideAllTabsAndBothBackActionsRestoreSearch() {
        val repo = repo("cursimple/example_school_plugin").copy(schoolAliases = listOf("示例大学"))
        show(PluginMarketUiState(marketRepos = listOf(repo)))
        compose.onNodeWithTag("catalog-market").performClick()
        compose.onNodeWithTag("catalog-search").performTextInput("EXAMPLE_SCHOOL")
        compose.onNodeWithTag("catalog-list").performScrollToNode(androidx.compose.ui.test.hasText(context.getString(R.string.plugin_catalog_details)))
        compose.onNodeWithText(context.getString(R.string.plugin_catalog_details)).performClick()
        assertNoCatalogTabs()
        compose.onNodeWithTag("detail-name").assertTextContains("示例大学")
        compose.onNodeWithTag("detail-list").performScrollToNode(hasTestTag("detail-repository-name"))
        compose.onNodeWithTag("detail-repository-name").performSemanticsAction(SemanticsActions.GetTextLayoutResult) { action ->
            val layouts = mutableListOf<TextLayoutResult>()
            check(action(layouts))
            assertEquals(1, layouts.single().lineCount)
        }
        compose.onNodeWithTag("detail-back").performClick()
        compose.onNodeWithTag("catalog-market").assertIsSelected()
        compose.onNodeWithTag("catalog-search").assertTextContains("EXAMPLE_SCHOOL")
        compose.onNodeWithTag("catalog-list").performScrollToNode(androidx.compose.ui.test.hasText(context.getString(R.string.plugin_catalog_details)))
        compose.onNodeWithText(context.getString(R.string.plugin_catalog_details)).performClick()
        Espresso.pressBack()
        compose.onNodeWithTag("catalog-market").assertIsSelected()
        compose.onNodeWithTag("catalog-search").assertTextContains("EXAMPLE_SCHOOL")
    }

    @Test
    fun installedCardsUninstallDirectlyWithConfirmForBothPluginsAndComponents() {
        val plugin = repo("school/importer")
        val component = repo("tools/notify", extension = true)
        val removed = mutableListOf<String>()
        val installedPlugin = record(plugin)
        val installedComponent = record(component)
        show(
            PluginMarketUiState(allMarketRepos = listOf(plugin, component), installedPlugins = listOf(installedPlugin, installedComponent)),
            onRemove = { removed += it },
        )
        val confirm = context.getString(R.string.plugin_remove_dialog_confirm)
        compose.onNodeWithTag("remove:${installedPlugin.installKey}").performScrollTo()
        java.io.File(context.filesDir, "plugin-card-remove.png").outputStream().use {
            compose.onNodeWithTag("installed:${installedPlugin.installKey}").captureToImage().asAndroidBitmap()
                .compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
        }
        // Remove directly from installed cards with confirmation; cancellation leaves records intact.
        compose.onNodeWithTag("remove:${installedPlugin.installKey}").performScrollTo().performClick()
        compose.onNodeWithText(confirm).assertExists()
        compose.onNodeWithText(context.getString(R.string.plugin_action_cancel)).performClick()
        compose.runOnIdle { assertEquals(emptyList<String>(), removed) }
        compose.onNodeWithTag("remove:${installedPlugin.installKey}").performClick()
        compose.onNodeWithText(confirm).performClick()
        compose.runOnIdle { assertEquals(listOf(installedPlugin.installKey), removed) }
        assertNoDetailOpened()

        compose.onNodeWithTag("platform-extensions").performClick()
        compose.onNodeWithTag("remove:${installedComponent.installKey}").performScrollTo().performClick()
        compose.onNodeWithText(confirm).performClick()
        compose.runOnIdle { assertEquals(listOf(installedPlugin.installKey, installedComponent.installKey), removed) }
        assertNoDetailOpened()
    }

    private fun assertNoDetailOpened() {
        compose.onNodeWithTag("installed-detail").assertDoesNotExist()
        compose.onNodeWithTag("catalog-installed").assertExists()
    }

    @Test
    fun installedDetailsHideTabsAndRestoreInstalledCatalog() {
        val repo = repo("school/importer")
        show(PluginMarketUiState(marketRepos = listOf(repo), installedPlugins = listOf(record(repo))))
        compose.onNodeWithTag("catalog-list").performScrollToNode(androidx.compose.ui.test.hasText(context.getString(R.string.plugin_catalog_details)))
        compose.onNodeWithText(context.getString(R.string.plugin_catalog_details)).performClick()
        assertNoCatalogTabs()
        compose.onNodeWithTag("installed-detail").assertExists()
        compose.onNodeWithTag("detail-back").performClick()
        compose.onNodeWithTag("catalog-installed").assertIsSelected()
        compose.onNodeWithTag("platform-plugins").assertExists()
    }

    @Test
    fun componentSettingsDeepLinkHidesTabsAndBackRestoresComponents() {
        val component = repo("qa/component-settings", extension = true)
        val installed = record(component)
        var consumed = 0
        val actions = object : ExtensionHostActions {
            override suspend fun loadPackage(record: InstalledPluginRecord) = manifest(extension = true) to ""
            override suspend fun loadUi(record: InstalledPluginRecord): String? = null
            override suspend fun syncNow(record: InstalledPluginRecord) = ExtensionSyncOutcome.Skipped(record.pluginId, "fixture")
            override fun onDataChanged(pluginId: String) {}
            override fun openFeed(pluginId: String) {}
        }
        show(
            PluginMarketUiState(componentRepos = listOf(component), installedPlugins = listOf(installed)),
            initialTab = PluginPlatformTab.Extensions,
            extensionActions = actions,
            openExtensionPluginId = installed.installKey,
            onExtensionOpenConsumed = { consumed++ },
        )
        compose.waitForIdle()
        assertNoCatalogTabs()
        compose.runOnIdle { assertEquals(1, consumed) }
        compose.onNodeWithContentDescription(context.getString(R.string.plugin_action_back)).performClick()
        compose.onNodeWithTag("platform-extensions").assertIsSelected()
        compose.onNodeWithTag("catalog-installed").assertIsSelected()
    }

    @Test
    fun installedComponentHasDetailsWithSizeAndInstallTimeAndSettingsStayReachable() {
        val component = repo("qa/component-details", extension = true)
        val installed = record(component).copy(storagePath = context.cacheDir.resolve("qa-component-details").apply {
            mkdirs(); resolve("main.js").writeBytes(ByteArray(2048))
        }.path, installedAt = "2026-10-04T12:30:00+08:00")
        val actions = object : ExtensionHostActions {
            override suspend fun loadPackage(record: InstalledPluginRecord) = manifest(extension = true) to ""
            override suspend fun loadUi(record: InstalledPluginRecord): String? = null
            override suspend fun syncNow(record: InstalledPluginRecord) = ExtensionSyncOutcome.Skipped(record.pluginId, "fixture")
            override fun onDataChanged(pluginId: String) {}
            override fun openFeed(pluginId: String) {}
        }
        show(
            PluginMarketUiState(componentRepos = listOf(component), installedPlugins = listOf(installed)),
            initialTab = PluginPlatformTab.Extensions,
            extensionActions = actions,
        )
        compose.onNodeWithTag("details:${installed.installKey}").performScrollTo().performClick()
        compose.onNodeWithTag("installed-detail").assertExists()
        compose.onNodeWithTag("detail-install-info").assertExists()
        compose.waitUntil(5_000) {
            compose.onAllNodesWithText(context.getString(R.string.plugin_detail_size_measuring)).fetchSemanticsNodes().isEmpty()
        }
        listOf(
            R.string.plugin_detail_field_installed_at, R.string.plugin_detail_field_package_size,
            R.string.plugin_detail_field_data_size, R.string.plugin_detail_field_total_size,
        ).forEach { compose.onNodeWithText(context.getString(it)).assertExists() }
        compose.onNodeWithTag("detail-install-info").captureToImage().asAndroidBitmap().let { bitmap ->
            java.io.File(context.filesDir, "plugin-detail-install-info.png").outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        }

        compose.onNodeWithTag("detail-open-settings").performScrollTo().performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("installed-detail").assertDoesNotExist()
        compose.onNodeWithContentDescription(context.getString(R.string.plugin_action_back)).performClick()
        compose.onNodeWithTag("installed-detail").assertExists()
        compose.onNodeWithTag("detail-back").performClick()
        compose.onNodeWithTag("catalog-installed").assertIsSelected()
    }

    @Test
    fun marketBlocksInstallWhileVersionsAreBeingCheckedAndOnlyShowsFreshOnes() {
        val bare = repo("school/importer").copy(latestRelease = null)
        val state = mutableStateOf(PluginMarketUiState(marketRepos = listOf(bare), allMarketRepos = listOf(bare), versionsChecking = true))
        var installs = 0
        show(state = { state.value }, onInstall = { installs++ })
        compose.onNodeWithTag("catalog-market").performClick()
        // Hide stale versions and disable installation until current metadata is available.
        compose.onNodeWithTag("install:school/importer").assertIsNotEnabled()
        compose.onNodeWithText(context.getString(R.string.plugin_catalog_loading_version)).assertExists()
        compose.runOnIdle { assertEquals(0, installs) }

        val fresh = bare.copy(latestRelease = release().copy(tagName = "v1.3.0"))
        compose.runOnIdle { state.value = state.value.copy(marketRepos = listOf(fresh), allMarketRepos = listOf(fresh), versionsChecking = false) }
        compose.onNodeWithTag("install:school/importer").assertIsEnabled().performClick()
        compose.runOnIdle { assertEquals(1, installs) }
    }

    @Test
    fun installPreviewShowsSummaryAndActualSizeWithTechnicalDetailsCollapsed() {
        val preview = PluginInstallPreview(manifest(extension = true), checksumVerified = true, source = PluginInstallSource.Remote)
        val origin = PluginInstallOrigin(
            repoSlug = "vendor/notices", downloadUrl = "https://example.com/release.zip",
            registrySource = DefaultMarketSources.COMPONENT_REGISTRY, displayName = "catalog name",
            description = "测试仓库简介", sizeBytes = 8L * 1024 * 1024,
        )
        show(PluginMarketUiState(installPreview = preview, installPreviewOrigin = origin, packageSizeBytes = 2L * 1024 * 1024))
        compose.onNodeWithTag("preview-name").assertTextContains("测试组件")
        compose.onNodeWithTag("preview-description").assertTextContains("测试仓库简介")
        compose.onNodeWithTag("preview-source").assertTextContains(context.getString(R.string.plugin_catalog_public_source))
        compose.onNodeWithTag("preview-size").assertTextContains(Formatter.formatShortFileSize(context, 2L * 1024 * 1024))
        compose.onNodeWithTag("preview-technical-content").assertDoesNotExist()
        compose.onNodeWithText("secret.example.com").assertDoesNotExist()
        compose.onNodeWithTag("preview-confirm").assertIsEnabled()
        compose.onNodeWithTag("preview-technical-toggle").performScrollTo()
        val fixedNodes = listOf("install-preview", "preview-scroll", "preview-confirm", "preview-cancel")
        val initialBounds = fixedNodes.associateWith { tag ->
            compose.onNodeWithTag(tag).fetchSemanticsNode().boundsInWindow
        }
        fun assertWindowAndActionsStayFixed() {
            fixedNodes.forEach { tag ->
                assertEquals(tag, initialBounds.getValue(tag), compose.onNodeWithTag(tag).fetchSemanticsNode().boundsInWindow)
            }
        }
        compose.onNodeWithTag("preview-technical-toggle").performScrollTo().performClick()
        assertWindowAndActionsStayFixed()
        compose.onNodeWithText("secret.example.com").performScrollTo().assertIsDisplayed()
        assertWindowAndActionsStayFixed()
        compose.onNodeWithTag("preview-technical-toggle").performScrollTo().performClick()
        compose.onNodeWithTag("preview-technical-content").assertDoesNotExist()
        assertWindowAndActionsStayFixed()
    }

    @Test
    fun blockedPreviewShowsReasonWithoutExpandingAndDisablesInstall() {
        val preview = PluginInstallPreview(manifest(), checksumVerified = false, source = PluginInstallSource.Local)
        show(PluginMarketUiState(installPreview = preview, packageSizeBytes = 1024L))
        compose.onNodeWithTag("preview-technical-content").assertDoesNotExist()
        compose.onNodeWithTag("preview-block-reason").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("preview-confirm").assertIsNotEnabled()
    }

    @Test
    fun largeDownloadProgressIsVisibleFromDetailsAndCancelKeepsDetails() {
        val repo = repo("vendor/notices", extension = true)
        val state = mutableStateOf(PluginMarketUiState(componentRepos = listOf(repo)))
        var cancelled = 0
        show(
            state = { state.value }, initialTab = PluginPlatformTab.Extensions,
            onInstall = {
                state.value = state.value.copy(
                    isLoading = true, status = PluginMarketStatus.DownloadingAsset("component.zip", "v1.0.0"),
                    downloadProgress = PluginDownloadProgress(2L * 1024 * 1024, 10L * 1024 * 1024),
                )
            },
            onDismissInstallPreview = {
                cancelled++
                state.value = state.value.copy(isLoading = false, status = null, downloadProgress = null)
            },
        )
        compose.onNodeWithTag("catalog-market").performClick()
        compose.onNodeWithTag("catalog-list").performScrollToNode(androidx.compose.ui.test.hasText(context.getString(R.string.plugin_catalog_details)))
        compose.onNodeWithText(context.getString(R.string.plugin_catalog_details)).performClick()
        compose.onNodeWithTag("install:${repo.fullName}").performClick()
        compose.onNodeWithTag("market-download").assertExists()
        compose.onNodeWithTag("download-percent").assertTextContains("20%")
        compose.onNodeWithText(context.getString(R.string.extension_download_title)).assertExists()
        compose.onNodeWithTag("download-cancel").performClick()
        compose.runOnIdle { assertEquals(1, cancelled) }
        compose.onNodeWithTag("market-download").assertDoesNotExist()
        compose.onNodeWithTag("market-detail").assertExists()
        assertNoCatalogTabs()
    }

    @Test
    fun downloadOverlayUsesOnlyTheFiveMiBThresholdIncludingUnknownTotals() {
        val threshold = 5L * 1024 * 1024
        val state = mutableStateOf(PluginMarketUiState(
            isLoading = true, status = PluginMarketStatus.DownloadingAsset("package.zip", "v1"),
            downloadProgress = PluginDownloadProgress(1, threshold - 1),
        ))
        show(state = { state.value })
        compose.onNodeWithTag("market-download").assertDoesNotExist()
        compose.runOnIdle { state.value = state.value.copy(downloadProgress = PluginDownloadProgress(threshold - 1, null)) }
        compose.onNodeWithTag("market-download").assertDoesNotExist()
        compose.runOnIdle { state.value = state.value.copy(downloadProgress = PluginDownloadProgress(threshold, null)) }
        compose.onNodeWithTag("market-download").assertExists()
        compose.onNodeWithTag("download-percent").assertDoesNotExist()
        compose.runOnIdle { state.value = state.value.copy(downloadProgress = PluginDownloadProgress(1, threshold)) }
        compose.onNodeWithTag("market-download").assertExists()
    }

    private fun assertNoCatalogTabs() {
        listOf("platform-plugins", "platform-extensions", "catalog-installed", "catalog-market").forEach {
            compose.onNodeWithTag(it).assertDoesNotExist()
        }
    }

    private fun show(
        uiState: PluginMarketUiState = PluginMarketUiState(),
        state: () -> PluginMarketUiState = { uiState },
        initialTab: PluginPlatformTab = PluginPlatformTab.Plugins,
        onInstall: (GitHubRepoSummary) -> Unit = {},
        onRefresh: () -> Unit = {},
        onSchoolImport: () -> Unit = {},
        extensionActions: ExtensionHostActions? = null,
        openExtensionPluginId: String? = null,
        onExtensionOpenConsumed: () -> Unit = {},
        onDismissInstallPreview: () -> Unit = {},
        onUpdateOptions: (Boolean, Boolean, Int) -> Unit = { _, _, _ -> },
        onCheckUpdates: () -> Unit = {},
        onRemove: (String) -> Unit = {},
    ) {
        val tab = mutableStateOf(initialTab)
        compose.setContent {
            MaterialTheme {
                PluginMarketScreen(
                    uiState = state(), selectedTab = tab.value,
                    enabledPluginIds = state().installedPlugins.map { it.installKey }.toSet(),
                    syncingPluginId = null, missingComponents = emptyList(), pendingWebSession = null,
                    pluginSources = listOf(DefaultMarketSources.PLUGIN_REGISTRY),
                    componentSources = listOf(DefaultMarketSources.COMPONENT_REGISTRY),
                    syncStatusMessage = null, onSelectTab = { tab.value = it }, onPickLocalPlugin = {},
                    onRefreshMarket = onRefresh, onOpenRepo = {}, onInstallFromGitHub = onInstall,
                    onConfirmInstall = {}, onDismissInstallPreview = onDismissInstallPreview, onRemovePlugin = onRemove,
                    onSetPluginEnabled = { _, _ -> }, onSyncPlugin = { onSchoolImport() },
                    onUpgradePlugin = { _, _ -> onSchoolImport() }, onCompleteWebSession = {}, onCancelWebSession = {},
                    extensionActions = extensionActions, openExtensionPluginId = openExtensionPluginId, onExtensionOpenConsumed = onExtensionOpenConsumed,
                    onUpdateOptions = onUpdateOptions, onCheckUpdates = onCheckUpdates,
                )
            }
        }
    }

    private fun release() = GitHubReleaseAsset("v1.0.0", "plugin.zip", "https://example.com/plugin.zip", 1)

    private fun manifest(extension: Boolean = false) = PluginManifest(
        id = "qa.fixture", name = if (extension) "测试组件" else "测试插件", version = "1.0.0", versionCode = 1,
        entry = "index.js", apiVersion = 1, allowedHosts = listOf("secret.example.com"),
        kind = if (extension) PluginManifest.KIND_EXTENSION else PluginManifest.KIND_SCHEDULE,
    )

    private fun repo(slug: String, extension: Boolean = false) = GitHubRepoSummary(
        fullName = slug, owner = slug.substringBefore('/'), name = slug.substringAfter('/'),
        description = "Test entry", stars = 0, avatarUrl = "", htmlUrl = "https://example.com", ownerHtmlUrl = "",
        isFresh = true, latestRelease = release(), kind = if (extension) PluginManifest.KIND_EXTENSION else "",
        registrySource = if (extension) DefaultMarketSources.COMPONENT_REGISTRY else DefaultMarketSources.PLUGIN_REGISTRY,
    )

    private fun record(repo: GitHubRepoSummary, version: String = "1.0.0") = InstalledPluginRecord(
        pluginId = repo.fullName, name = repo.name, version = version, versionCode = 1,
        storagePath = "/tmp/test", installedAt = "2026-10-04", source = PluginInstallSource.Remote,
        sourceRepo = repo.fullName, registrySource = repo.registrySource,
        kind = if (repo.isExtension) PluginManifest.KIND_EXTENSION else PluginManifest.KIND_SCHEDULE,
    )
}
