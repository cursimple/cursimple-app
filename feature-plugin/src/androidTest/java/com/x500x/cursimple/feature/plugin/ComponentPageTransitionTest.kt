package com.x500x.cursimple.feature.plugin

import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
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
import kotlinx.coroutines.delay
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

class ComponentPageTransitionTest {
    @get:Rule val compose = createComposeRule()
    private val inst get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = inst.targetContext

    @androidx.test.filters.SdkSuppress(minSdkVersion = 29)
    @Test fun settingsStayInsideTheComponentAndRestoreTheSameFeed() = runBlocking {
        assumeTrue(InstrumentationRegistry.getArguments().getString("componentMotionQa") == "true")
        val directory = File(context.cacheDir, "component-motion-qa").apply { deleteRecursively(); mkdirs() }
        ZipInputStream(File(context.filesDir, "qa-component.zip").inputStream()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                val file = File(directory, entry.name).canonicalFile
                require(file.path.startsWith(directory.canonicalPath + "/"))
                if (!entry.isDirectory) { file.parentFile?.mkdirs(); file.outputStream().use { zip.copyTo(it) } }
            }
        }
        val manifest = Json { ignoreUnknownKeys = true }.decodeFromString<PluginManifest>(File(directory, "manifest.json").readText())
        val record = InstalledPluginRecord(manifest.id, manifest.name, version = manifest.version, versionCode = manifest.versionCode,
            storagePath = directory.path, installedAt = "motion-qa", source = PluginInstallSource.Local)
        fun html(page: PluginExtensionUiPage) = File(directory, manifest.extension!!.ui!!.entryFor(page)!!).readText()
        val store = ExtensionStore.get(context)
        store.update(record.pluginId) { ExtensionData(record.pluginId, loginState = ExtensionLoginState.LoggedIn, account = ExtensionAccount("qa", "演示账号")) }
        var externalSettings = false
        var failNext = true
        var settingsLoads = 0
        val actions = object : ExtensionHostActions {
            override suspend fun loadPackage(record: InstalledPluginRecord) = manifest to ""
            override suspend fun loadUi(record: InstalledPluginRecord) = html(PluginExtensionUiPage.Feed)
            override suspend fun loadUi(record: InstalledPluginRecord, page: PluginExtensionUiPage): String {
                if (page == PluginExtensionUiPage.Settings) {
                    settingsLoads++
                    delay(500)
                    if (failNext) { failNext = false; error("QA load failed") }
                }
                return html(page)
            }
            override suspend fun syncNow(record: InstalledPluginRecord): ExtensionSyncOutcome = error("QA offline")
            override fun onDataChanged(pluginId: String) {}
            override fun openFeed(pluginId: String) { error("Unexpected external navigation") }
        }
        compose.setContent {
            val data by store.flow(record.pluginId).collectAsState(initial = ExtensionData(record.pluginId))
            MaterialTheme { ExtensionOwnedPage(record, manifest, data, html(PluginExtensionUiPage.Feed), PluginExtensionUiPage.Feed,
                actions, {}, { externalSettings = true }) }
        }
        awaitJs("!!document.querySelector('#next')")
        val original = activeWeb()
        js("window.qaRetained='same document';document.querySelector('#next').click()")
        awaitJs("!document.querySelector('.slide-out-l')")
        val month = js("document.querySelector('.cal-head h2').textContent")
        js("document.querySelector('#settings').click()")
        assertSame(original, activeWeb(false))
        compose.waitUntil(8_000) { settingsLoads == 1 }
        compose.waitForIdle()
        // A failed destination must leave the source interactive and retryable.
        awaitJs("document.querySelector('#settings')!==null")
        assertSame(original, activeWeb())
        js("document.querySelector('#settings').click()")
        awaitJs("!!document.querySelector('[data-panel=reminder]')")
        assertFalse(externalSettings)
        val settings = activeWeb()
        assertNotSame(original, settings)
        inst.runOnMainSync {
            val bitmap = android.graphics.Bitmap.createBitmap(settings.width, settings.height, android.graphics.Bitmap.Config.ARGB_8888)
            settings.draw(android.graphics.Canvas(bitmap))
            File(context.filesDir, "component-settings-motion.png").outputStream().use {
                bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
            }
            bitmap.recycle()
        }
        js("document.querySelector('[data-panel=reminder]').click()")
        awaitJs("!!document.querySelector('#sheet')")
        inst.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
        awaitJs("!document.querySelector('#sheet')")
        assertSame(settings, activeWeb())
        js("window.scrollTo(0,160)")
        val scroll = js("Math.round(scrollY)")
        inst.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
        awaitJs("window.qaRetained==='same document'")
        assertSame(original, activeWeb())
        assertEquals(month, js("document.querySelector('.cal-head h2').textContent"))
        js("document.querySelector('#settings').click()")
        awaitJs("!!document.querySelector('[data-panel=reminder]')")
        assertSame(settings, activeWeb())
        assertEquals(scroll, js("Math.round(scrollY)"))
        assertEquals(2, settingsLoads)
        js("document.querySelector('#back').click()")
        awaitJs("window.qaRetained==='same document'")
        assertFalse(externalSettings)
        store.remove(record.pluginId)
    }

    private fun activeWeb(interactive: Boolean = true): WebView {
        var result: WebView? = null
        fun find(view: View): WebView? = when {
            view is WebView && view.visibility == View.VISIBLE && (!interactive || view.importantForAccessibility == View.IMPORTANT_FOR_ACCESSIBILITY_AUTO) -> view
            view is ViewGroup -> (0 until view.childCount).firstNotNullOfOrNull { find(view.getChildAt(it)) }
            else -> null
        }
        inst.runOnMainSync { result = WindowInspector.getGlobalWindowViews().firstNotNullOfOrNull(::find) }
        return requireNotNull(result) { "No interactive component document" }
    }
    private fun js(code: String): String {
        val done = CountDownLatch(1); var result = ""
        val view = activeWeb()
        inst.runOnMainSync { view.evaluateJavascript(code) { result = it; done.countDown() } }
        check(done.await(5, TimeUnit.SECONDS))
        return result
    }
    private fun awaitJs(code: String) {
        try { compose.waitUntil(15_000) { runCatching { js(code) == "true" }.getOrDefault(false) } }
        catch(error: Exception) { throw AssertionError(code + "; " + runCatching { js("JSON.stringify({page:location.href,retained:window.qaRetained,hasBack:window.__CurSimpleComponentHasBack,body:document.body.innerText})") }.getOrNull(), error) }
    }
}
