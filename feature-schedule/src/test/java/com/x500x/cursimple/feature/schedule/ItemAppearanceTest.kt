package com.x500x.cursimple.feature.schedule

import androidx.compose.ui.graphics.Color
import com.x500x.cursimple.core.kernel.model.CourseItem
import com.x500x.cursimple.core.kernel.model.CourseTimeSlot
import com.x500x.cursimple.core.kernel.model.ScheduleEvent
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ItemAppearanceTest {
    @Test
    fun hexColorsParseWithOrWithoutHashAndAlpha() {
        assertEquals(0xFF1E3A5FL, parseHexColor("#1E3A5F"))
        assertEquals(0xFF1E3A5FL, parseHexColor(" 1e3a5f "))
        assertEquals(0x801E3A5FL, parseHexColor("#801E3A5F"))
        assertNull(parseHexColor("#12345"))
        assertNull(parseHexColor("#GGGGGG"))
        assertNull(parseHexColor(""))
    }

    @Test
    fun hexColorsFormatShortWhenOpaque() {
        assertEquals("#1E3A5F", formatHexColor(0xFF1E3A5FL))
        assertEquals("#801E3A5F", formatHexColor(0x801E3A5FL))
        assertEquals(0xFF336699L, parseHexColor(formatHexColor(0xFF336699L)))
    }

    @Test
    fun textColorFollowsTheMixOfABlend() {
        // Light on its own, but blended into near-black the mix is dark and needs white text.
        val light = Color(0xFFF5F5F5)
        val dark = Color(0xFF101010)
        assertEquals(Color(0xFF1F2A24), readableOn(blendedFill(light, null)))
        assertEquals(Color.White, readableOn(blendedFill(light, dark)))
    }

    @Test
    fun aNewBlendColorIsDarkerForLightFillsAndLighterForDarkOnes() {
        val lightFill = 0xFFE8F5E9L
        val darkFill = 0xFF1E3A5FL
        assertTrue(Color(shadeOf(lightFill).toInt()).let { it.red + it.green + it.blue } < Color(lightFill.toInt()).let { it.red + it.green + it.blue })
        assertTrue(Color(shadeOf(darkFill).toInt()).let { it.red + it.green + it.blue } > Color(darkFill.toInt()).let { it.red + it.green + it.blue })
    }

    @Test
    fun appearanceIsDefaultOnlyWhenNothingIsSet() {
        assertTrue(ItemAppearance().isDefault)
        assertFalse(ItemAppearance(border = 0xFF000000L).isDefault)
    }

    @Test
    fun oldSavedCoursesAndEventsStillLoadWithoutColors() {
        val json = Json { ignoreUnknownKeys = true }
        val course = json.decodeFromString<CourseItem>("""{"id":"c","title":"T","time":{"dayOfWeek":1,"startNode":1,"endNode":2}}""")
        assertTrue(course.appearance().isDefault)
        val event = json.decodeFromString<ScheduleEvent>("""{"id":"e","title":"T","date":"2026-09-01","startTime":"08:00","endTime":"09:00","colorArgb":4278190335}""")
        assertEquals(ItemAppearance(fill = 4278190335L), event.appearance())
    }

    @Test
    fun colorsSurviveASaveAndLoad() {
        val course = CourseItem("c", "T", time = CourseTimeSlot(1, 1, 2), colorArgb = 0xFF112233L, colorEndArgb = 0xFF445566L, borderArgb = 0xFF778899L)
        val decoded = Json.decodeFromString<CourseItem>(Json.encodeToString(course))
        assertEquals(ItemAppearance(0xFF112233L, 0xFF445566L, 0xFF778899L), decoded.appearance())
    }
}
