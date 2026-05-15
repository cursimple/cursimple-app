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
    private val fetchText: suspend (String) -> String = { url -> defaultFetchText(client, url) },
    private val downloadBytes: suspend (String) -> ByteArray = { url -> defaultDownloadBytes(client, url) },
) {
    suspend fun fetch(url: String): MarketIndexPayload = withContext(Dispatchers.IO) {
        val raw = fetchText(url)
        val signedIndex = runCatching {
            json.decodeFromString<SignedMarketIndex>(raw)
        }.getOrNull()
        if (signedIndex != null) {
            val canonicalPayload = json.encodeToString(signedIndex.payload).toByteArray()
            val verified = signatureVerifier.verifySignedContent(
                publicKeyPem = signedIndex.signature.publicKeyPem,
                algorithm = signedIndex.signature.algorithm,
                payload = canonicalPayload,
                signatureBase64 = signedIndex.signature.signatureBase64,
            )
            require(verified) { "市场索引签名无效" }
            return@withContext signedIndex.payload
        }
        runCatching { json.decodeFromString<MarketIndexPayload>(raw) }
            .getOrElse {
                MarketIndexPayload(
                    indexId = "unsupported",
                    generatedAt = "",
                    plugins = emptyList(),
                )
            }
    }

    suspend fun downloadPackage(url: String): ByteArray = withContext(Dispatchers.IO) {
        downloadBytes(url)
    }

    private companion object {
        fun defaultFetchText(client: OkHttpClient, url: String): String {
            val request = Request.Builder().url(url).build()
            client.newCall(request).execute().use { response ->
                check(response.isSuccessful) { "加载市场索引失败: ${response.code}" }
                return response.body.string()
            }
        }

        fun defaultDownloadBytes(client: OkHttpClient, url: String): ByteArray {
            val request = Request.Builder().url(url).build()
            client.newCall(request).execute().use { response ->
                check(response.isSuccessful) { "下载插件包失败: ${response.code}" }
                return response.body.bytes()
            }
        }
    }
}
