package com.x500x.cursimple.feature.schedule

import com.x500x.cursimple.core.kernel.model.CourseItem
import com.x500x.cursimple.core.kernel.model.CourseTimeSlot
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class SharedFreeTimeTest {
    private val monday = LocalDate.of(2026, 9, 14)
    private val termStart = monday
    private fun course(id: String, day: Int, start: Int, end: Int) =
        CourseItem(id, id, weeks = (1..18).toList(), time = CourseTimeSlot(day, start, end))

    private fun runsOn(runs: List<FreeRun>, date: LocalDate) = runs.filter { it.date == date }.map { it.startNode to it.endNode }

    @Test
    fun aPeriodIsFreeOnlyWhenNeitherPersonHasAClass() {
        val mine = listOf(course("m", 1, 1, 2))
        val theirs = listOf(course("t", 1, 3, 4))
        val runs = sharedFreeRuns(mine, termStart, theirs, termStart, monday, periodCount = 6)
        // Monday: periods 1-4 are busy for one of them, so 5-6 are the shared free run.
        assertEquals(listOf(5 to 6), runsOn(runs, monday))
    }

    @Test
    fun overlappingClassesLeaveTheirPeriodsFreeForBoth() {
        val mine = listOf(course("m", 2, 1, 2))
        val theirs = listOf(course("t", 2, 4, 5))
        val runs = sharedFreeRuns(mine, termStart, theirs, termStart, monday, periodCount = 6)
        val tuesday = monday.plusDays(1)
        // Free: 3 on its own, and 6.
        assertEquals(listOf(3 to 3, 6 to 6), runsOn(runs, tuesday))
    }

    @Test
    fun aFreeWeekdayIsOneRunOfAllPeriods() {
        val runs = sharedFreeRuns(emptyList(), termStart, emptyList(), termStart, monday, periodCount = 4)
        assertEquals(listOf(1 to 4), runsOn(runs, monday.plusDays(6)))
        assertEquals(7, runs.size)
    }

    @Test
    fun theWeekIsAlwaysMondayBasedWhateverDayIsGiven() {
        val wednesday = monday.plusDays(2)
        val runs = sharedFreeRuns(emptyList(), termStart, emptyList(), termStart, wednesday, periodCount = 2)
        assertEquals(monday, runs.first().date)
        assertEquals(7, runs.map { it.date }.distinct().size)
    }

    @Test
    fun theirTermStartDecidesWhichWeekTheirClassesFallIn() {
        // Their course only runs in week 2 of their own term, which starts a week later than ours.
        val theirs = listOf(CourseItem("t", "t", weeks = listOf(1), time = CourseTimeSlot(1, 1, 2)))
        val theirStart = monday.plusDays(7)
        val runs = sharedFreeRuns(emptyList(), termStart, theirs, theirStart, monday, periodCount = 2)
        // In our week 1 they have no class yet, so periods 1-2 are free for both.
        assertEquals(listOf(1 to 2), runsOn(runs, monday))
    }

    @Test
    fun periodCountFallsBackWhenTheTimingProfileIsEmpty() {
        assertEquals(DEFAULT_PERIOD_COUNT, periodCountOf(null))
        assertEquals(DEFAULT_PERIOD_COUNT, periodCountOf(0))
        assertEquals(10, periodCountOf(10))
    }
}
