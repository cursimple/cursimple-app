package com.x500x.cursimple.feature.schedule

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CourseLibraryAnimationTest {
    @Test
    fun libraryTabTransitionMovesForwardWhenSwitchingFromCoursesToEvents() {
        assertTrue(libraryTabMovesForward(LibraryTab.Courses, LibraryTab.Events))
        assertFalse(libraryTabMovesForward(LibraryTab.Events, LibraryTab.Courses))
    }
}
