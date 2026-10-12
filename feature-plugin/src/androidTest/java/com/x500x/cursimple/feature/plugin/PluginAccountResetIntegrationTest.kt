package com.x500x.cursimple.feature.plugin

import android.webkit.CookieManager
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import org.junit.Assert.*
import org.junit.Test

class PluginAccountResetIntegrationTest {
    private val portal = "https://portal.account-qa.invalid:4443/passport/"
    private val courses = "https://courses.account-qa.invalid/eams/"
    private val other = "https://notification.account-qa.invalid/"
    private val hosts = listOf("portal.account-qa.invalid", "courses.account-qa.invalid")
    private suspend fun cookie(url: String, value: String) = withContext(Dispatchers.Main) {
        suspendCancellableCoroutine<Unit> { continuation ->
            CookieManager.getInstance().setCookie(url, value) { if (continuation.isActive) continuation.resume(Unit) }
        }
    }
    private suspend fun header(url: String) = withContext(Dispatchers.Main) { CookieManager.getInstance().getCookie(url).orEmpty() }

    @Test fun switchClearsPortalAndCourseCookiesAndKeepsComponentBinding() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val reset = withContext(Dispatchers.Main) { PluginWebSessionReset(context) }
        try {
            cookie(portal, "PORTAL=old; Path=/passport; Secure; HttpOnly")
            cookie(courses, "COURSES=old; Path=/eams; Secure; HttpOnly")
            cookie(courses, "ROOT=old; Path=/; Secure")
            cookie(other, "COMPONENT=keep; Path=/; Secure")
            reset.rememberScopes("account-qa", listOf(portal, courses))
            reset.reset("account-qa", hosts)
            assertFalse(header(portal).contains("PORTAL="))
            assertFalse(header(courses).contains("COURSES="))
            assertFalse(header(courses).contains("ROOT="))
            assertTrue(header(other).contains("COMPONENT=keep"))
            cookie(courses, "COURSES=new; Path=/eams; Secure; HttpOnly")
            assertTrue(header(courses).contains("COURSES=new"))
            assertFalse(header(courses).contains("old"))
        } finally {
            reset.reset("account-qa", hosts, extraScopeUrls = listOf(portal, courses))
            cookie(other, "COMPONENT=; Max-Age=0; Path=/; Secure")
            reset.forget("account-qa")
        }
    }
}
