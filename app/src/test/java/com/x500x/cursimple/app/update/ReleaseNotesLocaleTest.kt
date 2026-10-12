package com.x500x.cursimple.app.update

import org.junit.Assert.*
import org.junit.Test

class ReleaseNotesLocaleTest {
    @Test fun `app language resolves traditional Chinese and English independently`() {
        assertEquals("zh-TW", releaseNotesLanguage("zh-Hant-HK"))
        assertEquals("zh-CN", releaseNotesLanguage("zh-Hans-CN"))
        assertEquals("en", releaseNotesLanguage("en-US"))
        assertTrue(localizedReleasePaths("v0.7.9", "en").first().endsWith("v0.7.9.en.md"))
        assertTrue(localizedReleasePaths("v0.7.9", "zh-TW").first().endsWith("v0.7.9.zh-TW.md"))
    }
}
