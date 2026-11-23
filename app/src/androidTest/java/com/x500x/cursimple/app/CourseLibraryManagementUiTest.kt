package com.x500x.cursimple.app

import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.espresso.Espresso
import androidx.test.platform.app.InstrumentationRegistry
import com.x500x.cursimple.app.theme.ClassScheduleTheme
import com.x500x.cursimple.core.data.AppLanguage
import com.x500x.cursimple.core.data.AppLocale
import com.x500x.cursimple.core.data.PreferencesStoreSnapshot
import com.x500x.cursimple.core.data.ThemeAccent
import com.x500x.cursimple.core.data.ThemeMode
import com.x500x.cursimple.core.data.event.DataStoreScheduleEventRepository
import com.x500x.cursimple.core.data.event.ScheduleEventRepository
import com.x500x.cursimple.core.kernel.model.CourseItem
import com.x500x.cursimple.core.kernel.model.CourseTimeSlot
import com.x500x.cursimple.core.kernel.model.ScheduleEvent
import com.x500x.cursimple.core.kernel.model.ScheduleEventSource
import com.x500x.cursimple.feature.schedule.CourseLibraryRoute
import com.x500x.cursimple.feature.schedule.ScheduleViewModel
import com.x500x.cursimple.feature.schedule.ScheduleViewModelFactory
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate

@OptIn(ExperimentalTestApi::class)
class CourseLibraryManagementUiTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val container get() = (context.applicationContext as ClassScheduleApplication).appContainer
    private val alpha = course("alpha", "QA matching Alpha", 1)
    private val variant = course("variant", "QA matching Alpha", 2)
    private val beta = course("beta", "QA matching Beta", 3)
    private val outside = course("outside", "QA outside", 4)
    private val today = LocalDate.now()
    private val first = event("first", "QA Library first", today.plusDays(1))
    private val second = event("second", "QA Library second", today.plusDays(2))
    private val third = event("third", "QA Library third", today.plusDays(3))
    private val unmatched = event("unmatched", "QA unrelated", today.plusDays(2)).copy(startTime = "10:00")
    private val pluginEvent = event("plugin", "QA plugin event", today.plusDays(4)).copy(
        source = ScheduleEventSource("qa-plugin", "plugin"),
    )
    private val eventStore get() = DataStoreScheduleEventRepository(context, container.termProfileRepository)
    private var eventSnapshot: PreferencesStoreSnapshot? = null
    private var hostBacks = 0

    @After
    fun removeFixtures() {
        runBlocking {
            for (course in listOf(alpha, variant, beta, outside)) container.manualCourseRepository.removeCourse(course.id)
            eventSnapshot?.let { eventStore.restoreBackupSnapshot(it) }
        }
    }

    @Test
    fun groupedCoursesToggleSelectAllFilteredConfirmAndExitOnBack() {
        runBlocking {
            container.bootstrapJob.join()
            listOf(alpha, variant, beta, outside).forEach { container.manualCourseRepository.addCourse(it) }
        }
        showLibrary()
        val search = compose.onNode(hasSetTextAction())
        search.performTextInput("QA matching")
        Espresso.closeSoftKeyboard()

        compose.onNodeWithTag("course-library-variants-toggle").performClick()
        val childCard = compose.onAllNodesWithTag("course-library-variant").onLast()
        childCard.performTouchInput { down(center) }
        try {
            compose.waitUntilExactlyOneExists(hasText("1 selected"), timeoutMillis = 3_000)
        } finally {
            childCard.performTouchInput { up() }
        }
        compose.onNodeWithContentDescription("Exit selection").performClick()

        courseCard(alpha.title).performTouchInput { longClick(Offset(center.x, 20f)) }
        compose.onNodeWithText("2 selected").assertExists()
        courseCard(beta.title).performTouchInput { click() }
        compose.onNodeWithText("3 selected").assertExists()
        courseCard(beta.title).performTouchInput { click() }
        compose.onNodeWithText("2 selected").assertExists()
        Espresso.pressBack()
        compose.onNodeWithText("2 selected").assertDoesNotExist()
        assertEquals(0, hostBacks)

        courseCard(beta.title).performTouchInput { longClick(Offset(center.x, 20f)) }
        compose.onNodeWithText("Select all").performClick()
        compose.onNodeWithText("3 selected").assertExists()
        compose.onNodeWithText("Delete selected").performClick()
        compose.onNodeWithTag("library-cancel-delete").performClick()
        compose.onNodeWithText("3 selected").assertExists()
        assertEquals(4, storedFixtureCourseIds().size)
        compose.onNodeWithText("Delete selected").performClick()
        compose.onNodeWithTag("library-confirm-delete").performClick()
        compose.waitUntil(5_000) { storedFixtureCourseIds() == setOf(outside.id) }
    }

    @Test
    fun eventSourceFilterSupportsMultipleSelectionsAndClear() {
        runBlocking {
            container.bootstrapJob.join()
            eventSnapshot = eventStore.exportBackupSnapshot()
            listOf(first, pluginEvent).forEach { eventStore.upsert(it) }
        }
        showLibrary()
        compose.onNodeWithText("Events ", substring = true).performClick()
        val plugin = compose.onNodeWithTag("event-source-Plugin")
        val user = compose.onNodeWithTag("event-source-UserCreated")
        val all = compose.onNodeWithTag("event-source-all")
        val clear = compose.onNodeWithTag("event-source-clear")
        plugin.assertIsSelected()
        user.assertIsSelected()
        all.assertIsNotEnabled()
        clear.assert(hasContentDescription("Clear selected sources")).assertIsEnabled()

        plugin.performClick().assertIsNotSelected()
        eventCard(first.title).assertExists()
        eventCard(pluginEvent.title).assertDoesNotExist()

        clear.assertIsEnabled()
        clear.performClick()
        compose.waitForIdle()
        clear.assertIsNotEnabled()
        user.assertIsNotSelected()
        compose.onNodeWithText("No events match the selected sources").assertExists()

        plugin.performClick().assertIsSelected()
        eventCard(first.title).assertDoesNotExist()
        eventCard(pluginEvent.title).assertExists()

        compose.onNode(hasSetTextAction()).performTextInput(first.title)
        Espresso.closeSoftKeyboard()
        eventCard(first.title).assertDoesNotExist()
        eventCard(pluginEvent.title).assertDoesNotExist()
        all.performClick().assertIsNotEnabled()
        plugin.assertIsSelected()
        user.assertIsSelected()
        eventCard(first.title).assertExists()
        eventCard(pluginEvent.title).assertDoesNotExist()
    }

    @Test
    fun sourceFiltersOnlyAppearWithPluginEventsAndDoNotHideManualEventsAfterRemoval() {
        val events = MutableStateFlow(emptyList<ScheduleEvent>())
        showLibrary(events)
        compose.onNodeWithText("Events ", substring = true).performClick()
        assertNoSourceFilters()

        events.value = listOf(first, unmatched)
        compose.waitUntilExactlyOneExists(hasTestTag("event-library-card") and hasText(first.title))
        assertNoSourceFilters()

        events.value = listOf(first, unmatched, pluginEvent)
        compose.waitUntilExactlyOneExists(hasTestTag("event-source-Plugin"))
        compose.onNodeWithTag("event-source-UserCreated").performClick()
        eventCard(first.title).assertDoesNotExist()

        events.value = listOf(first, unmatched)
        compose.waitUntilExactlyOneExists(hasTestTag("event-library-card") and hasText(first.title))
        assertNoSourceFilters()
        compose.onNode(hasSetTextAction()).performTextInput(first.title)
        Espresso.closeSoftKeyboard()
        eventCard(first.title).assertIsDisplayed()
        eventCard(unmatched.title).assertDoesNotExist()
    }

    @Test
    fun eventSourceChoicesShareTheCourseFiltersHorizontalPillLayout() {
        showLibrary(MutableStateFlow(listOf(pluginEvent)))
        val courseFilter = compose.onNodeWithText("By weekday").fetchSemanticsNode().boundsInRoot
        compose.onNodeWithText("Events ", substring = true).performClick()

        val sourceRow = listOf("Plugin", "UserCreated", "all", "clear")
            .map { compose.onNodeWithTag("event-source-$it").fetchSemanticsNode().boundsInRoot }
        sourceRow.forEach { chip ->
            assertEquals(courseFilter.height, chip.height, 1f)
            assertEquals(courseFilter.top, chip.top, 1f)
        }
        sourceRow.zipWithNext().forEach { (left, right) -> check(right.left >= left.right) }
    }

    @Test
    fun eventTapHoldDragAndConfirmedBatchDeletion() {
        runBlocking {
            container.bootstrapJob.join()
            eventSnapshot = eventStore.exportBackupSnapshot()
            listOf(first, unmatched, second, third).forEach { container.scheduleEventRepository.upsert(it) }
        }
        showLibrary()
        compose.onNodeWithText("Events ", substring = true).performClick()
        compose.onNode(hasSetTextAction()).performTextInput("QA Library")
        Espresso.closeSoftKeyboard()

        eventCard(first.title).performTouchInput { click() }
        compose.onNodeWithTag("event-title-field").assertExists()
        Espresso.pressBack()
        eventCard(first.title).performTouchInput { down(center) }
        try {
            compose.waitUntilExactlyOneExists(hasText("1 selected"), timeoutMillis = 3_000)
        } finally {
            eventCard(first.title).performTouchInput { up() }
        }
        compose.onNodeWithText("1 selected").assertExists()
        compose.onNodeWithTag("event-title-field").assertDoesNotExist()
        eventCard(second.title).performTouchInput { click() }
        compose.onNodeWithText("2 selected").assertExists()
        compose.onNodeWithContentDescription("Exit selection").performClick()

        val distance = eventCard(second.title).fetchSemanticsNode().boundsInRoot.center.y -
            eventCard(first.title).fetchSemanticsNode().boundsInRoot.center.y
        eventCard(first.title).performTouchInput {
            down(center)
            advanceEventTime(viewConfiguration.longPressTimeoutMillis + 100)
            moveTo(center.copy(y = center.y + distance + 20f), delayMillis = 100)
            up()
        }
        compose.waitUntil(5_000) {
            storedFixtureEvents().map { it.id } == listOf(second.id, first.id, third.id)
        }
        assertEquals(listOf(second, first, third), storedFixtureEvents())
        assertEquals(true, runBlocking { eventStore.libraryFlow.first().hasCustomOrder })
        compose.onNodeWithText("1 selected").assertDoesNotExist()
        compose.onNodeWithTag("event-title-field").assertDoesNotExist()
        compose.onNodeWithText("Courses ", substring = true).performClick()
        compose.onNodeWithText("Events ", substring = true).performClick()
        compose.waitForIdle()
        compose.onAllNodesWithTag("event-library-card")[0].assertTextContains(second.title)

        eventCard(first.title).performTouchInput { longClick() }
        compose.onNodeWithText("Select all").performClick()
        compose.onNodeWithText("3 selected").assertExists()
        compose.onNodeWithText("Delete selected").performClick()
        compose.onNodeWithTag("library-cancel-delete").performClick()
        assertEquals(3, storedFixtureEvents().size)
        compose.onNodeWithText("Delete selected").performClick()
        compose.onNodeWithTag("library-confirm-delete").performClick()
        compose.waitUntil(5_000) { storedFixtureEvents().isEmpty() }
        assertEquals(unmatched, runBlocking { eventStore.eventsFlow.first().single { it.id == unmatched.id } })
    }

    private fun showLibrary(fixtureEvents: MutableStateFlow<List<ScheduleEvent>>? = null) {
        val fixtureContext = AppLocale.wrap(context, AppLanguage.English)
        val repository = fixtureEvents?.let { events ->
            object : ScheduleEventRepository {
                override val eventsFlow = events
                override suspend fun upsert(event: ScheduleEvent) = error("Read-only fixture")
                override suspend fun remove(eventId: String) = error("Read-only fixture")
            }
        } ?: container.scheduleEventRepository
        compose.runOnUiThread {
            val factory = ScheduleViewModelFactory(
                appContext = context,
                scheduleRepository = container.scheduleRepository,
                pluginManager = container.pluginManager,
                reminderCoordinator = container.reminderCoordinator,
                manualCourseRepository = container.manualCourseRepository,
                scheduleEventRepository = repository,
            )
            val model = ViewModelProvider(compose.activity, factory)["library-management-test", ScheduleViewModel::class.java]
            compose.activity.setContent {
                CompositionLocalProvider(
                    LocalContext provides fixtureContext,
                    LocalConfiguration provides fixtureContext.resources.configuration,
                ) {
                    ClassScheduleTheme(themeMode = ThemeMode.Light, themeAccent = ThemeAccent.Green) {
                        CourseLibraryRoute(viewModel = model)
                    }
                }
                BackHandler { hostBacks++ }
            }
        }
        compose.waitForIdle()
    }

    private fun courseCard(title: String) = compose.onNode(hasTestTag("course-library-card") and hasText(title))
    private fun eventCard(title: String) = compose.onNode(hasTestTag("event-library-card") and hasText(title))

    private fun assertNoSourceFilters() {
        listOf("Plugin", "UserCreated", "all", "clear").forEach { source ->
            compose.onNodeWithTag("event-source-$source").assertDoesNotExist()
        }
    }

    private fun storedFixtureCourseIds() = runBlocking {
        val ids = setOf(alpha.id, variant.id, beta.id, outside.id)
        container.manualCourseRepository.manualCoursesFlow.first().map { it.id }.filter { it in ids }.toSet()
    }

    private fun storedFixtureEvents() = runBlocking {
        val ids = setOf(first.id, second.id, third.id)
        container.scheduleEventRepository.eventsFlow.first().filter { it.id in ids }
    }

    private fun course(id: String, title: String, node: Int) =
        CourseItem("library-management-test-$id", title, time = CourseTimeSlot(1, node, node))

    private fun event(id: String, title: String, date: LocalDate) =
        ScheduleEvent("library-management-test-$id", title, date.toString(), "12:00", "13:00")
}
