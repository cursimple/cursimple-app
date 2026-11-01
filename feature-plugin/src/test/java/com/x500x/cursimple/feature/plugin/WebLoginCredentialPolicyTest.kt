package com.x500x.cursimple.feature.plugin

import com.x500x.cursimple.core.plugin.school.SchoolPortalCatalog
import com.x500x.cursimple.core.plugin.school.SchoolPortalEntry
import org.junit.Assert.*
import org.junit.Test

class WebLoginCredentialPolicyTest {
    private val general = SchoolPortalCatalog.PLUGIN_ID
    private val policy = WebLoginCredentialPolicy(listOf(
        SchoolPortalEntry("school-a", "School A", "https://portal.a.edu.cn/login", "org/a", pluginId = "plugin-a"),
        SchoolPortalEntry("school-b", "School B", "https://portal.b.edu.cn/login", "org/b", pluginId = "plugin-b"),
        SchoolPortalEntry("unbound", "Unbound", "https://unbound.example.edu/login", "org/unbound"),
    ))

    @Test fun knownSchoolSharesOneOwnerWithGeneralImport() {
        assertEquals("school:school-a", policy.owners(general, "portal.a.edu.cn").first())
        assertEquals(policy.owners(general, "portal.a.edu.cn").toSet(),
            policy.owners("plugin-a", "PORTAL.A.EDU.CN.").toSet())
        assertTrue(policy.owns("plugin-a", general, "sso.a.edu.cn"))
    }

    @Test fun arbitraryPluginAndOtherSchoolCannotReadSharedCredentials() {
        assertEquals(listOf("arbitrary"), policy.owners("arbitrary", "portal.a.edu.cn"))
        assertEquals(listOf("plugin-a"), policy.owners("plugin-a", "portal.b.edu.cn"))
        assertFalse(policy.owns("plugin-b", "school:school-a", "portal.a.edu.cn"))
        assertFalse(policy.owns("plugin-a", general, "portal.a.edu.cn.attacker.test"))
        assertFalse(policy.owns("plugin-a", general, "not-a.edu.cn"))
    }

    @Test fun clearingOneSchoolOnlyIncludesItsExactHostAliases() {
        for (owner in listOf("school:school-a", "plugin-a", general)) {
            assertTrue(policy.owns("plugin-a", owner, "portal.a.edu.cn"))
        }
        assertFalse(policy.owns("plugin-a", general, "portal.b.edu.cn"))
        assertFalse(policy.owns("plugin-a", "school:school-b", "portal.b.edu.cn"))
    }

    @Test fun missingCatalogBindingKeepsPluginPrivate() {
        assertEquals(listOf("unbound"), policy.owners("unbound", "unbound.example.edu"))
        assertEquals(listOf(general), policy.owners(general, "unbound.example.edu"))
    }
}
