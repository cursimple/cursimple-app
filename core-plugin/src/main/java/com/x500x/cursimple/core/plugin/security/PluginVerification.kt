package com.x500x.cursimple.core.plugin.security

import com.x500x.cursimple.core.plugin.PluginArgumentException
import com.x500x.cursimple.core.plugin.R
import com.x500x.cursimple.core.plugin.packageformat.PluginPackageLayout
import com.x500x.cursimple.core.plugin.pluginReasonOr
import com.x500x.cursimple.core.plugin.pluginRequire
import com.x500x.cursimple.core.plugin.pluginRequireNotNull
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.ByteArrayOutputStream
import java.security.KeyFactory
import java.security.MessageDigest
import java.security.PublicKey
import java.security.Signature
import java.security.interfaces.RSAPublicKey
import java.security.spec.X509EncodedKeySpec

@Serializable
data class PluginChecksums(
    @SerialName("algorithm") val algorithm: String = "SHA-256",
    @SerialName("files") val files: Map<String, String> = emptyMap(),
)

@Serializable
data class PluginSignatureInfo(
    @SerialName("algorithm") val algorithm: String = "SHA256withRSA",
    @SerialName("publicKeyPem") val publicKeyPem: String,
    @SerialName("signatureBase64") val signatureBase64: String,
    @SerialName("signedFile") val signedFile: String = PluginPackageLayout.CHECKSUMS_FILE,
)

enum class PluginSignatureStatus {
    Absent,

    Valid,

    Invalid,
}

data class PluginSignatureResult(
    val status: PluginSignatureStatus,
    val signerFingerprint: String? = null,
    val error: Throwable? = null,
)

class PluginChecksumVerifier {
    fun verify(layout: PluginPackageLayout, checksums: PluginChecksums): Boolean {
        pluginRequire(
            checksums.algorithm.equals(SHA_256, ignoreCase = true),
            R.string.plugin_error_checksum_algorithm_unsupported,
            checksums.algorithm,
        )
        pluginRequire(checksums.files.isNotEmpty(), R.string.plugin_error_checksum_empty)
        checksums.files.forEach { (path, expected) ->
            pluginRequire(path in layout.files, R.string.plugin_error_checksum_unknown_file, path)
            pluginRequire(
                SHA_256_HEX.matches(expected),
                R.string.plugin_error_checksum_format_invalid,
                path,
            )
        }
        val requiredFiles = layout.files.keys
            .filterNot { it in OPTIONAL_METADATA_FILES }
            .toSet()
        val checksumFiles = checksums.files.keys.toSet()
        if (checksumFiles != requiredFiles) {
            throw coverageError(
                missing = (requiredFiles - checksumFiles).sorted(),
                extra = (checksumFiles - requiredFiles).sorted(),
            )
        }
        val digest = MessageDigest.getInstance(checksums.algorithm)
        return checksums.files.all { (path, expected) ->
            val actual = digest.digest(layout.requireFile(path)).joinToString("") { "%02x".format(it) }
            actual.equals(expected, ignoreCase = true)
        }
    }

    /** Separate optional missing and excess sections to keep localization arguments aligned. */
    private fun coverageError(missing: List<String>, extra: List<String>): PluginArgumentException = when {
        missing.isNotEmpty() && extra.isNotEmpty() -> PluginArgumentException(
            R.string.plugin_error_checksum_coverage_missing_extra,
            listOf(missing.joinToString(), extra.joinToString()),
        )

        missing.isNotEmpty() -> PluginArgumentException(
            R.string.plugin_error_checksum_coverage_missing,
            listOf(missing.joinToString()),
        )

        extra.isNotEmpty() -> PluginArgumentException(
            R.string.plugin_error_checksum_coverage_extra,
            listOf(extra.joinToString()),
        )

        else -> PluginArgumentException(R.string.plugin_error_checksum_coverage)
    }

    private companion object {
        const val SHA_256 = "SHA-256"
        val SHA_256_HEX = Regex("[A-Fa-f0-9]{64}")
        val OPTIONAL_METADATA_FILES = setOf(
            PluginPackageLayout.CHECKSUMS_FILE,
            PluginPackageLayout.SIGNATURE_FILE,
        )
    }
}

class PluginSignatureVerifier {
    /**
     * Verify optional signature.json against checksums.json; missing is Absent, invalid content
     * is Invalid.
     */
    fun resolve(layout: PluginPackageLayout, json: Json): PluginSignatureResult {
        if (PluginPackageLayout.SIGNATURE_FILE !in layout.files) {
            return PluginSignatureResult(status = PluginSignatureStatus.Absent)
        }
        return runCatching {
            val info = json.decodeFromString<PluginSignatureInfo>(
                layout.readText(PluginPackageLayout.SIGNATURE_FILE),
            )
            pluginRequire(
                info.signedFile == PluginPackageLayout.CHECKSUMS_FILE,
                R.string.plugin_error_signature_scope,
                PluginPackageLayout.CHECKSUMS_FILE,
                info.signedFile,
            )
            val publicKey = parsePemPublicKey(info.publicKeyPem)
            val verified = verifyWithKey(
                publicKey = publicKey,
                algorithm = info.algorithm,
                payload = layout.requireFile(info.signedFile),
                signatureBase64 = info.signatureBase64,
            )
            if (verified) {
                PluginSignatureResult(
                    status = PluginSignatureStatus.Valid,
                    signerFingerprint = fingerprintOf(publicKey),
                )
            } else {
                PluginSignatureResult(
                    status = PluginSignatureStatus.Invalid,
                    error = PluginArgumentException(R.string.plugin_error_signature_checksum_mismatch),
                )
            }
        }.getOrElse { error ->
            PluginSignatureResult(
                status = PluginSignatureStatus.Invalid,
                error = pluginReasonOr(error, R.string.plugin_error_signature_unparseable),
            )
        }
    }

    fun verifySignedContent(
        publicKeyPem: String,
        algorithm: String,
        payload: ByteArray,
        signatureBase64: String,
    ): Boolean {
        return verifyWithKey(
            publicKey = parsePemPublicKey(publicKeyPem),
            algorithm = algorithm,
            payload = payload,
            signatureBase64 = signatureBase64,
        )
    }

    private fun verifyWithKey(
        publicKey: PublicKey,
        algorithm: String,
        payload: ByteArray,
        signatureBase64: String,
    ): Boolean {
        val normalizedAlgorithm = pluginRequireNotNull(
            SUPPORTED_ALGORITHMS.firstOrNull { it.equals(algorithm, ignoreCase = true) },
            R.string.plugin_error_signature_algorithm_unsupported,
            algorithm,
        )
        val signature = Signature.getInstance(normalizedAlgorithm)
        signature.initVerify(publicKey)
        signature.update(payload)
        return signature.verify(decodeBase64(signatureBase64))
    }

    private fun parsePemPublicKey(pem: String): PublicKey {
        val normalized = pem
            .replace("-----BEGIN PUBLIC KEY-----", "")
            .replace("-----END PUBLIC KEY-----", "")
            .replace("\\s".toRegex(), "")
        pluginRequire(normalized.isNotBlank(), R.string.plugin_error_signature_missing_public_key)
        val decoded = decodeBase64(normalized)
        val publicKey = KeyFactory.getInstance("RSA").generatePublic(X509EncodedKeySpec(decoded))
        val modulusBits = (publicKey as? RSAPublicKey)?.modulus?.bitLength() ?: 0
        pluginRequire(
            modulusBits >= MIN_RSA_KEY_BITS,
            R.string.plugin_error_signature_key_too_weak,
            modulusBits,
        )
        return publicKey
    }

    /** Grouped first 16 bytes of the public key's SHA-256 fingerprint. */
    private fun fingerprintOf(publicKey: PublicKey): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(publicKey.encoded)
        return digest.take(FINGERPRINT_BYTES).joinToString(":") { "%02X".format(it) }
    }

    private companion object {
        const val MIN_RSA_KEY_BITS = 2048
        const val FINGERPRINT_BYTES = 16
        val SUPPORTED_ALGORITHMS = listOf("SHA256withRSA", "SHA512withRSA")
    }
}

internal fun decodeBase64(value: String): ByteArray {
    val output = ByteArrayOutputStream(value.length / 4 * 3 + 3)
    var buffer = 0
    var bits = 0
    for (symbol in value) {
        if (symbol == '=') break
        if (symbol.isWhitespace()) continue
        val index = BASE64_ALPHABET.indexOf(symbol)
        pluginRequire(index >= 0, R.string.plugin_error_base64_illegal_character)
        buffer = (buffer shl 6) or index
        bits += 6
        if (bits >= 8) {
            bits -= 8
            output.write((buffer shr bits) and 0xFF)
        }
    }
    return output.toByteArray()
}

private const val BASE64_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/"
