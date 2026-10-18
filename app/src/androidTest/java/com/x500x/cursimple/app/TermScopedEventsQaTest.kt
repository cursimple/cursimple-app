package com.x500x.cursimple.app

import androidx.test.platform.app.InstrumentationRegistry
import com.x500x.cursimple.core.kernel.model.ScheduleEvent
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 事务按学期分区：切换学期后只看到当前学期的事务。 */
class TermScopedEventsQaTest {

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val container get() = (context.applicationContext as ClassScheduleApplication).appContainer
    private val terms get() = container.termProfileRepository
    private val events get() = container.scheduleEventRepository
    private val created = mutableListOf<String>()

    private suspend fun newTerm(name: String) = terms.createTerm(name, "2026-02-23").also { created += it.id }.id

    private suspend fun eventIds(): List<String> =
        events.eventsFlow.first().map { it.id }.filter { it.startsWith("qa-term-") }

    @After
    fun restore() = runBlocking {
        terms.termsFlow.first().firstOrNull { it.id !in created }?.let { terms.setActiveTerm(it.id) }
        events.eventsFlow.first().filter { it.id.startsWith("qa-term-") }.forEach { events.remove(it.id) }
        created.forEach { terms.deleteTerm(it) }
    }

    @Test
    fun switchingTermsSwapsTheEventList() = runBlocking {
        val first = newTerm("QA 学期甲")
        val second = newTerm("QA 学期乙")

        terms.setActiveTerm(first)
        events.upsert(ScheduleEvent("qa-term-a", "甲的事务", "2026-03-02", "12:00", "13:00"))

        terms.setActiveTerm(second)
        assertTrue("切到乙学期不该看到甲的事务", eventIds().isEmpty())
        events.upsert(ScheduleEvent("qa-term-b", "乙的事务", "2026-03-03", "12:00", "13:00"))
        assertEquals(listOf("qa-term-b"), eventIds())

        terms.setActiveTerm(first)
        assertEquals("切回甲学期应只看到甲的事务", listOf("qa-term-a"), eventIds())
    }

    @Test
    fun theSameEventIdCanLiveInTwoTerms() = runBlocking {
        val first = newTerm("QA 学期丙")
        val second = newTerm("QA 学期丁")

        terms.setActiveTerm(first)
        events.upsert(ScheduleEvent("qa-term-same", "丙的标题", "2026-03-02", "12:00", "13:00"))
        terms.setActiveTerm(second)
        events.upsert(ScheduleEvent("qa-term-same", "丁的标题", "2026-03-02", "14:00", "15:00"))

        assertEquals(listOf("丁的标题"), events.eventsFlow.first().filter { it.id == "qa-term-same" }.map { it.title })

        terms.setActiveTerm(first)
        assertEquals(listOf("丙的标题"), events.eventsFlow.first().filter { it.id == "qa-term-same" }.map { it.title })
    }
}
