package com.kebiao.viewer.core.plugin.security

import android.util.Base64
import com.kebiao.viewer.core.plugin.packageformat.PluginPackageLayout
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.security.KeyFactory
import java.security.MessageDigest
import java.security.PublicKey
import java.security.Signature
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

class PluginChecksumVerifier {
    fun verify(layout: PluginPackageLayout, checksums: PluginChecksums): Boolean {
        val digest = MessageDigest.getInstance(checksums.algorithm)
        return checksums.files.all { (path, expected) ->
            val actual = digest.digest(layout.requireFile(path)).joinToString("") { "%02x".format(it) }
            actual.equals(expected, ignoreCase = true)
        }
    }
}

class PluginSignatureVerifier {
    fun verify(layout: PluginPackageLayout, signatureInfo: PluginSignatureInfo): Boolean {
        return verifySignedContent(
            publicKeyPem = signatureInfo.publicKeyPem,
            algorithm = signatureInfo.algorithm,
            payload = layout.requireFile(signatureInfo.signedFile),
            signatureBase64 = signatureInfo.signatureBase64,
        )
    }

    fun verifySignedContent(
        publicKeyPem: String,
        algorithm: String,
        payload: ByteArray,
        signatureBase64: String,
    ): Boolean {
        val signature = Signature.getInstance(algorithm)
        signature.initVerify(parsePemPublicKey(publicKeyPem))
        signature.update(payload)
        val rawSignature = Base64.decode(signatureBase64, Base64.DEFAULT)
        return signature.verify(rawSignature)
    }

    private fun parsePemPublicKey(pem: String): PublicKey {
        val normalized = pem
            .replace("-----BEGIN PUBLIC KEY-----", "")
            .replace("-----END PUBLIC KEY-----", "")
            .replace("\\s".toRegex(), "")
        val decoded = Base64.decode(normalized, Base64.DEFAULT)
        return KeyFactory.getInstance("RSA").generatePublic(X509EncodedKeySpec(decoded))
    }
}
