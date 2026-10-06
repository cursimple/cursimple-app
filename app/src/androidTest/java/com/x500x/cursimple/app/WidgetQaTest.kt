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
import android.widget.TextView
import android.graphics.Rect
import androidx.test.platform.app.InstrumentationRegistry
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.graphics.asAndroidBitmap
import com.x500x.cursimple.core.data.AppLanguage
import com.x500x.cursimple.core.data.AppLocale
import com.x500x.cursimple.core.data.ThemeMode
import com.x500x.cursimple.core.kernel.model.ClassSlotTime
import com.x500x.cursimple.core.kernel.model.CourseCategory
import com.x500x.cursimple.core.kernel.model.CourseItem
import com.x500x.cursimple.core.kernel.model.CourseTimeSlot
import com.x500x.cursimple.core.kernel.model.ScheduleEvent
import com.x500x.cursimple.core.kernel.model.TermTimingProfile
import com.x500x.cursimple.feature.widget.CalendarWidgetReceiver
import com.x500x.cursimple.feature.widget.MemoTodoWidgetReceiver
import com.x500x.cursimple.core.kernel.model.MemoNote
import com.x500x.cursimple.feature.widget.ComponentWidgetAvailability
import com.x500x.cursimple.feature.widget.WidgetCatalog
import com.x500x.cursimple.feature.widget.WidgetGuardHooks
import com.x500x.cursimple.feature.widget.PendingTaskWidgetReceiver
import com.x500x.cursimple.feature.widget.WidgetDeepLinks
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertNotNull
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.Rule
import java.io.File
import java.time.LocalDate
import java.time.LocalDateTime
import com.x500x.cursimple.feature.widget.R as WidgetR

/**
 * Requires widgetQa=true and appwidget grantbind on a dedicated QA emulator. Exercises
 * providers, RemoteViews and click dispatch through AppWidgetHost.
 */
class WidgetQaTest {
    @get:Rule val compose = createEmptyComposeRule()
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

        val (todayId, today) = bind(com.x500x.cursimple.feature.widget.ScheduleGlanceWidgetReceiver::class.java, 330, 200)
        refresh { com.x500x.cursimple.feature.widget.ScheduleGlanceWidgetReceiver.updateWidgets(context, intArrayOf(todayId)) }
        capture(today, 330, 200, "widget-today.png")

        val (smallId, small) = bind(CalendarWidgetReceiver::class.java, 250, 190)
        refresh { CalendarWidgetReceiver.updateWidgets(context, intArrayOf(smallId)) }
        capture(small, 250, 190, "widget-calendar-small.png")
        val (tallId, tall) = bind(CalendarWidgetReceiver::class.java, 330, 600)
        refresh { CalendarWidgetReceiver.updateWidgets(context, intArrayOf(tallId)) }
        capture(tall, 330, 600, "widget-calendar-four-column-tall.png")

        val monitor = instrumentation.addMonitor(MainActivity::class.java.name, null, false)
        click(view, WidgetR.id.calendar_hit_0_3, waitMs = 0)
        val activity = instrumentation.waitForMonitorWithTimeout(monitor, 10_000)
        assertNotNull("点日期没有打开应用", activity)
        assertEquals("2026-09-17", activity!!.intent.getStringExtra(WidgetDeepLinks.EXTRA_OPEN_SCHEDULE_DATE))
        Thread.sleep(2500)
        captureActivity(activity, "widget-calendar-open-day.png")
        activity.finishAndWait()
    }

    @Test fun guardAlarmAlsoChecksComponentSync() {
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

    @Test fun memoTodosWorkWithoutComponentsAndOpenTheirSourceNote() = runBlocking {
        ComponentWidgetAvailability.update(context, false)
        assertTrue(WidgetCatalog.pickerEntries(context).any { it.id == "memo" && !it.fromComponents })
        val repo = com.x500x.cursimple.core.data.memo.DataStoreMemoRepository(context)
        val previous = repo.notesFlow.first()
        previous.forEach { repo.remove(it.id) }
        try {
            repo.upsert(MemoNote("memo-widget-qa", courseTitle = "数据结构", title = "第三次实验",
                body = "- [x] 整理代码\n- [ ] 整理实验报告\n- [ ] 提交附件", pinned = true))
            repo.upsert(MemoNote("memo-widget-complete", title = "已完成", body = "- [x] 完成"))
            val (id, view) = bind(MemoTodoWidgetReceiver::class.java, 330, 230)
            refresh { MemoTodoWidgetReceiver.updateWidgets(context, intArrayOf(id)) }
            capture(view, 330, 230, "widget-memo-todos.png")
            val (smallId, small) = bind(MemoTodoWidgetReceiver::class.java, 330, 148)
            refresh { MemoTodoWidgetReceiver.updateWidgets(context, intArrayOf(smallId)) }
            capture(small, 330, 148, "widget-memo-compact.png")
            instrumentation.runOnMainSync {
                val list = small.findViewById<ListView>(WidgetR.id.memo_list)
                assertTrue("Compact checklist rendered no rows", list.childCount > 0)
                assertEquals(list.adapter.count, list.childCount)
                if (context.resources.configuration.fontScale <= 1.05f) assertEquals(2, list.childCount)
                assertTrue("Compact checklist clipped its final row", list.getChildAt(list.childCount - 1).bottom <= list.height)
            }
            // Mount widget views without unrelated Activity startup work.
            if (InstrumentationRegistry.getArguments().getString("memoLayoutOnly") == "true") return@runBlocking
            val monitor = instrumentation.addMonitor(MainActivity::class.java.name, null, false)
            instrumentation.runOnMainSync {
                val list = view.findViewById<ListView>(WidgetR.id.memo_list)
                assertEquals(2, list.adapter.count)
                val row = list.getChildAt(0)
                assertNotNull(row)
                list.performItemClick(row, 0, list.adapter.getItemId(0))
            }
            val activity = instrumentation.waitForMonitorWithTimeout(monitor, 10_000)
            assertNotNull(activity)
            assertEquals("memo-widget-qa", activity!!.intent.getStringExtra(WidgetDeepLinks.EXTRA_OPEN_MEMO))
            val editorTitle = AppLocale.wrap(context).getString(com.x500x.cursimple.feature.schedule.R.string.memo_editor_edit)
            compose.waitUntil(30_000) { compose.onAllNodesWithText(editorTitle).fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty() }
            compose.onNodeWithText(editorTitle).assertIsDisplayed()
            saveBitmap(compose.onNode(isRoot() and hasAnyDescendant(hasText(editorTitle)))
                .captureToImage().asAndroidBitmap(), "widget-memo-open-note.png")
            activity.finishAndWait()
        } finally {
            repo.remove("memo-widget-qa")
            repo.remove("memo-widget-complete")
            previous.forEach { repo.upsert(it) }
        }
    }

    @Test fun componentWidgetsOnlyAppearWithComponents() {
        val pm = context.packageManager
        val provider = ComponentName(context, PendingTaskWidgetReceiver::class.java)
        ComponentWidgetAvailability.update(context, false)
        assertEquals(PackageManager.COMPONENT_ENABLED_STATE_DISABLED, pm.getComponentEnabledSetting(provider))
        assertFalse(WidgetCatalog.pickerEntries(context).any { it.fromComponents })
        assertTrue(WidgetCatalog.pickerEntries(context).any { it.id == "calendar" })
        assertFalse(WidgetCatalog.pickerEntries(context).any { it.fromComponents })
        // A declaration, not merely an enabled extension, creates a picker entry.
    }

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
        preferences.setDebugForcedDateTime(LocalDateTime.of(2026, 9, 19, 10, 0))
        shoot("weekend")
        preferences.setHolidayCalendarBuiltInEnabled(true)
        preferences.setDebugForcedDateTime(LocalDateTime.of(2026, 10, 2, 10, 0))
        shoot("national")
        preferences.setHolidayCalendarBuiltInEnabled(false)
        preferences.setAppTimeZoneId("Asia/Shanghai")
        preferences.setDebugForcedDateTime(LocalDateTime.of(2027, 1, 6, 10, 0))
        refresh { CalendarWidgetReceiver.updateWidgets(context, intArrayOf(calendarId)) }
        capture(calendar, 330, 360, "widget-mood-calendar-empty-week.png")
    }

    @Test fun everyWidgetRendersAcrossSizesAndEmptyStates() = runBlocking {
        Thread.sleep(4000)
        val app = context.applicationContext as ClassScheduleApplication
        val preferences = app.appContainer.userPreferencesRepository
        val widgetPreferences = app.appContainer.widgetPreferencesRepository
        val notes = app.appContainer.memoRepository
        val oldNotes = notes.notesFlow.first()
        oldNotes.forEach { notes.remove(it.id) }
        notes.upsert(MemoNote("matrix-note", courseTitle = "程序设计与数据结构", title = "实验材料与复习清单",
            body = "- [ ] 整理带有较长标题的实验报告并补充代码说明\n- [ ] Review algorithm notes and submit supporting files\n- [x] 完成预习", pinned = true))
        val reminderRepo = app.appContainer.reminderRepository
        val oldRules = reminderRepo.getReminderRules()
        reminderRepo.saveReminderRule(com.x500x.cursimple.core.reminder.model.ReminderRule(
            ruleId = "widget-matrix", pluginId = "qa", scopeType = com.x500x.cursimple.core.reminder.model.ReminderScopeType.FirstCourseOfPeriod,
            displayName = "课程提醒", advanceMinutes = 15,
            firstCourseCandidate = com.x500x.cursimple.core.reminder.model.FirstCourseCandidateScope(
                daysOfWeek = listOf(2), nodeRange = com.x500x.cursimple.core.reminder.model.ReminderNodeRange(5, 7)),
            actions = listOf(com.x500x.cursimple.core.reminder.model.ReminderAction(
                type = com.x500x.cursimple.core.reminder.model.ReminderActionType.RemindFirstCandidate)),
            createdAt = "2026-09-15T00:00:00Z", updatedAt = "2026-09-15T00:00:00Z"))
        val issues = mutableListOf<String>()
        val profile = InstrumentationRegistry.getArguments().getString("widgetQaProfile", "default")
            .replace(Regex("[^A-Za-z0-9_-]"), "_")
        val summary = org.json.JSONArray()
        val dimensions = mapOf(
            "today" to listOf(180 to 110, 330 to 230, 420 to 280),
            "next" to listOf(180 to 110, 330 to 180, 420 to 240),
            "reminder" to listOf(180 to 110, 330 to 200, 420 to 260),
            "calendar" to listOf(180 to 140, 330 to 360, 330 to 600),
            "memo" to listOf(180 to 110, 330 to 148, 330 to 230),
        )
        suspend fun update(kind: String, id: Int) {
            when (kind) {
                "today" -> com.x500x.cursimple.feature.widget.ScheduleGlanceWidgetReceiver.updateWidgets(context, intArrayOf(id))
                "next" -> com.x500x.cursimple.feature.widget.NextCourseGlanceWidgetReceiver.updateWidgets(context, intArrayOf(id))
                "reminder" -> com.x500x.cursimple.feature.widget.ReminderGlanceWidgetReceiver.updateWidgets(context, intArrayOf(id))
                "calendar" -> CalendarWidgetReceiver.updateWidgets(context, intArrayOf(id))
                "memo" -> MemoTodoWidgetReceiver.updateWidgets(context, intArrayOf(id))
            }
        }
        fun checkLayout(view: AppWidgetHostView, name: String, strictList: Boolean) {
            var textCount = 0
            instrumentation.runOnMainSync {
                fun inspect(node: View) {
                    if (node.visibility != View.VISIBLE) return
                    if (node is TextView && node.text.isNotEmpty() && node.width > 0 && node.height > 0) {
                        textCount++
                        val layout = node.layout
                        val lines = minOf(layout?.lineCount ?: 0, node.maxLines)
                        if (layout != null && lines > 0 && layout.getLineBottom(lines - 1) > node.height - node.totalPaddingTop - node.totalPaddingBottom + 2) {
                            issues += "$name: clipped text ${node.resources.getResourceEntryName(node.id)} (${node.text.take(24)}) height=${node.height} padding=${node.totalPaddingTop + node.totalPaddingBottom} line=${layout.getLineBottom(lines - 1)}"
                        }
                    }
                    if (strictList && node is ListView && node.childCount > 0 && node.getChildAt(node.childCount - 1).bottom > node.height + 2) {
                        issues += "$name: partial final row"
                    }
                    if (node is ViewGroup) for (i in 0 until node.childCount) inspect(node.getChildAt(i))
                }
                inspect(view)
            }
            assertTrue("Widget has no rendered text: $name", textCount > 0)
            summary.put(org.json.JSONObject().put("name", name).put("textViews", textCount))
        }
        try {
            for (entry in WidgetCatalog.entries(context).filterNot { it.fromComponents }) {
                for ((index, size) in dimensions.getValue(entry.id).withIndex()) {
                    val (width, height) = size
                    val (id, view) = bind(Class.forName(entry.provider.className), width, height)
                    refresh { update(entry.id, id) }
                    val name = "matrix-$profile-${entry.id}-$width-$height"
                    capture(view, width, height, "$name.png")
                    checkLayout(view, name, entry.id in setOf("memo", "reminder"))
                    if (entry.id == "reminder") instrumentation.runOnMainSync {
                        assertTrue("Reminder fixture did not produce rows", view.findViewById<ListView>(WidgetR.id.reminder_list).adapter?.count?.let { it > 0 } == true)
                    }
                    if (entry.id == "calendar") {
                        click(view, WidgetR.id.calendar_mode)
                        capture(view, width, height, "$name-month.png")
                        checkLayout(view, "$name-month", false)
                    }
                    if (index == dimensions.getValue(entry.id).lastIndex) {
                        if (entry.id == "memo") notes.remove("matrix-note")
                        if (entry.id == "reminder") reminderRepo.removeReminderRule("widget-matrix")
                        if (entry.id in setOf("today", "next", "calendar")) {
                            app.appContainer.manualCourseRepository.replaceAll(emptyList())
                            app.appContainer.scheduleEventRepository.remove("qa-w-event-1")
                            app.appContainer.scheduleEventRepository.remove("qa-w-event-2")
                        }
                        refresh { update(entry.id, id) }
                        capture(view, width, height, "$name-empty.png")
                        checkLayout(view, "$name-empty", false)
                        if (entry.id in setOf("today", "next", "calendar")) seed()
                    }
                }
            }
            File(context.filesDir, "matrix-$profile.json").writeText(summary.toString(2))
            File(context.filesDir, "matrix-$profile-issues.txt").writeText(issues.joinToString("\n"))
            assertTrue(issues.joinToString("\n"), issues.isEmpty())
        } finally {
            notes.remove("matrix-note")
            oldNotes.forEach { notes.upsert(it) }
            reminderRepo.removeReminderRule("widget-matrix")
            oldRules.forEach { reminderRepo.saveReminderRule(it) }
            widgetPreferences.followAppThemeAccent()
        }
    }

    @Test fun componentOwnedWidgetsInstallRenderNavigateAndDisappear() = runBlocking {
        val app = context.applicationContext as ClassScheduleApplication
        val manager = app.appContainer.pluginManager
        val zip = File(context.filesDir, "qa-component.zip")
        assumeTrue("Push a component ZIP to files/qa-component.zip", zip.isFile)
        val installed = manager.installPackage(zip.readBytes(), com.x500x.cursimple.core.plugin.install.PluginInstallSource.Local)
        assertTrue(installed.toString(), installed is com.x500x.cursimple.core.plugin.install.PluginInstallResult.Success)
        val record = (installed as com.x500x.cursimple.core.plugin.install.PluginInstallResult.Success).record
        app.appContainer.userPreferencesRepository.setPluginEnabled(record.installKey, true)
        val widgets = manager.loadExtensionPackage(record).first.extension!!.widgets
        assertTrue(widgets.isNotEmpty())
        val store = com.x500x.cursimple.feature.plugin.extension.ExtensionStore.get(context)
        store.update(record.pluginId) { it.copy(
            loginState = com.x500x.cursimple.feature.plugin.extension.ExtensionLoginState.Expired,
            items = listOf(
                com.x500x.cursimple.feature.plugin.extension.ExtensionFeedItem("widget-task-1", "homework", "整理实验报告", course = "程序设计", dueAt = com.x500x.cursimple.core.kernel.time.BeijingTime.nowMillis(com.x500x.cursimple.core.kernel.time.BeijingTime.zone) + 3_600_000),
                com.x500x.cursimple.feature.plugin.extension.ExtensionFeedItem("widget-task-2", "exam", "章节测验", course = "高等数学"),
                com.x500x.cursimple.feature.plugin.extension.ExtensionFeedItem("widget-read", "announcement", "已读公告", done = true),
            )) }
        try {
            awaitWidgetCondition { com.x500x.cursimple.core.data.widget.ComponentWidgetRegistry.read(context).any { it.componentId == record.pluginId } }
            val entry = WidgetCatalog.pickerEntries(context).first { it.componentWidgetKey == "${record.pluginId}/${widgets.first().id}" }
            assertEquals(widgets.first().title, entry.title)
            awaitProvider(PendingTaskWidgetReceiver::class.java)
            var last: AppWidgetHostView? = null
            val profile = InstrumentationRegistry.getArguments().getString("widgetQaProfile", "default").replace(Regex("[^A-Za-z0-9_-]"), "_")
            for ((width, height) in listOf(180 to 110, 330 to 148, 330 to 230)) {
                val (id, view) = bind(PendingTaskWidgetReceiver::class.java, width, height)
                com.x500x.cursimple.core.data.widget.ComponentWidgetBindings.set(context, id, requireNotNull(entry.componentWidgetKey))
                refresh { com.x500x.cursimple.feature.widget.ComponentWidgetReceiver.updateWidgets(context, intArrayOf(id)) }
                capture(view, width, height, "widget-owned-$profile-$width-$height.png")
                instrumentation.runOnMainSync {
                    assertEquals(View.GONE, view.findViewById<View>(WidgetR.id.component_widget_error).visibility)
                    val drawable = view.findViewById<android.widget.ImageView>(WidgetR.id.component_widget_image).drawable
                    assertNotNull("Owned HTML produced no image", drawable)
                    val pixels = (drawable as android.graphics.drawable.BitmapDrawable).bitmap
                    val colors = mutableSetOf<Int>()
                    for (x in 0 until pixels.width step 8) for (y in 0 until pixels.height step 8) colors += pixels.getPixel(x, y)
                    assertTrue("Owned widget image is blank", colors.size > 8 && colors.any { android.graphics.Color.alpha(it) > 0 })
                }
                last = view
            }
            val monitor = instrumentation.addMonitor(MainActivity::class.java.name, null, false)
            instrumentation.runOnMainSync {
                val regions = requireNotNull(last).findViewById<ViewGroup>(WidgetR.id.component_widget_hits)
                assertTrue("Component supplied no clickable regions", regions.childCount > 0)
                regions.getChildAt(0).findViewById<View>(WidgetR.id.component_widget_hit_target).performClick()
            }
            val activity = instrumentation.waitForMonitorWithTimeout(monitor, 10_000)
            assertNotNull(activity)
            assertEquals(record.pluginId, activity!!.intent.getStringExtra(WidgetDeepLinks.EXTRA_OPEN_COMPONENT_PAGE))
            activity.finishAndWait()
        } finally {
            manager.removePlugin(record.installKey)
            awaitWidgetCondition { WidgetCatalog.pickerEntries(context).none { it.componentWidgetKey?.startsWith(record.pluginId + "/") == true } }
            assertTrue("Uninstalled component widget remained in picker", WidgetCatalog.pickerEntries(context).none { it.componentWidgetKey?.startsWith(record.pluginId + "/") == true })
        }
    }

    @Test fun independentWidgetsKeepTheirOwnersAcrossDisableUpdateAndRemoval() = runBlocking {
        val container = (context.applicationContext as ClassScheduleApplication).appContainer
        val registry = com.x500x.cursimple.core.data.widget.ComponentWidgetRegistry
        val bindings = com.x500x.cursimple.core.data.widget.ComponentWidgetBindings
        val manager = container.pluginManager
        val installed = mutableListOf<com.x500x.cursimple.core.plugin.install.InstalledPluginRecord>()
        suspend fun install(id: String, color: String, hasWidget: Boolean = true, version: Int = 1) {
            val result = manager.installPackage(widgetFixture(id, color, hasWidget, version),
                com.x500x.cursimple.core.plugin.install.PluginInstallSource.Local)
            assertTrue(result.toString(), result is com.x500x.cursimple.core.plugin.install.PluginInstallResult.Success)
            val record = (result as com.x500x.cursimple.core.plugin.install.PluginInstallResult.Success).record
            installed.removeAll { it.pluginId == id }
            installed += record
            container.userPreferencesRepository.setPluginEnabled(record.installKey, true)
        }
        try {
            install("qa.no-widget", "#ff0000", hasWidget = false)
            install("qa.board", "#b82b8a")
            install("qa.weather", "#0b7357")
            awaitWidgetCondition { registry.read(context).size == 2 }
            assertEquals(setOf("qa.board", "qa.weather"), registry.read(context).map { it.componentId }.toSet())
            assertFalse(WidgetCatalog.pickerEntries(context).any { it.componentWidgetKey?.startsWith("qa.no-widget/") == true })
            awaitProvider(PendingTaskWidgetReceiver::class.java)
            val (id, view) = bind(PendingTaskWidgetReceiver::class.java, 330, 148)
            fun assertColor(hex: String) = instrumentation.runOnMainSync {
                assertEquals(View.GONE, view.findViewById<View>(WidgetR.id.component_widget_error).visibility)
                val bitmap = (view.findViewById<android.widget.ImageView>(WidgetR.id.component_widget_image).drawable as android.graphics.drawable.BitmapDrawable).bitmap
                assertEquals("Pixels came from the wrong owner", android.graphics.Color.parseColor(hex), bitmap.getPixel(bitmap.width / 2, bitmap.height / 2))
            }
            bindings.set(context, id, "qa.board/surface")
            refresh { com.x500x.cursimple.feature.widget.ComponentWidgetReceiver.updateWidgets(context, intArrayOf(id)) }
            assertColor("#b82b8a")
            capture(view, 330, 148, "widget-independent-board.png")
            val board = installed.first { it.pluginId == "qa.board" }
            container.userPreferencesRepository.setPluginEnabled(board.installKey, false)
            awaitWidgetCondition { registry.read(context).none { it.componentId == "qa.board" } }
            refresh { com.x500x.cursimple.feature.widget.ComponentWidgetReceiver.updateWidgets(context, intArrayOf(id)) }
            instrumentation.runOnMainSync {
                assertEquals(View.VISIBLE, view.findViewById<View>(WidgetR.id.component_widget_error).visibility)
                assertEquals(0, view.findViewById<ViewGroup>(WidgetR.id.component_widget_hits).childCount)
            }
            assertEquals("qa.board/surface", bindings.get(context, id))
            container.userPreferencesRepository.setPluginEnabled(board.installKey, true)
            awaitWidgetCondition { registry.read(context).any { it.componentId == "qa.board" } }
            refresh { com.x500x.cursimple.feature.widget.ComponentWidgetReceiver.updateWidgets(context, intArrayOf(id)) }
            assertColor("#b82b8a")
            bindings.set(context, id, "qa.weather/surface")
            refresh { com.x500x.cursimple.feature.widget.ComponentWidgetReceiver.updateWidgets(context, intArrayOf(id)) }
            assertColor("#0b7357")
            capture(view, 330, 148, "widget-independent-weather.png")
            install("qa.weather", "#152aba", version = 2)
            awaitWidgetCondition { registry.read(context).any { it.componentId == "qa.weather" && it.revision == installed.last().packageRevision } }
            refresh { com.x500x.cursimple.feature.widget.ComponentWidgetReceiver.updateWidgets(context, intArrayOf(id)) }
            assertColor("#152aba")
            install("qa.weather", "#152aba", hasWidget = false, version = 3)
            awaitWidgetCondition { registry.read(context).none { it.componentId == "qa.weather" } }
            refresh { com.x500x.cursimple.feature.widget.ComponentWidgetReceiver.updateWidgets(context, intArrayOf(id)) }
            instrumentation.runOnMainSync { assertEquals(View.VISIBLE, view.findViewById<View>(WidgetR.id.component_widget_error).visibility) }
            assertEquals("qa.weather/surface", bindings.get(context, id))
        } finally {
            installed.forEach { manager.removePlugin(it.installKey) }
            awaitWidgetCondition { registry.read(context).isEmpty() }
        }
    }

    private fun awaitWidgetCondition(condition: () -> Boolean) {
        val until = android.os.SystemClock.uptimeMillis() + 10_000
        while (!condition() && android.os.SystemClock.uptimeMillis() < until) Thread.sleep(100)
        assertTrue("Widget lifecycle did not settle", condition())
    }

    private fun widgetFixture(id: String, color: String, hasWidget: Boolean, version: Int): ByteArray {
        val widgets = if (hasWidget) """{"id":"surface","title":"$id dashboard","entry":"ui/widget.html"}""" else ""
        val files = linkedMapOf("manifest.json" to """{
            "id":"$id","name":"$id","version":"1.0.$version","versionCode":$version,
            "apiVersion":9,"kind":"extension","entry":"main.js","allowedHosts":["example.invalid"],
            "extension":{"loginUrl":"https://example.invalid/","runUrl":"https://example.invalid/","widgets":[$widgets],"feedTypes":[{"id":"task","label":"Task","kind":"task"}]}}
        """.trimIndent(), "main.js" to "export async function sync() { return {}; }")
        if (hasWidget) files["ui/widget.html"] = """<!doctype html><html><head>
            <meta name="viewport" content="width=device-width, initial-scale=1">
            <style>html,body{margin:0;width:100%;height:100%;background:$color;color:white;font:20px sans-serif}</style>
            </head><body>$id owns this view<script>
            CurSimpleWidget.ready([{x:0,y:0,width:CurSimpleWidget.state.context.width,height:CurSimpleWidget.state.context.height,action:'settings'}]);
            </script></body></html>""".trimIndent()
        val sums = org.json.JSONObject()
        files.forEach { (path, content) -> sums.put(path, java.security.MessageDigest.getInstance("SHA-256").digest(content.toByteArray()).joinToString("") { "%02x".format(it) }) }
        files["checksums.json"] = org.json.JSONObject().put("algorithm", "SHA-256").put("files", sums).toString()
        val output = java.io.ByteArrayOutputStream()
        java.util.zip.ZipOutputStream(output).use { zip -> files.forEach { (path, content) ->
            zip.putNextEntry(java.util.zip.ZipEntry(path)); zip.write(content.toByteArray()); zip.closeEntry()
        } }
        return output.toByteArray()
    }

    private fun bind(provider: Class<*>, widthDp: Int, heightDp: Int): Pair<Int, AppWidgetHostView> {
        val manager = AppWidgetManager.getInstance(context)
        val id = host.allocateAppWidgetId()
        boundIds += id
        val options = Bundle().apply {
            putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, widthDp)
            putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, widthDp)
            putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, heightDp)
            putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, heightDp)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                putParcelableArrayList(AppWidgetManager.OPTION_APPWIDGET_SIZES,
                    arrayListOf(android.util.SizeF(widthDp.toFloat(), heightDp.toFloat())))
            }
        }
        check(manager.bindAppWidgetIdIfAllowed(id, ComponentName(context, provider), options)) {
            "绑定失败：先执行 adb shell appwidget grantbind --package ${context.packageName}"
        }
        lateinit var view: AppWidgetHostView
        instrumentation.runOnMainSync {
            view = host.createView(context, id, manager.getAppWidgetInfo(id))
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                view.updateAppWidgetSize(Bundle(), listOf(android.util.SizeF(widthDp.toFloat(), heightDp.toFloat())))
            } else view.updateAppWidgetSize(Bundle(), widthDp, heightDp, widthDp, heightDp)
        }
        return id to view
    }

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

    /** Capture only the test window without competing for UiAutomation. */
    private fun captureActivity(activity: Activity, name: String) {
        val ready = java.util.concurrent.CountDownLatch(1)
        var result = android.view.PixelCopy.ERROR_UNKNOWN
        lateinit var bitmap: Bitmap
        instrumentation.runOnMainSync {
            val decor = activity.window.decorView
            bitmap = Bitmap.createBitmap(decor.width, decor.height, Bitmap.Config.ARGB_8888)
            android.view.PixelCopy.request(activity.window, bitmap, { status -> result = status; ready.countDown() },
                android.os.Handler(android.os.Looper.getMainLooper()))
        }
        assertTrue(ready.await(5, java.util.concurrent.TimeUnit.SECONDS))
        assertEquals(android.view.PixelCopy.SUCCESS, result)
        saveBitmap(bitmap, name)
    }

    private fun Activity.finishAndWait() {
        finish()
        Thread.sleep(800)
    }

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
        preferences.setPluginUpdateOptions(false, true, 6)
        preferences.setAlarmKeepAliveEnabled(false)
        preferences.setClassNoticeEnabled(false)
        preferences.setThemeMode(if (InstrumentationRegistry.getArguments().getString("widgetQaTheme") == "dark") ThemeMode.Dark else ThemeMode.Light)
        preferences.setDebugForcedDateTime(LocalDateTime.of(2026, 9, 15, 10, 0))
        preferences.resetScheduleAppearanceAndDisplay()
        preferences.setHolidayCalendarBuiltInEnabled(false)
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
            course("qa-w-program", "程序设计", 2, 5, 5, "机房 3"),
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
    }

    private companion object {
        const val HOST_ID = 7301
    }
}
