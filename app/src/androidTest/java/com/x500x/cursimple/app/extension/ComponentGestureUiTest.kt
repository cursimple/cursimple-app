package com.x500x.cursimple.app.extension

import android.content.Intent
import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.x500x.cursimple.BuildConfig
import com.x500x.cursimple.app.MainActivity
import com.x500x.cursimple.app.ClassScheduleApplication
import com.x500x.cursimple.core.data.*
import com.x500x.cursimple.core.plugin.install.*
import com.x500x.cursimple.feature.plugin.extension.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import java.io.File
import java.time.LocalDate
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Touch injection through MainActivity; dedicated QA emulator only. */
class ComponentGestureUiTest {
    private val compose = createAndroidComposeRule<MainActivity>()
    private val inst get() = InstrumentationRegistry.getInstrumentation()
    private val ctx get() = inst.targetContext
    private lateinit var record: InstalledPluginRecord
    private val setup = object : ExternalResource() {
        override fun before() {
            if (InstrumentationRegistry.getArguments().getString("componentGestureQa") != "true") return
            runBlocking {
                DataStoreUserPreferencesRepository(ctx).apply {
                    setDisclaimerAccepted(true); setFirstRunGuideCompleted(true)
                    markIslandStartupPromptShown(); markNotificationPermissionStartupAsked()
                    setTermStartDate(LocalDate.now().minusDays(7)); setAutoUpdateEnabled(false)
                    setLastSeenVersionCode(BuildConfig.VERSION_CODE); setPluginRegistryRepo("")
                    setAppLanguage(AppLanguage.Chinese)
                }
                AppLocale.cache(ctx, AppLanguage.Chinese)
                com.x500x.cursimple.app.reminder.ForceStopMonitor.markPrompted(ctx)
                val manager = (ctx.applicationContext as ClassScheduleApplication).appContainer.pluginManager
                val result = manager.installPackage(File(ctx.getExternalFilesDir(null), "component.zip").readBytes(), PluginInstallSource.Local)
                check(result is PluginInstallResult.Success)
                record = result.record
                val now = System.currentTimeMillis()
                ExtensionStore.get(ctx).update(record.pluginId) {
                    ExtensionData(record.pluginId, loginState=ExtensionLoginState.LoggedIn,
                        account=ExtensionAccount("qa", "滑动测试"),
                        host=ExtensionHostSettings(feed=ExtensionFeedSettings(defaultView=ExtensionFeedView.List)),
                        items=(1..60).map { n -> ExtensionFeedItem("qa-$n", "announcement", "测试公告 $n", course="测试课程", publishAt=now,
                            kind="notice", content=(1..80).joinToString("\n") { "第 $it 段，用于验证详情面板连续滚动。" }) })
                }
            }
        }
    }
    @get:Rule val rules: RuleChain = RuleChain.outerRule(setup).around(compose)
    private fun findWeb(view: View): WebView? = if(view is WebView) view else if(view is ViewGroup) (0 until view.childCount).firstNotNullOfOrNull { findWeb(view.getChildAt(it)) } else null
    private fun js(script: String): String {
        val done=CountDownLatch(1); var result=""
        inst.runOnMainSync { val view=findWeb(compose.activity.window.decorView); if(view==null)done.countDown() else view.evaluateJavascript(script) {result=it;done.countDown()} }
        check(done.await(5, TimeUnit.SECONDS));return result
    }
    private fun awaitJs(script: String) {
        val until=SystemClock.uptimeMillis()+20_000
        while(SystemClock.uptimeMillis()<until){compose.waitForIdle();if(js(script)=="true")return;Thread.sleep(80)}
        fail("Not ready: $script; ${js("document.body.innerText")}")
    }
    private fun drawerVisible(): Boolean {
        val nodes=compose.onAllNodesWithText("换主题")
        return nodes.fetchSemanticsNodes().indices.any { nodes[it].isDisplayed() }
    }
    private fun swipe(x1: Float=.12f,y1: Float=.75f,x2: Float=.82f,y2: Float=.48f) {
        compose.waitForIdle()
        val bounds=IntArray(4)
        inst.runOnMainSync { val root=compose.activity.window.decorView; bounds[2]=root.width;bounds[3]=root.height }
        val down=SystemClock.uptimeMillis()
        for(i in 0..24){
            val fraction=i/24f
            val event=MotionEvent.obtain(down,SystemClock.uptimeMillis(),when(i){0->MotionEvent.ACTION_DOWN;24->MotionEvent.ACTION_UP;else->MotionEvent.ACTION_MOVE},
                (x1+(x2-x1)*fraction)*bounds[2],(y1+(y2-y1)*fraction)*bounds[3],0)
            event.source=InputDevice.SOURCE_TOUCHSCREEN
            inst.uiAutomation.injectInputEvent(event,true);event.recycle();if(i<24)SystemClock.sleep(12)
        }
        compose.waitForIdle()
    }
    @Test fun componentScrollDoesNotOpenDrawerAndMenuStillWorks() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("componentGestureQa")=="true")
        compose.runOnUiThread { compose.activity.startActivity(Intent(compose.activity, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP).putExtra(MainActivity.EXTRA_OPEN_EXTENSION_FEED,record.installKey)) }
        awaitJs("document.querySelectorAll('.list-scroll .item-row').length===60")
        js("window.qaCancels=0;window.qaPushes=0;document.addEventListener('touchcancel',()=>qaCancels++);const push=window.__CurSimpleComponentPush;window.__CurSimpleComponentPush=x=>{qaPushes++;push(x)};")
        repeat(4){swipe()}
        assertFalse("组件内斜滑打开了抽屉",drawerVisible())
        assertEquals("滚动被父级取消", "0", js("qaCancels"))
        swipe(.5f,.82f,.54f,.32f)
        awaitJs("document.querySelector('.list-scroll').scrollTop>100")
        val before=js("document.querySelector('.list-scroll').scrollTop").toDouble()
        val pushesBeforeNavigation=js("qaPushes")
        // Drawer changes must preserve component state and scroll position.
        compose.onNodeWithContentDescription("打开侧边栏").performClick()
        assertTrue("菜单按钮无法打开抽屉",drawerVisible())
        swipe(.52f,.45f,.05f,.45f)
        assertFalse("已打开的抽屉不能滑动关闭",drawerVisible())
        assertTrue(js("document.querySelector('.list-scroll').scrollTop").toDouble()>=before-2)
        assertEquals("无关导航导致组件重画", pushesBeforeNavigation, js("qaPushes"))
        js("document.querySelector('[data-item=qa-1]').click()")
        awaitJs("!!document.querySelector('.sheet-body .body')")
        swipe(.2f,.82f,.8f,.52f); swipe(.5f,.82f,.52f,.42f)
        assertFalse(drawerVisible());awaitJs("document.querySelector('.sheet-body').scrollTop>50")
        js("document.getElementById('sheet-close').click();document.getElementById('settings').click()")
        awaitJs("!!document.querySelector('[data-panel=schedule]')")
        swipe();assertFalse("组件设置也应保护手势",drawerVisible())
        js("document.querySelector('[data-panel=schedule]').click()")
        awaitJs("!!document.querySelector('[data-host=addToSchedule]')")
        swipe();assertFalse(drawerVisible())
        js("document.getElementById('sheet-close').click();document.getElementById('back').click()")
        compose.waitUntil(10_000) {
            var absent=false
            inst.runOnMainSync { absent=findWeb(compose.activity.window.decorView)==null }
            absent
        }
        compose.waitForIdle()
        swipe(.1f,.5f,.86f,.5f)
        if (!drawerVisible()) {
            val screenshot=File(ctx.getExternalFilesDir(null), "gesture-return.png")
            val bitmap=inst.uiAutomation.takeScreenshot()
            screenshot.outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it) }; bitmap.recycle()
        }
        assertTrue("退出组件后未恢复普通页面的抽屉手势",drawerVisible())
    }
}
