package com.x500x.cursimple.app.extension

import android.graphics.Bitmap
import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.x500x.cursimple.app.MainActivity
import com.x500x.cursimple.app.ClassScheduleApplication
import com.x500x.cursimple.app.theme.ClassScheduleTheme
import com.x500x.cursimple.core.data.*
import com.x500x.cursimple.core.plugin.install.*
import com.x500x.cursimple.core.plugin.manifest.*
import com.x500x.cursimple.feature.plugin.extension.*
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Opt-in isolated emulator QA. Network test never sends a phone number or SMS. */
class ComponentPageUiTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val inst get() = InstrumentationRegistry.getInstrumentation()
    private val ctx get() = inst.targetContext
    private fun web(view: View): WebView? = if (view is WebView) view else if (view is ViewGroup) (0 until view.childCount).firstNotNullOfOrNull { web(view.getChildAt(it)) } else null
    private fun js(code: String): String {
        val latch = CountDownLatch(1); var result = ""
        inst.runOnMainSync { val view = web(compose.activity.window.decorView); if (view == null) latch.countDown() else view.evaluateJavascript(code) { result = it; latch.countDown() } }
        check(latch.await(5, TimeUnit.SECONDS)) { "WebView evaluation timed out" }
        return result
    }
    private fun awaitJs(code: String, timeout: Long = 20_000) {
        val until = System.currentTimeMillis() + timeout
        while (System.currentTimeMillis() < until) { compose.waitForIdle(); if (js(code) == "true") return; Thread.sleep(150) }
        shot("failure")
        fail("Condition not met: $code; page=${js("document.body.innerText")}")
    }
    private fun viewportMetrics(): String = js("JSON.stringify({w:innerWidth,h:innerHeight,sh:document.documentElement.scrollHeight,overflow:document.documentElement.scrollHeight-innerHeight,dpr:devicePixelRatio,fontScale:getComputedStyle(document.documentElement).getPropertyValue('--font-scale')})")
    /** Long settings pages must keep every entry reachable by scrolling. */
    private fun verifySettingsCanBeScrolled() {
        assertEquals("true", js("!['hidden','clip'].includes(getComputedStyle(document.body).overflowY) && !['hidden','clip'].includes(getComputedStyle(document.getElementById('app')).overflowY)"))
        if (js("document.documentElement.scrollHeight <= innerHeight+1") != "true") {
            val rect = IntArray(4)
            inst.runOnMainSync {
                val view = requireNotNull(web(compose.activity.window.decorView))
                view.getLocationOnScreen(rect)
                rect[2] = view.width
                rect[3] = view.height
            }
            val downAt = SystemClock.uptimeMillis()
            for (step in 0..16) {
                val event = MotionEvent.obtain(downAt, SystemClock.uptimeMillis(), when (step) {
                    0 -> MotionEvent.ACTION_DOWN
                    16 -> MotionEvent.ACTION_UP
                    else -> MotionEvent.ACTION_MOVE
                }, rect[0] + rect[2] * .5f, rect[1] + rect[3] * (.85f - .5f * step / 16f), 0)
                event.source = InputDevice.SOURCE_TOUCHSCREEN
                inst.uiAutomation.injectInputEvent(event, true)
                event.recycle()
                if (step < 16) SystemClock.sleep(16)
            }
            awaitJs("window.scrollY > 0")
        }
        awaitJs("(()=>{const r=document.getElementById('remove').getBoundingClientRect();return r.top>=0&&r.bottom<=innerHeight+1})()")
        shot("04-settings-scrolled")
        js("window.scrollTo(0,0)")
        awaitJs("Math.abs(window.scrollY)<1")
    }
    private fun pageMetadata(): String = js("""
        JSON.stringify({w:innerWidth,h:innerHeight,sh:document.documentElement.scrollHeight,
          overflow:document.documentElement.scrollHeight-innerHeight,dpr:devicePixelRatio,
          fontScale:getComputedStyle(document.documentElement).getPropertyValue('--font-scale'),
          ready:document.readyState,fonts:document.fonts ? document.fonts.status : 'unavailable',
          app:getComputedStyle(document.getElementById('app')).height,
          list:[...document.querySelectorAll('.list-scroll,.list-scroll .item-row')].map(e=>({r:e.getBoundingClientRect().toJSON(),h:getComputedStyle(e).height,display:getComputedStyle(e).display})),
          layout:[...document.querySelectorAll('html,body,#app,#app > *,#app .stagger > *,#app .nav-row')].map(e=>{
            const s=getComputedStyle(e);return {tag:e.tagName,id:e.id,cls:e.className,
              r:e.getBoundingClientRect().toJSON(),sh:e.scrollHeight,ch:e.clientHeight,
              h:s.height,minHeight:s.minHeight,overflowY:s.overflowY,transform:s.transform,
              opacity:s.opacity,gap:s.gap,padding:s.padding,margin:s.margin};
          }),
          animations:document.getAnimations().map(a=>{const t=a.effect.getComputedTiming(),e=a.effect.target;
            return {target:e ? e.id || e.className : '',state:a.playState,pending:a.pending,
              finite:Number.isFinite(t.endTime),endTime:t.endTime,currentTime:a.currentTime};}),
          body:document.body.innerText})
    """.trimIndent())
    private fun awaitStableLayout(name: String, beforeLayout: String) {
        val until = SystemClock.elapsedRealtime() + 10_000
        var previous = ""
        var stableSince = 0L
        // DOM presence and two painted frames do not cover delayed entrance animations or async rerenders.
        // Wait for quiescence independently of scrollHeight; persistent overflow must still fail the assertion.
        while (SystemClock.elapsedRealtime() < until) {
            compose.waitForIdle()
            val sample = js("""
                (()=>{
                  const app=document.getElementById('app');
                  if(!app || document.readyState!=='complete' || app.querySelector('.skeleton') ||
                     (document.fonts && document.fonts.status!=='loaded') ||
                     document.getAnimations().some(a=>Number.isFinite(a.effect.getComputedTiming().endTime) && (a.pending || a.playState==='running')))return null;
                  return JSON.stringify({w:innerWidth,h:innerHeight,sh:document.documentElement.scrollHeight,
                    text:app.innerText,rects:[app,...app.querySelectorAll(':scope > *,.stagger > *,.nav-row')].map(e=>e.getBoundingClientRect().toJSON())});
                })()
            """.trimIndent())
            val now = SystemClock.elapsedRealtime()
            if (sample.isNotEmpty() && sample != "null") {
                if (sample != previous) { previous = sample; stableSince = now }
                else if (now - stableSince >= 600) return
            } else { previous = ""; stableSince = 0L }
            Thread.sleep(150)
        }
        shot(name, beforeLayout)
        fail("Layout did not settle within 10s: ${viewportMetrics()}; see component-qa/$name.json")
    }
    private fun shot(name: String, beforeLayout: String? = null) {
        val painted = CountDownLatch(1)
        inst.runOnMainSync {
            val view = requireNotNull(web(compose.activity.window.decorView))
            view.postVisualStateCallback(1L, object : WebView.VisualStateCallback() {
                override fun onComplete(requestId: Long) { view.postOnAnimation { view.postOnAnimation { painted.countDown() } } }
            })
        }
        check(painted.await(5, TimeUnit.SECONDS)) { "Page did not paint" }
        inst.waitForIdleSync()
        val target = File(ctx.getExternalFilesDir(null), "component-qa/$name.png"); target.parentFile!!.mkdirs()
        File(target.parentFile, "$name.json").writeText(pageMetadata())
        if (beforeLayout != null) File(target.parentFile, "$name-before.json").writeText(beforeLayout)
        inst.uiAutomation.takeScreenshot().useBitmap { target.outputStream().use { out -> it.compress(Bitmap.CompressFormat.PNG, 100, out) } }
    }
    private inline fun Bitmap.useBitmap(block: (Bitmap) -> Unit) { try { block(this) } finally { recycle() } }
    @Test fun componentScreensAndLiveQr() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("componentUiQa") == "true")
        val manager = (compose.activity.application as ClassScheduleApplication).appContainer.pluginManager
        val store = ExtensionStore.get(ctx)
        val record = runBlocking {
            DataStoreUserPreferencesRepository(ctx).apply { setDisclaimerAccepted(true); setFirstRunGuideCompleted(true) }
            val result = manager.installPackage(File(ctx.getExternalFilesDir(null), "component.zip").readBytes(), PluginInstallSource.Local)
            check(result is PluginInstallResult.Success) { result.toString() }
            store.update(result.record.pluginId) { ExtensionData(result.record.pluginId) }; result.record
        }
        val route = mutableStateOf("feed")
        val dark = mutableStateOf(false)
        val actions = object : ExtensionHostActions {
            override suspend fun loadPackage(record: InstalledPluginRecord) = manager.loadExtensionPackage(record)
            override suspend fun loadUi(record: InstalledPluginRecord) = manager.loadExtensionUi(record)
            override suspend fun loadUi(record: InstalledPluginRecord, page: PluginExtensionUiPage) = manager.loadExtensionUi(record, page)
            override suspend fun syncNow(record: InstalledPluginRecord) = ExtensionSyncOutcome.Skipped(record.pluginId, "qa_no_sync")
            override fun onDataChanged(pluginId: String) {}
            override fun openFeed(pluginId: String) { route.value = "feed" }
        }
        compose.runOnUiThread { compose.activity.setContent {
            ClassScheduleTheme(if(dark.value) ThemeMode.Dark else ThemeMode.Light) {
                if (route.value == "settings") ExtensionSettingsScreen(record, actions, {route.value="feed"}, {}, Modifier.fillMaxSize().safeDrawingPadding())
                else ExtensionFeedScreen(record, actions, {route.value="settings"}, Modifier.fillMaxSize().safeDrawingPadding())
            }
        } }
        awaitJs("!!document.getElementById('login')")
        assertEquals("0", js("document.querySelectorAll('.calendar').length"))
        assertEquals("true", js("document.documentElement.scrollHeight <= innerHeight+1"))
        shot("01-logged-out")
        js("document.getElementById('login').click()")
        awaitJs("!!document.querySelector('#back svg') && !!document.getElementById('mobile')")
        shot("02-login-phone")
        assertEquals(js("JSON.stringify({w:innerWidth,h:innerHeight,sh:document.documentElement.scrollHeight,dpr:devicePixelRatio,zoom:getComputedStyle(document.body).fontSize})"), "true", js("document.documentElement.scrollHeight <= innerHeight+1"))
        js("document.getElementById('qr-tab').click()")
        awaitJs("!!document.querySelector('img.qr') && document.querySelector('img.qr').naturalWidth > 0", 40_000)
        awaitJs("document.getElementById('qr-countdown').textContent.includes('后失效')")
        shot("03-login-qr")
        js("document.getElementById('phone-tab').click()")
        awaitJs("document.getElementById('qr-panel').hidden")
        // Call host's real validation with no account. It must return feedback, never save a session.
        js("window.qaCheck='pending';CurSimpleComponent.request('login.check',{navigate:false}).then(()=>qaCheck='unexpected-success').catch(e=>qaCheck=e.message)")
        awaitJs("window.qaCheck !== 'pending'", 42_000)
        assertNotEquals("\"unexpected-success\"", js("window.qaCheck"))
        assertEquals(ExtensionLoginState.Never, runBlocking { store.get(record.pluginId).loginState })
        // Fixture content is local test data, never reported as a verified real account.
        val fixtureNow = com.x500x.cursimple.core.kernel.time.BeijingTime.nowMillis(com.x500x.cursimple.core.kernel.time.BeijingTime.zone)
        runBlocking { store.update(record.pluginId) { it.copy(loginState=ExtensionLoginState.LoggedIn, account=ExtensionAccount("qa","演示同学"), items=listOf(
            ExtensionFeedItem("qa-notice","announcement","课程调整通知",course="计算机网络",publishAt=fixtureNow,content="下周课程调整至信息楼 302。",kind="notice"),
            ExtensionFeedItem("qa-task","homework","第三章课后练习",course="计算机网络",dueAt=fixtureNow,kind="task"),
        )) } }
        js("CurSimpleComponent.request('ui.feed')")
        awaitJs("document.querySelectorAll('.day').length >= 28")
        js("document.getElementById('settings').click()")
        awaitJs("!!document.querySelector('[data-panel=reminder]')")
        val settingsBefore = pageMetadata()
        awaitStableLayout("04-settings", settingsBefore)
        shot("04-settings", settingsBefore)
        verifySettingsCanBeScrolled()
        js("document.querySelector('[data-panel=reminder]').click();document.querySelector('[data-host=notifyNew]').click()")
        awaitJs("CurSimpleComponent.state.data.host.notifyNew===false")
        assertFalse(runBlocking { store.get(record.pluginId).host.notifyNew })
        js("document.querySelector('[data-host=notifyNew]').click()")
        awaitJs("CurSimpleComponent.state.data.host.notifyNew===true")
        assertTrue(runBlocking { store.get(record.pluginId).host.notifyNew })
        js("document.getElementById('sheet-close').click()")
        js("document.getElementById('feed').click()")
        awaitJs("document.querySelectorAll('.day').length >= 28")
        assertEquals("true", js("document.documentElement.scrollHeight <= innerHeight+1"))
        shot("05-month")
        js("document.getElementById('selected').click()")
        awaitJs("!!document.querySelector('[data-item=qa-notice]')")
        js("document.querySelector('[data-item=qa-notice]').click()")
        awaitJs("!!document.querySelector('.body')")
        shot("06-detail")
        js("document.getElementById('sheet-close').click()")
        js("document.querySelector('#view-seg [data-seg=list]').click()")
        awaitJs("document.querySelectorAll('.list-scroll .item-row').length===2")
        assertEquals("true", js("document.querySelector('.list-scroll').getBoundingClientRect().height > 100"))
        shot("07-list")
        compose.runOnUiThread { dark.value=true; androidx.core.view.WindowCompat.getInsetsController(compose.activity.window, compose.activity.window.decorView).isAppearanceLightStatusBars=false }
        awaitJs("document.documentElement.style.colorScheme==='dark'")
        shot("08-dark")
    }
}
