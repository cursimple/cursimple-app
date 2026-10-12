package com.x500x.cursimple.feature.plugin

import android.view.View
import android.view.ViewGroup
import android.view.KeyEvent
import android.view.inspector.WindowInspector
import android.webkit.WebView
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.x500x.cursimple.core.plugin.install.*
import com.x500x.cursimple.core.plugin.manifest.*
import com.x500x.cursimple.feature.plugin.extension.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.zip.ZipInputStream

class ComponentToolsNavigationTest {
    @get:Rule val compose = createComposeRule()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private lateinit var web: WebView

    @androidx.test.filters.SdkSuppress(minSdkVersion = 29)
    @Test fun ownedPagesUnlockPersistAndHandleSystemBack() = runBlocking {
        assumeTrue(InstrumentationRegistry.getArguments().getString("componentToolsQa") == "true")
        val archive = File(context.filesDir, "qa-component.zip")
        assertTrue(archive.isFile)
        val directory = File(context.cacheDir, "component-tools-qa").apply { deleteRecursively(); mkdirs() }
        ZipInputStream(archive.inputStream()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                val target = File(directory, entry.name).canonicalFile
                require(target.path.startsWith(directory.canonicalPath + "/"))
                if (!entry.isDirectory) { target.parentFile?.mkdirs(); target.outputStream().use { zip.copyTo(it) } }
            }
        }
        val json = Json { ignoreUnknownKeys = true }
        val manifest = json.decodeFromString<PluginManifest>(File(directory, "manifest.json").readText())
        val record = InstalledPluginRecord(manifest.id, manifest.name, version = manifest.version,
            versionCode = manifest.versionCode, storagePath = directory.path, installedAt = "qa", source = PluginInstallSource.Local)
        val html = File(directory, manifest.extension!!.ui!!.entryFor(PluginExtensionUiPage.Feed)!!).readText()
        val store = ExtensionStore.get(context)
        store.remove(record.pluginId)
        var exited = false
        val actions = object : ExtensionHostActions {
            override suspend fun loadPackage(record: InstalledPluginRecord) = manifest to ""
            override suspend fun loadUi(record: InstalledPluginRecord) = html
            override suspend fun syncNow(record: InstalledPluginRecord): ExtensionSyncOutcome = error("QA offline")
            override fun onDataChanged(pluginId: String) {}
            override fun openFeed(pluginId: String) {}
            override suspend fun notificationCommand(record: InstalledPluginRecord, command: String, payload: JsonObject): JsonElement =
                if (command == "notification.config.get") buildJsonObject { put("values", buildJsonObject { put("targets", JsonArray(emptyList())) }) }
                else JsonArray(emptyList())
        }
        compose.setContent {
            val data by store.flow(record.pluginId).collectAsState(initial = ExtensionData(record.pluginId))
            MaterialTheme { ExtensionOwnedPage(record, manifest, data, html, PluginExtensionUiPage.Feed, actions, { exited = true }, {}) }
        }
        fun find(view: View): WebView? = if (view is WebView) view else if (view is ViewGroup)
            (0 until view.childCount).firstNotNullOfOrNull { find(view.getChildAt(it)) } else null
        compose.waitUntil(15_000) {
            instrumentation.runOnMainSync { WindowInspector.getGlobalWindowViews().firstNotNullOfOrNull(::find)?.let { web = it } }
            ::web.isInitialized
        }
        awaitJs("!!document.querySelector('#about')")
        js("document.querySelector('#about').click()")
        awaitJs("!!document.querySelector('#about-version')")
        assertEquals("false", js("!!document.querySelector('[data-tool=home]')"))
        repeat(4) { js("document.querySelector('#about-version').click()") }
        assertFalse(store.get(record.pluginId).diagnosticsEnabled)
        js("document.querySelector('#about-version').click()")
        awaitJs("!!document.querySelector('[data-tool=logs]')")
        assertTrue(store.get(record.pluginId).diagnosticsEnabled)
        js("document.querySelector('[data-tool=widget]').click()")
        awaitJs("!!document.querySelector('#widget-refresh')")
        instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
        awaitJs("!!document.querySelector('[data-tool=logs]')")
        assertFalse(exited)
        js("document.querySelector('[data-tool=disable]').click()")
        js("document.querySelector('#disable-tools').click()")
        awaitJs("!!document.querySelector('#about-version')")
        assertFalse(store.get(record.pluginId).diagnosticsEnabled)
        assertEquals("false", js("!!document.querySelector('[data-tool=home]')"))
        instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
        awaitJs("!document.querySelector('#component-tools')")
        assertFalse(exited)
        assertEquals("false", js("document.querySelector('#app').hidden"))
        store.remove(record.pluginId)
    }

    private fun awaitJs(expression: String) {
        try { compose.waitUntil(15_000) { js(expression) == "true" } }
        catch (error: Exception) { throw AssertionError("$expression; " + js("JSON.stringify({hasBack:window.__CurSimpleComponentHasBack,title:document.querySelector('.tool-top h1')?.textContent,history:history.state})"), error) }
    }
    private fun js(expression: String): String {
        val done = CountDownLatch(1)
        var result = ""
        instrumentation.runOnMainSync { web.evaluateJavascript(expression) { result = it; done.countDown() } }
        check(done.await(5, TimeUnit.SECONDS)) { "WebView did not answer" }
        return result
    }
}
