package com.x500x.cursimple.feature.plugin

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WebLoginCredentialsTest {

    @Test
    fun scriptOnlyRunsOnTheHostItWasBuiltFor() {
        val script = webLoginAssistScript("cas.example.edu.cn", saved = null)
        assertTrue(script.contains("!== \"cas.example.edu.cn\""))
        assertTrue(script.contains("var saved = (null !== null)"))
    }

    @Test
    fun savedValuesAreEmbeddedAsJsonStringLiterals() {
        val script = webLoginAssistScript(
            "cas.example.edu.cn",
            WebLoginCredential(host = "cas.example.edu.cn", username = "2024\"01", password = "p\\w</script>"),
        )
        assertTrue(script.contains("\"2024\\\"01\""))
        assertFalse(script.contains("</script>"))
        assertTrue(script.contains("\"p\\\\w\\u003c/script>\""))
    }

    @Test
    fun scriptReportsThroughTheLoginBridge() {
        val script = webLoginAssistScript("cas.example.edu.cn", saved = null)
        assertTrue(script.contains("window.${WebLoginAssist.BRIDGE_NAME}.onLoginSubmit("))
    }

    @org.junit.Test
    fun autoSubmitIsOffUnlessAskedAndClaimedOncePerSheet() {
        val saved = WebLoginCredential(host = "cas.example.edu.cn", username = "u", password = "p")
        val off = webLoginAssistScript("cas.example.edu.cn", saved)
        val on = webLoginAssistScript("cas.example.edu.cn", saved, autoSubmit = true)
        org.junit.Assert.assertTrue(off.contains("if (false && user"))
        org.junit.Assert.assertTrue(on.contains("if (true && user"))
        // The page must ask the host before pressing, so one sheet presses at most once.
        org.junit.Assert.assertTrue(on.contains("claimAutoSubmit()"))
    }
}
