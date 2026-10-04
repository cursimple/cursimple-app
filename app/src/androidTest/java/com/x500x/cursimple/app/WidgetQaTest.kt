package com.x500x.cursimple.app

import android.app.Activity
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.ListView
import androidx.test.platform.app.InstrumentationRegistry
import com.x500x.cursimple.core.data.AppLanguage
import com.x500x.cursimple.core.data.AppLocale
import com.x500x.cursimple.core.data.ThemeMode
import com.x500x.cursimple.core.data.widget.PendingTask
import com.x500x.cursimple.core.data.widget.PendingTaskFeed
import com.x500x.cursimple.core.kernel.model.ClassSlotTime
import com.x500x.cursimple.core.kernel.model.CourseCategory
import com.x500x.cursimple.core.kernel.model.CourseItem
import com.x500x.cursimple.core.kernel.model.CourseTimeSlot
import com.x500x.cursimple.core.kernel.model.ScheduleEvent
import com.x500x.cursimple.core.kernel.model.TermTimingProfile
import com.x500x.cursimple.feature.widget.CalendarWidgetReceiver
import com.x500x.cursimple.feature.widget.ComponentWidgetAvailability
import com.x500x.cursimple.feature.widget.WidgetCatalog
import com.x500x.cursimple.feature.widget.WidgetGuardHooks
import com.x500x.cursimple.feature.widget.PendingTaskWidgetReceiver
import com.x500x.cursimple.feature.widget.WidgetDeepLinks
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertNotNull
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import com.x500x.cursimple.feature.widget.R as WidgetR

/**
 * 只在独立 QA 模拟器上显式启用（widgetQa=true，并先 `adb shell appwidget grantbind`）：
 * 用 AppWidgetHost 挂上真实的「课程日历」「待完成」，走一遍 provider → RemoteViews → 点击的完整链路，并截图。
 */
class WidgetQaTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context: Context get() = instrumentation.targetContext
    private lateinit var host: AppWidgetHost
    private val boundIds = mutableListOf<Int>()

    @Before fun setUp() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("widgetQa") == "true")
        seed()
        host = AppWidgetHost(context, HOST_ID)
        instrumentation.runOnMainSync { host.startListening() }
    }

    @After fun tearDown() {
        if (!::host.isInitialized) return
        boundIds.forEach { host.deleteAppWidgetId(it) }
        instrumentation.runOnMainSync { host.stopListening() }
    }

    @Test fun calendarWeekMonthAndDayDeepLink() {
        val (id, view) = bind(CalendarWidgetReceiver::class.java, 330, 360)
        refresh { CalendarWidgetReceiver.updateWidgets(context, intArrayOf(id)) }
        capture(view, 330, 360, "widget-calendar-week.png")

        click(view, WidgetR.id.calendar_mode)
        capture(view, 330, 360, "widget-calendar-month.png")
        click(view, WidgetR.id.calendar_next)
        capture(view, 330, 360, "widget-calendar-month-next.png")
        click(view, WidgetR.id.calendar_reset)
        click(view, WidgetR.id.calendar_mode)
        capture(view, 330, 360, "widget-calendar-week-again.png")

        // 每日课程：和课程日历共用同一套翻页箭头
        val (todayId, today) = bind(com.x500x.cursimple.feature.widget.ScheduleGlanceWidgetReceiver::class.java, 330, 200)
        refresh { com.x500x.cursimple.feature.widget.ScheduleGlanceWidgetReceiver.updateWidgets(context, intArrayOf(todayId)) }
        capture(today, 330, 200, "widget-today.png")

        // 小尺寸也要排得开
        val (smallId, small) = bind(CalendarWidgetReceiver::class.java, 250, 190)
        refresh { CalendarWidgetReceiver.updateWidgets(context, intArrayOf(smallId)) }
        capture(small, 250, 190, "widget-calendar-small.png")

        // 点周四那一列：进 App 的日视图，停在 9 月 17 日
        val monitor = instrumentation.addMonitor(MainActivity::class.java.name, null, false)
        click(view, WidgetR.id.calendar_hit_0_3, waitMs = 0)
        val activity = instrumentation.waitForMonitorWithTimeout(monitor, 10_000)
        assertNotNull("点日期没有打开应用", activity)
        assertEquals("2026-09-17", activity!!.intent.getStringExtra(WidgetDeepLinks.EXTRA_OPEN_SCHEDULE_DATE))
        Thread.sleep(2500)
        saveBitmap(instrumentation.uiAutomation.takeScreenshot(), "widget-calendar-open-day.png")
        activity.finishAndWait()
    }

    @Test fun guardAlarmAlsoChecksComponentSync() {
        // App 启动时就该把组件同步挂到守护闹钟上
        val appHook = WidgetGuardHooks.onGuardTick
        assertNotNull("App 没有把组件同步挂到守护闹钟上", appHook)
        val fired = java.util.concurrent.CountDownLatch(1)
        WidgetGuardHooks.onGuardTick = { fired.countDown() }
        try {
            context.sendBroadcast(
                android.content.Intent("com.x500x.cursimple.feature.widget.action.ALARM_GUARD_TICK")
                    .setClassName(context, "com.x500x.cursimple.feature.widget.WidgetAlarmGuardReceiver"),
            )
            assertTrue("守护闹钟响了却没有检查组件同步", fired.await(15, java.util.concurrent.TimeUnit.SECONDS))
        } finally {
            WidgetGuardHooks.onGuardTick = appHook
        }
    }

    @Test fun componentWidgetsOnlyAppearWithComponents() {
        val pm = context.packageManager
        val provider = ComponentName(context, PendingTaskWidgetReceiver::class.java)
        ComponentWidgetAvailability.update(context, false)
        assertEquals(PackageManager.COMPONENT_ENABLED_STATE_DISABLED, pm.getComponentEnabledSetting(provider))
        assertFalse(WidgetCatalog.pickerEntries(context).any { it.fromComponents })
        assertTrue(WidgetCatalog.pickerEntries(context).any { it.id == "calendar" })
        ComponentWidgetAvailability.update(context, true)
        assertEquals(PackageManager.COMPONENT_ENABLED_STATE_ENABLED, pm.getComponentEnabledSetting(provider))
        assertTrue(WidgetCatalog.pickerEntries(context).any { it.fromComponents })
    }

    @Test fun tasksListAndComponentDeepLink() {
        // 演示机上不一定装着组件，这里直接把组件小组件上架。
        // 冷启动时 App 会按「没有组件」先把它下架一次，等那一步跑完再上架，免得被覆盖
        Thread.sleep(4000)
        ComponentWidgetAvailability.update(context, true)
        awaitProvider(PendingTaskWidgetReceiver::class.java)
        val (id, view) = bind(PendingTaskWidgetReceiver::class.java, 330, 230)
        Thread.sleep(1500)
        writeTasks()
        refresh { PendingTaskWidgetReceiver.updateWidgets(context, intArrayOf(id)) }
        capture(view, 330, 230, "widget-tasks.png")

        val monitor = instrumentation.addMonitor(MainActivity::class.java.name, null, false)
        instrumentation.runOnMainSync {
            val list = view.findViewById<ListView>(WidgetR.id.tasks_list)
            val row = list.getChildAt(0)
            assertNotNull("待完成列表没有行", row)
            // 列表里的点击由 AdapterView 的条目点击分发（桌面上也是这样），不是行自己的点击
            list.performItemClick(row, 0, list.getItemIdAtPosition(0))
        }
        val activity = instrumentation.waitForMonitorWithTimeout(monitor, 10_000)
        assertNotNull("点任务没有打开应用", activity)
        assertEquals("qa-component", activity!!.intent.getStringExtra(WidgetDeepLinks.EXTRA_OPEN_COMPONENT_PAGE))
        activity.finishAndWait()

        PendingTaskFeed.write(context, emptyList())
        refresh { PendingTaskWidgetReceiver.updateWidgets(context, intArrayOf(id)) }
        capture(view, 330, 230, "widget-tasks-empty.png")
    }

    /** 空状态换成每天一句的闲话：周末没课、国庆、整周没课各截一张 */
    @Test fun emptyStatesSpeakWithMood() = runBlocking {
        val preferences = (context.applicationContext as ClassScheduleApplication).appContainer.userPreferencesRepository
        val (todayId, today) = bind(com.x500x.cursimple.feature.widget.ScheduleGlanceWidgetReceiver::class.java, 330, 200)
        val (nextId, next) = bind(com.x500x.cursimple.feature.widget.NextCourseGlanceWidgetReceiver::class.java, 330, 160)
        val (calendarId, calendar) = bind(CalendarWidgetReceiver::class.java, 330, 360)
        suspend fun shoot(suffix: String) {
            refresh { com.x500x.cursimple.feature.widget.ScheduleGlanceWidgetReceiver.updateWidgets(context, intArrayOf(todayId)) }
            refresh { com.x500x.cursimple.feature.widget.NextCourseGlanceWidgetReceiver.updateWidgets(context, intArrayOf(nextId)) }
            capture(today, 330, 200, "widget-mood-today-$suffix.png")
            capture(next, 330, 160, "widget-mood-next-$suffix.png")
        }
        // 9 月 19 日周六，没课
        preferences.setDebugForcedDateTime(LocalDateTime.of(2026, 9, 19, 10, 0))
        shoot("weekend")
        // 10 月 2 日，内置日历里是国庆假期
        preferences.setHolidayCalendarBuiltInEnabled(true)
        preferences.setDebugForcedDateTime(LocalDateTime.of(2026, 10, 2, 10, 0))
        shoot("national")
        preferences.setHolidayCalendarBuiltInEnabled(false)
        // 第 17 周以后课表空了
        preferences.setDebugForcedDateTime(LocalDateTime.of(2027, 1, 6, 10, 0))
        refresh { CalendarWidgetReceiver.updateWidgets(context, intArrayOf(calendarId)) }
        capture(calendar, 330, 360, "widget-mood-calendar-empty-week.png")
    }

    // ---------------- 挂载与截图 ----------------

    private fun bind(provider: Class<*>, widthDp: Int, heightDp: Int): Pair<Int, AppWidgetHostView> {
        val manager = AppWidgetManager.getInstance(context)
        val id = host.allocateAppWidgetId()
        boundIds += id
        val options = Bundle().apply {
            putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, widthDp)
            putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, widthDp)
            putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, heightDp)
            putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, heightDp)
        }
        check(manager.bindAppWidgetIdIfAllowed(id, ComponentName(context, provider), options)) {
            "绑定失败：先执行 adb shell appwidget grantbind --package ${context.packageName}"
        }
        lateinit var view: AppWidgetHostView
        instrumentation.runOnMainSync { view = host.createView(context, id, manager.getAppWidgetInfo(id)) }
        return id to view
    }

    /** 启用 receiver 后，系统要等收到包变更广播才把它登记成小组件提供方 */
    private fun awaitProvider(provider: Class<*>) {
        val manager = AppWidgetManager.getInstance(context)
        val component = ComponentName(context, provider)
        repeat(50) {
            if (manager.installedProviders.any { it.provider == component }) return
            Thread.sleep(200)
        }
        error("小组件提供方 ${component.className} 没有登记")
    }

    private fun refresh(block: suspend () -> Unit) {
        runBlocking { block() }
        Thread.sleep(1200)
        instrumentation.waitForIdleSync()
    }

    private fun click(view: AppWidgetHostView, id: Int, waitMs: Long = 1800) {
        instrumentation.runOnMainSync {
            val target = view.findViewById<View>(id)
            assertNotNull("找不到控件 $id", target)
            target.performClick()
        }
        if (waitMs > 0) {
            Thread.sleep(waitMs)
            instrumentation.waitForIdleSync()
        }
    }

    private fun capture(view: AppWidgetHostView, widthDp: Int, heightDp: Int, name: String) {
        val density = context.resources.displayMetrics.density
        val w = (widthDp * density).toInt()
        val h = (heightDp * density).toInt()
        val margin = (12 * density).toInt()
        lateinit var bitmap: Bitmap
        instrumentation.runOnMainSync {
            if (view.layoutParams == null) view.layoutParams = ViewGroup.LayoutParams(w, h)
            view.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY))
            view.layout(0, 0, w, h)
            // 列表行是在布局后才建出来的，再排一遍
            view.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY))
            view.layout(0, 0, w, h)
            bitmap = Bitmap.createBitmap(w + margin * 2, h + margin * 2, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            canvas.drawColor(0xFFD9E3DC.toInt())
            canvas.translate(margin.toFloat(), margin.toFloat())
            view.draw(canvas)
        }
        saveBitmap(bitmap, name)
    }

    private fun saveBitmap(bitmap: Bitmap, name: String) {
        File(context.filesDir, name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun Activity.finishAndWait() {
        finish()
        Thread.sleep(800)
    }

    // ---------------- 演示数据 ----------------

    private fun seed() = runBlocking {
        val app = context.applicationContext as ClassScheduleApplication
        val container = app.appContainer
        container.bootstrapJob.join()
        val preferences = container.userPreferencesRepository
        AppLanguage.entries.firstOrNull { it.tag.isNotEmpty() && it.tag == InstrumentationRegistry.getArguments().getString("widgetQaLanguage") }
            ?.let { language ->
                preferences.setAppLanguage(language)
                AppLocale.cache(context, language)
            }
        val termStart = LocalDate.of(2026, 9, 7)
        val term = container.termProfileRepository.createTerm("2026 秋季", termStart.toString())
        container.termProfileRepository.setActiveTerm(term.id)
        preferences.setTermStartDate(termStart)
        preferences.setTermStartUserDecided(true)
        preferences.setDisclaimerAccepted(true)
        preferences.setFirstRunGuideCompleted(true)
        preferences.markNotificationPermissionStartupAsked()
        preferences.markIslandStartupPromptShown()
        com.x500x.cursimple.app.reminder.ForceStopMonitor.markPrompted(context)
        preferences.setLastSeenVersionCode(com.x500x.cursimple.BuildConfig.VERSION_CODE)
        preferences.setAutoUpdateEnabled(false)
        preferences.setClassNoticeEnabled(false)
        preferences.setThemeMode(ThemeMode.Light)
        preferences.setDebugForcedDateTime(LocalDateTime.of(2026, 9, 15, 10, 0))
        preferences.resetScheduleAppearanceAndDisplay()
        preferences.setHolidayCalendarBuiltInEnabled(false)
        // 作息表里改过名字、晚上两节合成一个时段：小组件左栏要原样显示
        container.widgetPreferencesRepository.saveManualTimingProfile(TermTimingProfile(
            termStart.toString(), listOf(
                ClassSlotTime(1, 1, "08:00", "08:45", label = "第一节"), ClassSlotTime(2, 2, "08:50", "09:35", label = "第二节"),
                ClassSlotTime(3, 3, "09:55", "10:40", label = "第三节"), ClassSlotTime(4, 4, "10:45", "11:30", label = "第四节"),
                ClassSlotTime(5, 5, "11:35", "12:20", label = "第五节"), ClassSlotTime(6, 6, "12:25", "13:10", label = "午间课"),
                ClassSlotTime(7, 7, "14:00", "14:45", label = "第七节"), ClassSlotTime(8, 8, "14:50", "15:35", label = "第八节"),
                ClassSlotTime(9, 9, "15:55", "16:40", label = "第九节"), ClassSlotTime(10, 10, "16:45", "17:30", label = "第十节"),
                ClassSlotTime(11, 12, "19:00", "20:35", label = "晚课"),
            ),
        ))
        fun course(id: String, title: String, day: Int, start: Int, end: Int, location: String, weeks: List<Int> = (1..16).toList(), category: CourseCategory = CourseCategory.Course) =
            CourseItem(id, title, teacher = "张老师", location = location, weeks = weeks, time = CourseTimeSlot(day, start, end), category = category)
        container.manualCourseRepository.replaceAll(listOf(
            course("qa-w-math-mon", "高等数学", 1, 1, 2, "A101"),
            course("qa-w-english", "大学英语", 1, 3, 4, "B202"),
            course("qa-w-sport", "体育", 1, 7, 8, "体育馆"),
            course("qa-w-algebra", "线性代数", 2, 1, 2, "A203"),
            course("qa-w-program", "程序设计", 2, 5, 6, "机房 3"),
            course("qa-w-lab", "物理实验", 2, 5, 7, "实验楼"),
            course("qa-w-history", "近代史纲要", 3, 3, 4, "C301"),
            course("qa-w-math-thu", "高等数学", 4, 3, 4, "A101"),
            course("qa-w-circuit", "电路分析", 4, 7, 9, "D105"),
            course("qa-w-physics", "大学物理", 5, 1, 2, "B305"),
            course("qa-w-midterm", "线性代数期中", 5, 5, 6, "A203", weeks = listOf(2), category = CourseCategory.Exam),
            course("qa-w-elective", "影视鉴赏", 3, 11, 12, "报告厅"),
        ))
        container.scheduleEventRepository.upsert(ScheduleEvent("qa-w-event-1", "社团例会", "2026-09-16", "19:00", "20:00"))
        container.scheduleEventRepository.upsert(ScheduleEvent("qa-w-event-2", "实验报告提交", "2026-09-24", "12:00", "12:30"))
        writeTasks()
    }

    /** 装着真实组件时 App 启动会按组件内容重写一遍快照，所以截图前再写一次演示任务 */
    private fun writeTasks() {
        val zone = ZoneId.of("Asia/Shanghai")
        fun at(text: String) = LocalDateTime.parse(text).atZone(zone).toInstant().toEpochMilli()
        PendingTaskFeed.write(context, listOf(
            PendingTask("qa-component/1", "qa-component", "示例组件", "第二章课后习题", "作业", "高等数学", dueAtMillis = at("2026-09-15T23:59"), colorArgb = 0xFF2563EB),
            PendingTask("qa-component/2", "qa-component", "示例组件", "期中线上测验", "考试", "线性代数", startAtMillis = at("2026-09-18T11:35"), dueAtMillis = at("2026-09-18T13:10"), colorArgb = 0xFFDC2626),
            PendingTask("qa-component/3", "qa-component", "示例组件", "实验预习报告", "作业", "物理实验", dueAtMillis = at("2026-09-21T08:00"), colorArgb = 0xFF2563EB),
            PendingTask("qa-component/4", "qa-component", "示例组件", "上周的讨论帖", "作业", "近代史纲要", dueAtMillis = at("2026-09-12T23:59"), colorArgb = 0xFF2563EB),
        ))
    }

    private companion object {
        const val HOST_ID = 7301
    }
}
