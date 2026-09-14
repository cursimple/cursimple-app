package com.x500x.cursimple.core.kernel.model

import org.junit.Assert.assertEquals
import org.junit.Test

class LocationSuffixTest {

    @Test
    fun `shared suffix strips school name from room locations`() {
        val locations = listOf(
            "东13-306长江大学",
            "东16-101长江大学",
            "西22-204长江大学",
        )

        val suffix = sharedLocationSuffix(locations)

        assertEquals("长江大学", suffix)
        assertEquals("东13-306", stripLocationSuffix(locations[0], suffix))
        assertEquals("东16-101", stripLocationSuffix(locations[1], suffix))
    }

    @Test
    fun `separator before school name is removed too`() {
        val locations = listOf("东13-306 长江大学", "东16-101 长江大学")

        val suffix = sharedLocationSuffix(locations)

        assertEquals("东13-306", stripLocationSuffix(locations[0], suffix))
    }

    @Test
    fun `campus tail is part of the suffix`() {
        val locations = listOf("东13-306长江大学东校区", "东16-101长江大学东校区")

        assertEquals("长江大学东校区", sharedLocationSuffix(locations))
        assertEquals("东13-306", stripLocationSuffix(locations[0], sharedLocationSuffix(locations)))
    }

    @Test
    fun `different campuses do not count as shared`() {
        val locations = listOf("东13-306长江大学", "西22-101武汉大学")

        assertEquals("", sharedLocationSuffix(locations))
    }

    @Test
    fun `mixed campuses without majority do not strip`() {
        val locations = listOf("东13-306长江大学", "西22-101长江大学西校区")

        assertEquals("", sharedLocationSuffix(locations))
    }

    @Test
    fun `chinese room names are not swallowed into the suffix`() {
        // “层”是汉字，机构名前面不是边界，不能把它一并剥掉
        val locations = listOf("实验楼三层长江大学", "实验楼五层长江大学")

        assertEquals("", sharedLocationSuffix(locations))
    }

    @Test
    fun `location that is only the school name stays intact`() {
        val locations = listOf("长江大学", "东13-306长江大学")

        val suffix = sharedLocationSuffix(locations)

        assertEquals("长江大学", suffix)
        assertEquals("长江大学", stripLocationSuffix("长江大学", suffix))
    }

    @Test
    fun `single location never yields a suffix`() {
        assertEquals("", sharedLocationSuffix(listOf("东13-306长江大学")))
        assertEquals("", sharedLocationSuffix(emptyList()))
    }

    @Test
    fun `locations without org keyword are untouched`() {
        val locations = listOf("A栋301", "B栋202")

        assertEquals("", sharedLocationSuffix(locations))
        assertEquals("A栋301", stripLocationSuffix("A栋301", "大学"))
    }
}
