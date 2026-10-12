package com.x500x.cursimple.app.notice

import org.junit.Assert.*
import org.junit.Test

class NoticeProgressTest {
    @Test fun progressCoversPreparationAndClassWithoutExceedingBounds() {
        val start = 1_000_000L
        val end = start + 60_000
        assertEquals(0, noticeProgress(start, end, 1, start - 70_000))
        assertEquals(0, noticeProgress(start, end, 1, start - 60_000))
        assertEquals(500, noticeProgress(start, end, 1, start))
        assertEquals(750, noticeProgress(start, end, 1, start + 30_000))
        assertEquals(1_000, noticeProgress(start, end, 1, end + 60_000))
    }

    @Test fun missingOrInvalidCourseTimesCannotProduceFakeProgress() {
        assertEquals(0, noticeProgress(0, 120_000, 15, 60_000))
        assertEquals(0, noticeProgress(100_000, 90_000, 15, 60_000))
        assertEquals(500, noticeProgress(100_000, 160_000, 0, 100_000))
    }

    @Test fun nativeComparisonsRequireTheActualPlatformApi() {
        assertTrue(NoticeLiveStyle.Automatic.supported(24))
        listOf(NoticeLiveStyle.NativeText, NoticeLiveStyle.NativeProgress).forEach {
            assertFalse(it.supported(35))
            assertTrue(it.supported(36))
        }
    }
}
