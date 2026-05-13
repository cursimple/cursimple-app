package com.x500x.cursimple.core.plugin.market

import com.x500x.cursimple.core.plugin.security.PluginSignatureVerifier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request

class MarketIndexRepository(
    private val client: OkHttpClient = OkHttpClient(),
    private val json: Json = Json { ignoreUnknownKeys = true; encodeDefaults = true },
    private val signatureVerifier: PluginSignatureVerifier = PluginSignatureVerifier(),
) {
    suspend fun fetch(url: String): MarketIndexPayload = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(url).build()
        client.newCall(request).execute().use { response ->
            check(response.isSuccessful) { "加载市场索引失败: ${response.code}" }
            val raw = response.body.string()
            val signedIndex = json.decodeFromString<SignedMarketIndex>(raw)
            val canonicalPayload = json.encodeToString(signedIndex.payload).toByteArray()
            val verified = signatureVerifier.verifySignedContent(
                publicKeyPem = signedIndex.signature.publicKeyPem,
                algorithm = signedIndex.signature.algorithm,
                payload = canonicalPayload,
                signatureBase64 = signedIndex.signature.signatureBase64,
            )
            require(verified) { "市场索引签名无效" }
            signedIndex.payload
        }
    }

    suspend fun downloadPackage(url: String): ByteArray = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(url).build()
        client.newCall(request).execute().use { response ->
            check(response.isSuccessful) { "下载插件包失败: ${response.code}" }
            response.body.bytes()
        }
    }
}
