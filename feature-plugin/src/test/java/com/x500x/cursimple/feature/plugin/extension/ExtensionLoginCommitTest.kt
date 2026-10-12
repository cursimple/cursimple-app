package com.x500x.cursimple.feature.plugin.extension

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test
import java.nio.file.Files

class ExtensionLoginCommitTest {
    private val login = buildJsonObject {
        put("loggedIn", true); put("account", buildJsonObject { put("id", "student-a"); put("name", "Student A") })
    }
    @Test fun firstLoginCreatesArchiveWithoutAnySettingsChanges() = runBlocking {
        val dir = Files.createTempDirectory("component-first-login").toFile()
        try {
            val store = ExtensionStore(dir)
            assertTrue(store.all.value.isEmpty())
            val before = requireNotNull(store.initializeIfMissing("sample") { true })
            val saved = store.updateIfPresent("sample") { commitComponentLogin(before, it, login, "student-a") }
            assertNotNull(saved); assertTrue(saved!!.loggedIn)
            assertEquals("student-a", saved.account?.id)
            assertEquals("student-a", ExtensionStore(dir).get("sample").account?.id)
        } finally { dir.deleteRecursively() }
    }
    @Test fun initializationRejectsClosedOrRemovedComponent() = runBlocking {
        val dir = Files.createTempDirectory("component-cancel-login").toFile()
        try {
            val store = ExtensionStore(dir)
            assertNull(store.initializeIfMissing("sample") { false })
            assertFalse(dir.resolve("sample.json").exists())
        } finally { dir.deleteRecursively() }
    }
    @Test fun repeatedConfirmationReturnsTheSavedAccountWithoutInvalidatingItsSession() {
        val before = ExtensionData("sample")
        val first = requireNotNull(commitComponentLogin(before, before, login))
        assertEquals(1L, first.sessionRevision)
        assertEquals(first, commitComponentLogin(before, first, login))
        assertEquals(first.sessionRevision, acceptComponentLogin(first, login).sessionRevision)
    }
    @Test fun logoutAndAccountChangeRejectOlderConfirmation() {
        val before = ExtensionData("sample")
        val loggedIn = acceptComponentLogin(before, login)
        assertNull(commitComponentLogin(before, loggedOutComponent(before), login))
        val other = loggedIn.copy(account = ExtensionAccount(id = "student-b"))
        assertNull(commitComponentLogin(before, other, login))
        val relogged = acceptComponentLogin(loggedOutComponent(before), login)
        assertNull(commitComponentLogin(before, relogged, login))
    }
    @Test fun changedSiteAndUnexpectedAccountCannotBeAccepted() {
        val before = ExtensionData("sample", settings = mapOf("site" to JsonPrimitive("one.example")))
        val first = acceptComponentLogin(before, login)
        assertNull(commitComponentLogin(before, first.copy(settings = mapOf("site" to JsonPrimitive("two.example"))), login))
        assertThrows(IllegalArgumentException::class.java) { commitComponentLogin(before, before, login, "student-b") }
    }
    @Test fun accountReplacementClearsPreviousUsersContent() {
        val before = ExtensionData("sample", loginState = ExtensionLoginState.LoggedIn,
            account = ExtensionAccount(id = "student-b"), items = listOf(ExtensionFeedItem("old", title = "Old")),
            state = mapOf("cache" to JsonPrimitive("old")), baselineReady = true)
        val saved = requireNotNull(commitComponentLogin(before, before, login, "student-a"))
        assertTrue(saved.items.isEmpty()); assertTrue(saved.state.isEmpty()); assertFalse(saved.baselineReady)
        assertEquals(before.sessionRevision + 1, saved.sessionRevision)
    }
    @Test fun removalDuringVerificationDoesNotRecreateArchive() = runBlocking {
        val dir = Files.createTempDirectory("component-remove-login").toFile()
        try {
            val store = ExtensionStore(dir)
            val before = requireNotNull(store.initializeIfMissing("sample") { true })
            store.remove("sample")
            assertNull(store.updateIfPresent("sample") { commitComponentLogin(before, it, login) })
            assertFalse(dir.resolve("sample.json").exists())
        } finally { dir.deleteRecursively() }
    }
}
