package com.x500x.cursimple.app.provider

import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ProviderInfo
import android.net.Uri
import android.os.Bundle
import android.provider.DocumentsContract
import android.system.Os
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.File
import java.util.UUID

class PrivateFilesProviderSecurityTest {
    private lateinit var root: File
    private lateinit var provider: PrivateFilesDocumentsProvider
    private lateinit var app: Context
    private lateinit var prefs: android.content.SharedPreferences
    private val providerAuthority get() = "${app.packageName}.privatefiles.documents"
    private val prefix get() = "${app.packageName}/files"

    @Before fun setup() {
        val target = InstrumentationRegistry.getInstrumentation().targetContext
        root = File(target.cacheDir, "provider-security-${UUID.randomUUID()}").apply { mkdirs() }
        app = object : ContextWrapper(target) {
            override fun getFilesDir() = File(root, "files").apply { mkdirs() }
            override fun getApplicationContext(): Context = this
            override fun getSharedPreferences(name: String, mode: Int) = target.getSharedPreferences("security-test-$name", mode)
        }
        prefs = app.getSharedPreferences("private_files_provider", Context.MODE_PRIVATE)
        prefs.edit().putBoolean("private_files_provider_enabled", true).commit()
        provider = PrivateFilesDocumentsProvider()
        provider.attachInfo(app, ProviderInfo().apply {
            authority = providerAuthority
            exported = true; grantUriPermissions = true
            readPermission = "android.permission.MANAGE_DOCUMENTS"
            writePermission = "android.permission.MANAGE_DOCUMENTS"
        })
    }

    @After fun cleanup() { prefs.edit().clear().commit(); root.deleteRecursively() }

    @Test fun rejectsFinalAndAncestorSymlinksAndTraversal() {
        val outside = File(root, "outside").apply { mkdirs() }
        val secret = File(outside, "secret").apply { writeText("untouched") }
        Os.symlink(secret.path, File(app.filesDir, "link").path)
        Os.symlink(outside.path, File(app.filesDir, "parent").path)
        for (id in listOf("$prefix/link", "$prefix/parent/secret", "$prefix/../outside/secret", "$prefix/./link", "$prefix//link")) {
            assertTrue(id, runCatching { provider.openDocument(id, "wt", null).close() }.isFailure)
        }
        assertEquals("untouched", secret.readText())
        provider.queryChildDocuments(prefix, null, sortOrder = null).use { assertEquals(0, it.count) }
    }

    @Test fun normalReadWriteWorksAndRootsCannotBeMutated() {
        val id = provider.createDocument(prefix, "text/plain", "note.txt")
        provider.openDocument(id, "wt", null).use { android.os.ParcelFileDescriptor.AutoCloseOutputStream(it).use { out -> out.write("hello".toByteArray()) } }
        provider.openDocument(id, "r", null).use { android.os.ParcelFileDescriptor.AutoCloseInputStream(it).use { input -> assertEquals("hello", input.readBytes().toString(Charsets.UTF_8)) } }
        assertTrue(runCatching { provider.deleteDocument(prefix) }.isFailure)
        assertTrue(runCatching { provider.renameDocument(prefix, "moved") }.isFailure)
        assertTrue(runCatching { provider.createDocument(prefix, "text/plain", "..") }.isFailure)
        assertTrue(runCatching { provider.createDocument(prefix, "text/plain", "bad\u0000name") }.isFailure)
    }

    @Test fun customCallsRejectOtherAuthoritiesAndSymlinkCreation() {
        val id = provider.createDocument(prefix, "text/plain", "file")
        val extras = Bundle().apply { putParcelable("uri", DocumentsContract.buildDocumentUri(providerAuthority, id)); putString("path", "/data") }
        assertFalse(provider.call("mt:createSymlink", null, extras)!!.getBoolean("result"))
        extras.putParcelable("uri", DocumentsContract.buildDocumentUri("other.authority", id))
        assertTrue(runCatching { provider.call("mt:setLastModified", null, extras) }.exceptionOrNull() is SecurityException)
        extras.putParcelable("uri", Uri.parse("file:///data/user/0/secret"))
        assertTrue(runCatching { provider.call("mt:setPermissions", null, extras) }.exceptionOrNull() is SecurityException)
    }

    @Test fun disabledProviderRejectsAccessAndChildChecks() {
        prefs.edit().putBoolean("private_files_provider_enabled", false).commit()
        assertTrue(runCatching { provider.isChildDocument(prefix, "$prefix/file") }.isFailure)
        assertTrue(runCatching { provider.openDocument("$prefix/file", "r", null) }.isFailure)
    }
}
