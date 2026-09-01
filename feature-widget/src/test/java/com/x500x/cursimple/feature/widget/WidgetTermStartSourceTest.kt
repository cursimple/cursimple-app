package com.x500x.cursimple.feature.widget

import com.x500x.cursimple.core.kernel.model.TermTimingProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test
import java.time.LocalDate

class WidgetTermStartSourceTest {
    private val profileDate: LocalDate = LocalDate.of(2026, 9, 7)
    private val timingDate: LocalDate = LocalDate.of(2026, 3, 2)
    private val preferenceDate: LocalDate = LocalDate.of(2025, 9, 1)

    @Test
    fun `active term profile wins over the other sources`() {
        assertEquals(
            profileDate,
            selectTermStartDate(
                activeTermStartIso = profileDate.toString(),
                timingProfileTermStartIso = timingDate.toString(),
                preferenceTermStartDate = preferenceDate,
            ),
        )
    }

    @Test
    fun `timing profile takes over when the term profile has no date`() {
        assertEquals(
            timingDate,
            selectTermStartDate(
                activeTermStartIso = null,
                timingProfileTermStartIso = timingDate.toString(),
                preferenceTermStartDate = preferenceDate,
            ),
        )
    }

    @Test
    fun `user preference is the last fallback`() {
        assertEquals(
            preferenceDate,
            selectTermStartDate(
                activeTermStartIso = null,
                timingProfileTermStartIso = null,
                preferenceTermStartDate = preferenceDate,
            ),
        )
    }

    @Test
    fun `unparsable dates fall through to the next source`() {
        assertEquals(
            timingDate,
            selectTermStartDate(
                activeTermStartIso = "",
                timingProfileTermStartIso = timingDate.toString(),
                preferenceTermStartDate = preferenceDate,
            ),
        )
        assertEquals(
            preferenceDate,
            selectTermStartDate(
                activeTermStartIso = "2026-13-40",
                timingProfileTermStartIso = "not-a-date",
                preferenceTermStartDate = preferenceDate,
            ),
        )
    }

    @Test
    fun `no source at all means no term start date`() {
        assertNull(
            selectTermStartDate(
                activeTermStartIso = null,
                timingProfileTermStartIso = null,
                preferenceTermStartDate = null,
            ),
        )
    }

    @Test
    fun `timing profile adopts the term start date resolved for widgets`() {
        val timingProfile = TermTimingProfile(
            termStartDate = timingDate.toString(),
            slotTimes = emptyList(),
        )
        val resolved = selectTermStartDate(
            activeTermStartIso = profileDate.toString(),
            timingProfileTermStartIso = timingProfile.termStartDate,
            preferenceTermStartDate = preferenceDate,
        )

        assertEquals(
            profileDate.toString(),
            timingProfile.withTermStartDate(resolved).termStartDate,
        )
    }

    @Test
    fun `timing profile keeps its own date when no other source resolves`() {
        val timingProfile = TermTimingProfile(
            termStartDate = timingDate.toString(),
            slotTimes = emptyList(),
        )
        val resolved = selectTermStartDate(
            activeTermStartIso = null,
            timingProfileTermStartIso = timingProfile.termStartDate,
            preferenceTermStartDate = null,
        )

        assertSame(timingProfile, timingProfile.withTermStartDate(resolved))
        assertSame(timingProfile, timingProfile.withTermStartDate(null))
    }
}
