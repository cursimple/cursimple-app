package com.x500x.cursimple.core.plugin.school

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SchoolPortalCatalogTest {
    private val entries: List<SchoolPortalEntry> by lazy {
        SchoolPortalCatalog.parse(File("src/main/assets/${SchoolPortalCatalog.ASSET_PATH}").readText())
    }

    @Test
    fun catalogContainsOnlyOpenableHttpsPortalsOrPluginBindings() {
        assertTrue(entries.size > 500)
        entries.forEach { entry ->
            // A plugin-bound school may also list a fixed portal; its generic import opens that address.
            if (entry.portalUrl != null) {
                assertTrue(entry.name, SchoolPortalCatalog.isOpenablePortal(entry.portalUrl))
            } else {
                assertTrue(entry.name, entry.usesPlugin)
            }
        }
    }

    @Test
    fun schoolNamesAndIdsAreUnique() {
        assertEquals(entries.size, entries.map { it.name }.toSet().size)
        assertEquals(entries.size, entries.map { it.id }.toSet().size)
    }

    @Test
    fun eachPluginIsBoundToExactlyOneSchool() {
        val repos = entries.mapNotNull { it.pluginRepo }
        assertEquals(repos.size, repos.toSet().size)
        assertTrue(entries.any { it.name == "长江大学" && it.pluginRepo == "cursimple/YangtzU_course_plugin" })
        val ids = entries.mapNotNull { it.pluginId }
        assertEquals(ids.size, ids.toSet().size)
        assertTrue(entries.filter { it.pluginId != null }.all { it.usesPlugin && it.portalUrl != null })
        assertFalse(entries.single { it.name == "长江大学" }.pluginId.isNullOrBlank())
    }

    @Test
    fun schoolTimingIsBundledWithoutDependingOnAnInstalledPluginOrOldTermDate() {
        val profile = entries.single { it.name == "长江大学" }.timingProfile!!
        assertEquals("", profile.termStartDate)
        assertEquals(listOf(1, 2, 3, 4, 5, 6, 7, 8), profile.slotTimes.map { it.startNode })
        assertEquals("午间课", profile.slotTimes[2].label)
        assertEquals("晚间课", profile.slotTimes[5].label)
        assertEquals("第三节", profile.slotTimes[3].label)
        assertEquals("14:00", profile.slotTimes[3].startTime)
        assertEquals("20:45", profile.slotTimes[7].startTime)
        assertNull(entries.first { it.name == "清华大学" }.timingProfile)
    }

    @Test
    fun wellKnownSchoolsArePresent() {
        assertNotNull(entries.firstOrNull { it.name == "清华大学" })
        assertNotNull(entries.firstOrNull { it.name == "浙江大学" })
    }

    @Test
    fun searchIsCaseInsensitiveSubstringAndBlankReturnsAll() {
        val sample = entries.take(50)
        assertEquals(sample, SchoolPortalCatalog.search(sample, "  "))
        val hits = SchoolPortalCatalog.search(entries, "清华")
        assertTrue(hits.isNotEmpty())
        assertTrue(hits.all { it.name.contains("清华") })
    }

    @Test
    fun openablePortalRejectsHttpIpLiteralsAndMissingHosts() {
        assertTrue(SchoolPortalCatalog.isOpenablePortal("https://jw.example.edu.cn/login"))
        assertFalse(SchoolPortalCatalog.isOpenablePortal("http://jw.example.edu.cn/login"))
        assertFalse(SchoolPortalCatalog.isOpenablePortal("https://10.0.0.8/login"))
        assertFalse(SchoolPortalCatalog.isOpenablePortal("https://222.75.167.77/portal"))
        assertFalse(SchoolPortalCatalog.isOpenablePortal("https://intranet/"))
        assertFalse(SchoolPortalCatalog.isOpenablePortal(null))
        assertFalse(SchoolPortalCatalog.isOpenablePortal("  "))
    }

    @Test
    fun allowedHostsIncludeTheSchoolDomainButNotPublicSuffixes() {
        assertEquals(listOf("jw.school.edu.cn", "school.edu.cn"), SchoolPortalCatalog.allowedHostsFor("jw.school.edu.cn"))
        assertEquals(listOf("xyz.edu.cn"), SchoolPortalCatalog.allowedHostsFor("xyz.edu.cn"))
        assertEquals(listOf("portal.example.com"), SchoolPortalCatalog.allowedHostsFor("portal.example.com").take(1))
        assertFalse(SchoolPortalCatalog.allowedHostsFor("jw.school.edu.cn").contains("edu.cn"))
    }
}
