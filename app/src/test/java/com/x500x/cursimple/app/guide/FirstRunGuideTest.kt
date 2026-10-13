package com.x500x.cursimple.app.guide

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import com.x500x.cursimple.R

class ShouldShowFirstRunGuideTest {
    @Test
    fun `a fresh install shows the guide once the disclaimer is accepted`() {
        assertTrue(
            shouldShowFirstRunGuide(
                loaded = true,
                disclaimerAccepted = true,
                guideCompleted = false,
                blockingDialogVisible = false,
            ),
        )
    }

    @Test
    fun `nothing shows before preferences are loaded`() {
        assertFalse(
            shouldShowFirstRunGuide(
                loaded = false,
                disclaimerAccepted = true,
                guideCompleted = false,
                blockingDialogVisible = false,
            ),
        )
    }

    @Test
    fun `the disclaimer comes first`() {
        assertFalse(
            shouldShowFirstRunGuide(
                loaded = true,
                disclaimerAccepted = false,
                guideCompleted = false,
                blockingDialogVisible = false,
            ),
        )
    }

    @Test
    fun `the term start prompt is not covered by the guide`() {
        assertFalse(
            shouldShowFirstRunGuide(
                loaded = true,
                disclaimerAccepted = true,
                guideCompleted = false,
                blockingDialogVisible = true,
            ),
        )
    }

    @Test
    fun `a finished guide never comes back on its own`() {
        assertFalse(
            shouldShowFirstRunGuide(
                loaded = true,
                disclaimerAccepted = true,
                guideCompleted = true,
                blockingDialogVisible = false,
            ),
        )
    }
}

class GuideNavigationTest {
    @Test
    fun `next walks forward through the steps`() {
        assertEquals(1, nextGuideIndex(current = 0, total = 3))
        assertEquals(2, nextGuideIndex(current = 1, total = 3))
    }

    @Test
    fun `next past the last step ends the guide`() {
        assertNull(nextGuideIndex(current = 2, total = 3))
    }

    @Test
    fun `previous walks back but stops at the first step`() {
        assertEquals(1, previousGuideIndex(current = 2))
        assertEquals(0, previousGuideIndex(current = 1))
        assertEquals(0, previousGuideIndex(current = 0))
    }
}

class GuideStepsTest {
    @Test
    fun `the guide has steps to show`() {
        assertTrue(firstRunGuideSteps(bottomBar = false).isNotEmpty())
    }

    @Test
    fun `steps that point somewhere name a real element`() {
        (firstRunGuideSteps(bottomBar = false) + firstRunGuideSteps(bottomBar = true)).mapNotNull { it.anchor }.forEach { anchor ->
            assertTrue(anchor in GuideAnchor.entries)
        }
    }
}

class GuideLayoutTest {
    @Test
    fun `the bottom bar tour explains the bar instead of the drawer menu`() {
        val bar = firstRunGuideSteps(bottomBar = true).first()
        assertEquals(R.string.guide_bar_title, bar.titleRes)
        assertEquals(R.string.guide_bar_body, bar.bodyRes)
        assertEquals(GuideAnchor.Navigation, bar.anchor)
        val drawer = firstRunGuideSteps(bottomBar = false).first()
        assertEquals(R.string.guide_drawer_body, drawer.bodyRes)
        assertEquals(GuideAnchor.Navigation, drawer.anchor)
    }

    @Test
    fun `both layouts visit the same pages`() {
        assertEquals(firstRunGuideSteps(false).map { it.destination }, firstRunGuideSteps(true).map { it.destination })
    }

    @Test
    fun `page steps introduce the page instead of narrating how it opens`() {
        val pageBodies = firstRunGuideSteps(bottomBar = false)
            .filter { it.destination != GuideDestination.Schedule }
            .map { stepName(it.bodyRes) }
        listOf("values", "values-en", "values-zh-rTW").forEach { dir ->
            val strings = strings(dir)
            pageBodies.forEach { name ->
                val body = strings.getValue(name)
                listOf("下一步", "过渡", "轉場", "Next", "transition").forEach { word ->
                    assertFalse("$dir/$name 还在讲怎么打开：$body", body.contains(word))
                }
            }
        }
    }

    private fun stepName(id: Int) = R.string::class.java.fields.first { it.getInt(null) == id }.name

    private fun strings(dir: String): Map<String, String> {
        val userDir = java.nio.file.Paths.get(System.getProperty("user.dir"))
        val file = sequenceOf(userDir.resolve("src/main/res/$dir/strings.xml"), userDir.resolve("app/src/main/res/$dir/strings.xml"))
            .first(java.nio.file.Files::isRegularFile).toFile()
        val nodes = javax.xml.parsers.DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
            .getElementsByTagName("string")
        return (0 until nodes.length).map { nodes.item(it) as org.w3c.dom.Element }
            .associate { it.getAttribute("name") to it.textContent }
    }
}

class GuideCardPlacementTest {
    @Test
    fun `a spotlight up top puts the card at the bottom`() {
        assertEquals(
            GuideCardPlacement.Bottom,
            guideCardPlacement(anchorCenterY = 120f, containerHeight = 2400f),
        )
    }

    @Test
    fun `a spotlight down low puts the card up top`() {
        assertEquals(
            GuideCardPlacement.Top,
            guideCardPlacement(anchorCenterY = 1800f, containerHeight = 2400f),
        )
    }

    @Test
    fun `a step with nothing to circle centres the card`() {
        assertEquals(
            GuideCardPlacement.Center,
            guideCardPlacement(anchorCenterY = null, containerHeight = 2400f),
        )
        assertEquals(
            GuideCardPlacement.Center,
            guideCardPlacement(anchorCenterY = 100f, containerHeight = 0f),
        )
    }
}
