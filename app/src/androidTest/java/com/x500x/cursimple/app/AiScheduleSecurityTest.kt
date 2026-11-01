package com.x500x.cursimple.app

import androidx.test.platform.app.InstrumentationRegistry
import com.x500x.cursimple.app.util.AiScheduleCompat
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class AiScheduleSecurityTest {
    @Test fun parserPreservesJsonEscapesOnRealWebView() = runBlocking {
        val result = AiScheduleCompat.executeJsParser(
            InstrumentationRegistry.getInstrumentation().targetContext,
            """function scheduleHtmlParser(html) { return [{name: html, day: 1, sections: [1], weeks: [1]}]; }""",
            "课程\n\"quoted\"\\path",
        )
        assertTrue(result.exceptionOrNull()?.toString(), result.isSuccess)
        val course = requireNotNull(result.getOrThrow().schedule).dailySchedules.flatMap { it.courses }.single()
        assertEquals("课程\n\"quoted\"\\path", course.title)
    }

    @Test fun endlessParserIsStoppedByTimeout() = runBlocking {
        val result = AiScheduleCompat.executeJsParser(
            InstrumentationRegistry.getInstrumentation().targetContext,
            "function scheduleHtmlParser(html) { while (true) {} }",
            "",
        )
        assertTrue(result.exceptionOrNull() is java.util.concurrent.TimeoutException)
    }
}
