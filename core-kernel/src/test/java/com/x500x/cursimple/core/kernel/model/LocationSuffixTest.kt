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
    fun `the same school written with and without a campus is still stripped`() {
        // 以前要求过半才剥，这种一半带校区、一半不带的写法就凑不够，
        // 结果整份课表的格子里都留着学校名——用户看到的正是这个。
        val locations = listOf("东13-306长江大学", "西22-101长江大学西校区")

        val suffix = sharedLocationSuffix(locations)

        assertEquals("长江大学", suffix)
        assertEquals("东13-306", stripLocationSuffix(locations[0], suffix))
        // 认定的是不带校区的写法，带校区的那条也要一起剥干净
        assertEquals("西22-101", stripLocationSuffix(locations[1], suffix))
    }

    @Test
    fun `a school name in front of the room is stripped too`() {
        // 同一个教务系统里两种语序都见得到，不能只认结尾
        val locations = listOf("长江大学东13-306", "长江大学西22-101")

        val suffix = sharedLocationSuffix(locations)

        assertEquals("东13-306", stripLocationSuffix(locations[0], suffix))
        assertEquals("西22-101", stripLocationSuffix(locations[1], suffix))
    }

    @Test
    fun `a room name running straight into the school name may lose one character`() {
        // 教室名与学校名之间没有分隔符时，光看字符串无法断定名字从哪个字起：
        // 「实验楼三层长江大学」里的「层」和「北京理工大学」里的「京」处境完全一样。
        // 取最长的共有写法，是为了让「北京理工大学」这类四字校名能整个剥掉——
        // 代价是这种没有分隔符的写法会多吃一个字。真实课表里前者常见、后者罕见。
        val locations = listOf("实验楼三层长江大学", "实验楼五层长江大学")

        val stripped = stripLocationSuffix(locations[0], sharedLocationSuffix(locations))

        assertEquals("实验楼三", stripped)
    }

    @Test
    fun `a multi character school name is stripped whole`() {
        val locations = listOf("三教305北京理工大学", "四教210北京理工大学")

        val suffix = sharedLocationSuffix(locations)

        assertEquals("北京理工大学", suffix)
        assertEquals("三教305", stripLocationSuffix(locations[0], suffix))
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
