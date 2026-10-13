package com.x500x.cursimple.feature.plugin

import android.view.View
import android.view.ViewGroup
import android.view.inspector.WindowInspector
import android.webkit.WebView
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import com.x500x.cursimple.core.plugin.install.*
import com.x500x.cursimple.core.plugin.manifest.*
import com.x500x.cursimple.feature.plugin.extension.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.zip.ZipInputStream

/** Uses the actual Rain login document and the native bridge with an already verified account. */
class RainSavedLoginNavigationTest {
    @get:Rule val compose = createComposeRule()
    private val inst get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = inst.targetContext

    @androidx.test.filters.SdkSuppress(minSdkVersion = 29)
    @Test fun savedAccountSkipsRootLoginAndRemovesItsDocument() = runBlocking {
        assumeTrue(InstrumentationRegistry.getArguments().getString("rainLoginNavigationQa") == "true")
        inst.runOnMainSync { android.graphics.HardwareRenderer.setDrawingEnabled(true) }
        val directory = File(context.cacheDir, "rain-login-navigation-qa").apply { deleteRecursively(); mkdirs() }
        ZipInputStream(File(context.filesDir, "qa-rain-component.zip").inputStream()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                val file = File(directory, entry.name).canonicalFile
                require(file.path.startsWith(directory.canonicalPath + "/"))
                if (!entry.isDirectory) { file.parentFile!!.mkdirs(); file.outputStream().use { zip.copyTo(it) } }
            }
        }
        val manifest = Json { ignoreUnknownKeys = true }.decodeFromString<PluginManifest>(File(directory, "manifest.json").readText())
            .copy(id = "qa-rain-saved-login")
        val record = InstalledPluginRecord(manifest.id, manifest.name, version = manifest.version, versionCode = manifest.versionCode,
            storagePath = directory.path, installedAt = "login-navigation-qa", source = PluginInstallSource.Local)
        val store = ExtensionStore.get(context)
        val account = ExtensionAccount("saved-student", "Saved student")
        store.update(record.pluginId) { ExtensionData(record.pluginId, loginState = ExtensionLoginState.LoggedIn, account = account) }
        var feedLoads = 0
        val actions = object : ExtensionHostActions {
            override suspend fun loadPackage(record: InstalledPluginRecord): Pair<PluginManifest, String> = error("Login must not be repeated")
            override suspend fun loadUi(record: InstalledPluginRecord) = """<html><head></head><body><p id="saved-feed">Saved classroom</p></body></html>"""
            override suspend fun loadUi(record: InstalledPluginRecord, page: PluginExtensionUiPage): String? {
                assertEquals(PluginExtensionUiPage.Feed, page); feedLoads++
                if (feedLoads == 1) error("QA classroom load failed")
                return loadUi(record)
            }
            override suspend fun syncNow(record: InstalledPluginRecord): ExtensionSyncOutcome = error("No authentication or sync needed")
            override fun onDataChanged(pluginId: String) {}
            override fun openFeed(pluginId: String) { error("Navigation must stay inside the component") }
        }
        try {
            val login = File(directory, "ui/login.html").readText()
            // Read the store before composition; `.value` inside setContent is not observable.
            val initialData = store.all.value.getValue(record.pluginId)
            compose.setContent {
                val data by store.flow(record.pluginId).collectAsState(initial = initialData)
                MaterialTheme { ExtensionOwnedPage(record, manifest, data, login, PluginExtensionUiPage.Login, actions, {}, {}) }
            }
            compose.waitUntil(15_000) { feedLoads == 1 }
            compose.onNodeWithText("QA classroom load failed").assertIsDisplayed()
            assertEquals("true", js("!!document.querySelector('#open-feed') && !document.querySelector('#qr-area')"))
            compose.waitForIdle(); Thread.sleep(300)
            val image = checkNotNull(inst.uiAutomation.takeScreenshot())
            File(context.filesDir, "rain-saved-navigation-retry.png").outputStream().use {
                image.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
            }
            image.recycle()
            compose.onNodeWithText("重试").performClick()
            compose.waitUntil(15_000) { runCatching { js("!!document.querySelector('#saved-feed')") == "true" }.getOrDefault(false) }
            assertEquals(2, feedLoads)
            assertEquals("true", js("CurSimpleComponent.state.data.loginState === 'logged_in' && CurSimpleComponent.state.data.account.id === 'saved-student'"))
            assertEquals(1, views().size)
            assertEquals(account, store.get(record.pluginId).account)
            assertEquals("true", js("!document.querySelector('#qr-area') && !document.querySelector('#login-card')"))
        } finally { store.remove(record.pluginId) }
    }
    private fun views(): List<WebView> {
        val result = mutableListOf<WebView>()
        fun visit(view: View) { if (view is WebView) result.add(view) else if (view is ViewGroup) for (i in 0 until view.childCount) visit(view.getChildAt(i)) }
        inst.runOnMainSync { WindowInspector.getGlobalWindowViews().forEach(::visit) }
        return result
    }
    private fun js(code: String): String {
        val view = views().first { it.visibility == View.VISIBLE && it.importantForAccessibility == View.IMPORTANT_FOR_ACCESSIBILITY_AUTO }
        val done = CountDownLatch(1); var result = ""
        inst.runOnMainSync { view.evaluateJavascript(code) { result = it; done.countDown() } }
        check(done.await(3, TimeUnit.SECONDS)); return result
    }
}
