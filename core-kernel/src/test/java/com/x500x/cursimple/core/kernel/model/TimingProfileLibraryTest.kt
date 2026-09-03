package com.x500x.cursimple.core.kernel.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TimingProfileLibraryTest {

    private fun slot(node: Int) = ClassSlotTime(
        startNode = node,
        endNode = node,
        startTime = "08:00",
        endTime = "08:45",
    )

    private fun entry(id: String, node: Int = 1) = TimingProfileEntry(id = id, slotTimes = listOf(slot(node)))

    @Test
    fun `an empty library has no active profile`() {
        assertNull(TimingProfileLibrary().active)
    }

    @Test
    fun `a dangling active id falls back to the first profile`() {
        val library = TimingProfileLibrary(listOf(entry("a"), entry("b")), activeId = "missing")

        assertEquals("a", library.active?.id)
    }

    @Test
    fun `legacy data becomes a single profile library`() {
        val legacy = TermTimingProfile(
            termStartDate = "2026-09-07",
            slotTimes = listOf(slot(1), slot(2)),
            timezone = "Asia/Shanghai",
        )

        val library = legacyTimingProfileLibrary(legacy, manuallyEdited = true)
        val active = library.active

        assertEquals(1, library.profiles.size)
        assertEquals(DEFAULT_TIMING_PROFILE_ID, active?.id)
        assertEquals(DEFAULT_TIMING_PROFILE_NAME_KEY, active?.nameKey)
        assertEquals(2, active?.slotTimes?.size)
        assertEquals("Asia/Shanghai", active?.timezone)
        assertTrue(active?.manuallyEdited == true)
    }

    @Test
    fun `no legacy data means an empty library`() {
        assertTrue(legacyTimingProfileLibrary(null, manuallyEdited = true).profiles.isEmpty())
    }

    @Test
    fun `resolving puts the term start back on the profile`() {
        val resolved = entry("a").resolveWith("2026-09-07")

        assertEquals("2026-09-07", resolved.termStartDate)
        assertEquals(1, resolved.slotTimes.size)
    }

    @Test
    fun `the first inserted profile becomes active`() {
        val library = TimingProfileLibrary().upserting(entry("a"))

        assertEquals("a", library.activeId)
    }

    @Test
    fun `upserting replaces the profile with the same id and keeps the selection`() {
        val library = TimingProfileLibrary(listOf(entry("a"), entry("b")), activeId = "b")
            .upserting(entry("a", node = 5))

        assertEquals(2, library.profiles.size)
        assertEquals(5, library.entryOf("a")?.slotTimes?.single()?.startNode)
        assertEquals("b", library.activeId)
    }

    @Test
    fun `updating the active profile leaves the others alone`() {
        val library = TimingProfileLibrary(listOf(entry("a"), entry("b")), activeId = "b")
            .updatingActive { it.copy(manuallyEdited = true) }

        assertTrue(library.entryOf("b")?.manuallyEdited == true)
        assertTrue(library.entryOf("a")?.manuallyEdited == false)
    }

    @Test
    fun `updating an empty library changes nothing`() {
        val library = TimingProfileLibrary()

        assertEquals(library, library.updatingActive { it.copy(name = "x") })
    }

    @Test
    fun `activating an unknown id changes nothing`() {
        val library = TimingProfileLibrary(listOf(entry("a")), activeId = "a")

        assertEquals("a", library.activating("nope").activeId)
        assertEquals("a", library.activating("a").activeId)
    }

    @Test
    fun `renaming drops the built-in name key`() {
        val library = TimingProfileLibrary(
            listOf(TimingProfileEntry(id = "a", nameKey = DEFAULT_TIMING_PROFILE_NAME_KEY)),
            activeId = "a",
        ).renaming("a", "  夏季作息  ")

        assertEquals("夏季作息", library.entryOf("a")?.name)
        assertNull(library.entryOf("a")?.nameKey)
    }

    @Test
    fun `a blank name is rejected`() {
        val library = TimingProfileLibrary(listOf(entry("a")), activeId = "a")

        assertEquals(library, library.renaming("a", "   "))
    }

    @Test
    fun `the last profile cannot be removed`() {
        val library = TimingProfileLibrary(listOf(entry("a")), activeId = "a")

        assertEquals(library, library.removing("a"))
    }

    @Test
    fun `removing the active profile selects the first remaining one`() {
        val library = TimingProfileLibrary(listOf(entry("a"), entry("b"), entry("c")), activeId = "b")
            .removing("b")

        assertEquals(listOf("a", "c"), library.profiles.map { it.id })
        assertEquals("a", library.activeId)
    }

    @Test
    fun `removing a background profile keeps the selection`() {
        val library = TimingProfileLibrary(listOf(entry("a"), entry("b")), activeId = "b").removing("a")

        assertEquals("b", library.activeId)
    }

    @Test
    fun `a duplicate copies the slots and counts as manually edited`() {
        val library = TimingProfileLibrary(listOf(entry("a", node = 3)), activeId = "a")
            .duplicating("a", newId = "a-copy", name = "副本")

        assertEquals(listOf("a", "a-copy"), library.profiles.map { it.id })
        assertEquals(3, library.entryOf("a-copy")?.slotTimes?.single()?.startNode)
        assertTrue(library.entryOf("a-copy")?.manuallyEdited == true)
        // 复制不改变选中项，用户可能只是想留个备份
        assertEquals("a", library.activeId)
    }

    @Test
    fun `duplicating onto an existing id changes nothing`() {
        val library = TimingProfileLibrary(listOf(entry("a"), entry("b")), activeId = "a")

        assertEquals(library, library.duplicating("a", newId = "b", name = "副本"))
        assertEquals(library, library.duplicating("missing", newId = "c", name = "副本"))
    }
}
