package com.x500x.cursimple.app

import android.content.Context
import androidx.test.platform.app.InstrumentationRegistry
import com.x500x.cursimple.app.github.GitHubAccount
import com.x500x.cursimple.app.github.GitHubAccountStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GitHubAccountStoreTest {
    @Test fun tokenIsEncryptedReloadedAndClearedAndSameAccountRefreshesSession() {
        val context = IsolatedGitHubContext(InstrumentationRegistry.getInstrumentation().targetContext, "github_account_store_test")
        val store = GitHubAccountStore(context)
        store.clear()
        try {
            val account = GitHubAccount("qa-user", "")
            val token = "qa-token-for-local-keystore-test"
            val before = store.revision.value
            store.save(token, account)
            assertEquals(account, store.account.value)
            assertTrue(store.revision.value > before)
            val encrypted = context.getSharedPreferences("unused", Context.MODE_PRIVATE).getString("token", "")!!
            assertTrue(encrypted.isNotBlank())
            assertFalse(encrypted.contains(token))
            assertEquals(token, GitHubAccountStore(context).token())
            val revision = store.revision.value
            store.save("qa-another-token", account)
            assertTrue(store.revision.value > revision)
            store.clear()
            assertNull(store.account.value)
            assertNull(store.token())
            assertNull(GitHubAccountStore(context).account.value)
        } finally { store.clear() }
    }

    @Test fun unreadableCiphertextClearsStaleSignedInState() {
        val context = IsolatedGitHubContext(InstrumentationRegistry.getInstrumentation().targetContext, "github_account_invalid_test")
        context.getSharedPreferences("unused", Context.MODE_PRIVATE).edit()
            .putString("login", "qa-user").putString("token", "invalid-encrypted-value").commit()
        val store = GitHubAccountStore(context)
        assertNull(store.account.value)
        assertNull(store.token())
    }
}
