package com.x500x.cursimple.feature.plugin

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 登录页「记住密码」脚本的生成规则；存储与加密依赖 Keystore，在真机上验证。 */
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
        // `<` 转义掉，密码里带 </script> 也截不断外层脚本
        assertFalse(script.contains("</script>"))
        assertTrue(script.contains("\"p\\\\w\\u003c/script>\""))
    }

    @Test
    fun scriptReportsThroughTheLoginBridge() {
        val script = webLoginAssistScript("cas.example.edu.cn", saved = null)
        assertTrue(script.contains("window.${WebLoginAssist.BRIDGE_NAME}.onLoginSubmit("))
    }
}
