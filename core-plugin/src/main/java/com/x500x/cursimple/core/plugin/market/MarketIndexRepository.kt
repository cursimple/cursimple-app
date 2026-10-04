package com.x500x.cursimple.core.plugin.market

import com.x500x.cursimple.core.plugin.security.PluginSignatureVerifier
import com.x500x.cursimple.core.plugin.R
import com.x500x.cursimple.core.plugin.pluginCheck
import com.x500x.cursimple.core.plugin.pluginRequire
import com.x500x.cursimple.core.plugin.market.github.readDownloadBody
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class MarketIndexRepository(
    private val client: OkHttpClient = OkHttpClient(),
    private val json: Json = Json { ignoreUnknownKeys = true; encodeDefaults = true },
    private val signatureVerifier: PluginSignatureVerifier = PluginSignatureVerifier(),
    private val fetchText: suspend (String) -> String = { url -> defaultFetchText(client, url) },
    private val downloadBytes: (suspend (String) -> ByteArray)? = null,
    private val downloadBytesWithProgress: (suspend (String, (Long, Long) -> Unit) -> ByteArray)? = null,
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
            pluginRequire(verified, R.string.plugin_error_market_index_signature_invalid)
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

    suspend fun fetchComponentIndex(url: String): ComponentMarketIndexPayload = withContext(Dispatchers.IO) {
        val raw = fetchText(url)
        runCatching { json.decodeFromString<ComponentMarketIndexPayload>(raw) }
            .getOrElse {
                ComponentMarketIndexPayload(
                    indexId = "unsupported",
                    generatedAt = "",
                    components = emptyList(),
                )
            }
    }

    suspend fun downloadPackage(
        url: String,
        onProgress: (downloaded: Long, total: Long) -> Unit = { _, _ -> },
    ): ByteArray = withContext(Dispatchers.IO) {
        downloadBytesWithProgress?.let { return@withContext it(url, onProgress) }
        downloadBytes?.let { legacy ->
            onProgress(0L, -1L)
            val bytes = legacy(url)
            currentCoroutineContext().ensureActive()
            onProgress(bytes.size.toLong(), -1L)
            return@withContext bytes
        }
        defaultDownloadBytes(client, url, onProgress)
    }

    private companion object {
        fun defaultFetchText(client: OkHttpClient, url: String): String {
            val request = Request.Builder().url(url).build()
            client.newCall(request).execute().use { response ->
                pluginCheck(
                    response.isSuccessful,
                    R.string.plugin_error_market_index_load_failed,
                    response.code,
                )
                return response.body.string()
            }
        }

        suspend fun defaultDownloadBytes(client: OkHttpClient, url: String, onProgress: (Long, Long) -> Unit): ByteArray =
            suspendCancellableCoroutine { continuation ->
                val request = Request.Builder().url(url).build()
                val call = client.newCall(request)
                continuation.invokeOnCancellation { call.cancel() }
                call.enqueue(object : Callback {
                    override fun onFailure(call: Call, e: IOException) {
                        if (continuation.isActive) continuation.resumeWithException(e)
                    }

                    override fun onResponse(call: Call, response: Response) {
                        response.use {
                            if (!continuation.isActive) return
                            try {
                                pluginCheck(response.isSuccessful, R.string.plugin_error_package_download_failed, response.code)
                                val bytes = readDownloadBody(response.body, onProgress) {
                                    if (!continuation.isActive) throw CancellationException("Download cancelled")
                                }
                                continuation.resume(bytes)
                            } catch (error: Exception) {
                                if (continuation.isActive) continuation.resumeWithException(error)
                            }
                        }
                    }
                })
            }
    }
}
