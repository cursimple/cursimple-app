package com.x500x.cursimple.core.kernel.model

import org.junit.Assert.assertEquals
import org.junit.Test

class LocationSuffixTest {

    @Test
    fun `shared suffix strips school name from room locations`() {
        val locations = listOf(
            "东13-306示例大学",
            "东16-101示例大学",
            "西22-204示例大学",
        )

        val suffix = sharedLocationSuffix(locations)

        assertEquals("示例大学", suffix)
        assertEquals("东13-306", stripLocationSuffix(locations[0], suffix))
        assertEquals("东16-101", stripLocationSuffix(locations[1], suffix))
    }

    @Test
    fun `separator before school name is removed too`() {
        val locations = listOf("东13-306 示例大学", "东16-101 示例大学")

        val suffix = sharedLocationSuffix(locations)

        assertEquals("东13-306", stripLocationSuffix(locations[0], suffix))
    }

    @Test
    fun `campus tail is part of the suffix`() {
        val locations = listOf("东13-306示例大学东校区", "东16-101示例大学东校区")

        assertEquals("示例大学东校区", sharedLocationSuffix(locations))
        assertEquals("东13-306", stripLocationSuffix(locations[0], sharedLocationSuffix(locations)))
    }

    @Test
    fun `different campuses do not count as shared`() {
        val locations = listOf("东13-306示例大学", "西22-101其他大学")

        assertEquals("", sharedLocationSuffix(locations))
    }

    @Test
    fun `the same school written with and without a campus is still stripped`() {
        // Mixed campus forms can match without a majority threshold.
        val locations = listOf("东13-306示例大学", "西22-101示例大学西校区")

        val suffix = sharedLocationSuffix(locations)

        assertEquals("示例大学", suffix)
        assertEquals("东13-306", stripLocationSuffix(locations[0], suffix))
        assertEquals("西22-101", stripLocationSuffix(locations[1], suffix))
    }

    @Test
    fun `a school name in front of the room is stripped too`() {
        // Match institution names before or after room text.
        val locations = listOf("示例大学东13-306", "示例大学西22-101")

        val suffix = sharedLocationSuffix(locations)

        assertEquals("东13-306", stripLocationSuffix(locations[0], suffix))
        assertEquals("西22-101", stripLocationSuffix(locations[1], suffix))
    }

    @Test
    fun `a room name running straight into the school name may lose one character`() {
        // Without separators, the longest shared candidate may consume a room character; keep this inference tradeoff explicit.
        val locations = listOf("实验楼三层示例大学", "实验楼五层示例大学")

        val stripped = stripLocationSuffix(locations[0], sharedLocationSuffix(locations))

        assertEquals("实验楼三", stripped)
    }

    @Test
    fun `a multi character school name is stripped whole`() {
        val locations = listOf("三教305示例理工大学", "四教210示例理工大学")

        val suffix = sharedLocationSuffix(locations)

        assertEquals("示例理工大学", suffix)
        assertEquals("三教305", stripLocationSuffix(locations[0], suffix))
    }

    @Test
    fun `location that is only the school name stays intact`() {
        val locations = listOf("示例大学", "东13-306示例大学")

        val suffix = sharedLocationSuffix(locations)

        assertEquals("示例大学", suffix)
        assertEquals("示例大学", stripLocationSuffix("示例大学", suffix))
    }

    @Test
    fun `single location never yields a suffix`() {
        assertEquals("", sharedLocationSuffix(listOf("东13-306示例大学")))
        assertEquals("", sharedLocationSuffix(emptyList()))
    }

    @Test
    fun `locations without org keyword are untouched`() {
        val locations = listOf("A栋301", "B栋202")

        assertEquals("", sharedLocationSuffix(locations))
        assertEquals("A栋301", stripLocationSuffix("A栋301", "大学"))
    }
}
