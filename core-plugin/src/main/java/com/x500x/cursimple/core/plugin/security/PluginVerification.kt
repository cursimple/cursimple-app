package com.x500x.cursimple.core.plugin.security

import com.x500x.cursimple.core.plugin.packageformat.PluginPackageLayout
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

/** 插件包中 signature.json 的校验结论。 */
enum class PluginSignatureStatus {
    /** 包内没有 signature.json。 */
    Absent,

    /** signature.json 存在，且对 checksums.json 的签名验证通过。 */
    Valid,

    /** signature.json 存在，但无法解析或验证不通过。 */
    Invalid,
}

data class PluginSignatureResult(
    val status: PluginSignatureStatus,
    val signerFingerprint: String? = null,
    val message: String? = null,
)

class PluginChecksumVerifier {
    fun verify(layout: PluginPackageLayout, checksums: PluginChecksums): Boolean {
        require(checksums.algorithm.equals(SHA_256, ignoreCase = true)) {
            "插件摘要只支持 SHA-256: ${checksums.algorithm}"
        }
        require(checksums.files.isNotEmpty()) { "插件摘要不能为空" }
        checksums.files.forEach { (path, expected) ->
            require(path in layout.files) { "插件摘要包含不存在的文件: $path" }
            require(SHA_256_HEX.matches(expected)) { "插件摘要格式无效: $path" }
        }
        val requiredFiles = layout.files.keys
            .filterNot { it in OPTIONAL_METADATA_FILES }
            .toSet()
        val checksumFiles = checksums.files.keys.toSet()
        require(checksumFiles == requiredFiles) {
            val missing = (requiredFiles - checksumFiles).sorted()
            val extra = (checksumFiles - requiredFiles).sorted()
            buildString {
                append("插件摘要文件覆盖不完整")
                if (missing.isNotEmpty()) append("，缺少: ").append(missing.joinToString())
                if (extra.isNotEmpty()) append("，多余: ").append(extra.joinToString())
            }
        }
        val digest = MessageDigest.getInstance(checksums.algorithm)
        return checksums.files.all { (path, expected) ->
            val actual = digest.digest(layout.requireFile(path)).joinToString("") { "%02x".format(it) }
            actual.equals(expected, ignoreCase = true)
        }
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
     * 读取包内 signature.json 并对 checksums.json 验签。包内没有该文件时返回 [PluginSignatureStatus.Absent]，
     * 解析失败或验签不通过返回 [PluginSignatureStatus.Invalid]。
     */
    fun resolve(layout: PluginPackageLayout, json: Json): PluginSignatureResult {
        if (PluginPackageLayout.SIGNATURE_FILE !in layout.files) {
            return PluginSignatureResult(status = PluginSignatureStatus.Absent)
        }
        return runCatching {
            val info = json.decodeFromString<PluginSignatureInfo>(
                layout.readText(PluginPackageLayout.SIGNATURE_FILE),
            )
            require(info.signedFile == PluginPackageLayout.CHECKSUMS_FILE) {
                "插件签名只能覆盖 ${PluginPackageLayout.CHECKSUMS_FILE}: ${info.signedFile}"
            }
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
                    message = "插件签名与包内摘要清单不匹配",
                )
            }
        }.getOrElse { error ->
            PluginSignatureResult(
                status = PluginSignatureStatus.Invalid,
                message = error.message ?: "插件签名无法解析",
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
        val normalizedAlgorithm = SUPPORTED_ALGORITHMS.firstOrNull { it.equals(algorithm, ignoreCase = true) }
        require(normalizedAlgorithm != null) { "插件签名算法不受支持: $algorithm" }
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
        require(normalized.isNotBlank()) { "插件签名缺少公钥" }
        val decoded = decodeBase64(normalized)
        val publicKey = KeyFactory.getInstance("RSA").generatePublic(X509EncodedKeySpec(decoded))
        val modulusBits = (publicKey as? RSAPublicKey)?.modulus?.bitLength() ?: 0
        require(modulusBits >= MIN_RSA_KEY_BITS) { "插件签名公钥强度不足: $modulusBits 位" }
        return publicKey
    }

    /** 公钥 DER 编码的 SHA-256 前 16 字节，按字节分组，供用户比对同一发布者的不同版本。 */
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

/**
 * 标准字母表的 Base64 解码，忽略空白字符。
 */
internal fun decodeBase64(value: String): ByteArray {
    val output = ByteArrayOutputStream(value.length / 4 * 3 + 3)
    var buffer = 0
    var bits = 0
    for (symbol in value) {
        if (symbol == '=') break
        if (symbol.isWhitespace()) continue
        val index = BASE64_ALPHABET.indexOf(symbol)
        require(index >= 0) { "Base64 内容包含非法字符" }
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
