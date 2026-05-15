package com.x500x.cursimple.app.download

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import kotlin.random.Random
import kotlin.system.measureTimeMillis

class MirrorDownloader(
    private val mirrorPool: DownloadMirrorPool = DownloadMirrorPool(),
    private val probeRoundSize: Int = 4,
    private val random: Random = Random.Default,
    private val userAgent: String = "CurSimple",
) {
    suspend fun downloadBytes(
        request: DownloadRequest,
        validate: (ByteArray) -> Unit = {},
    ): MirrorDownloadResult<ByteArray> = withContext(Dispatchers.IO) {
        if (request.purpose == DownloadPurpose.LocalFile) {
            return@withContext loadLocalFile(request, validate)
        }
        downloadMeasured(request) { candidate ->
            val bytes = requestBytes(candidate.url)
            validate(bytes)
            bytes
        }
    }

    suspend fun downloadFile(
        request: DownloadRequest,
        target: File,
        validate: (File) -> Unit = {},
    ): MirrorDownloadResult<File> = withContext(Dispatchers.IO) {
        if (request.purpose == DownloadPurpose.LocalFile) {
            return@withContext copyLocalFile(request, target, validate)
        }
        downloadMeasured(request) { candidate ->
            runCatching { target.delete() }
            requestFile(candidate.url, target)
            validate(target)
            target
        }
    }

    suspend fun downloadText(
        request: DownloadRequest,
        accept: String = "text/plain",
        validate: (String) -> Unit = {},
    ): MirrorDownloadResult<String> = withContext(Dispatchers.IO) {
        if (request.purpose == DownloadPurpose.LocalFile) {
            val bytesResult = loadLocalFile(request) {}
            return@withContext when (bytesResult) {
                is MirrorDownloadResult.Success -> {
                    val text = bytesResult.value.toString(Charsets.UTF_8)
                    runCatching {
                        validate(text)
                        MirrorDownloadResult.Success(text, bytesResult.candidate, bytesResult.failures)
                    }.getOrElse { error ->
                        MirrorDownloadResult.Failure(
                            message = error.message ?: "本地文件校验失败",
                            failures = bytesResult.failures + DownloadFailure("本地文件", error.message ?: "校验失败"),
                        )
                    }
                }

                is MirrorDownloadResult.Failure -> bytesResult
            }
        }
        downloadMeasured(request) { candidate ->
            val text = requestText(candidate.url, accept)
            validate(text)
            text
        }
    }

    private suspend fun <T> downloadMeasured(
        request: DownloadRequest,
        fetch: (DownloadCandidate) -> T,
    ): MirrorDownloadResult<T> = coroutineScope {
        val remaining = mirrorPool.candidates(request).toMutableList()
        val failures = mutableListOf<DownloadFailure>()
        while (remaining.isNotEmpty()) {
            val sampled = remaining
                .shuffled(random)
                .take(probeRoundSize.coerceAtLeast(1))
            remaining.removeAll(sampled.toSet())
            val measured = sampled
                .map { candidate ->
                    async {
                        runCatching {
                            var latency = 0L
                            latency = measureTimeMillis { probe(candidate.url) }
                            MeasuredDownloadCandidate(candidate, latency)
                        }.getOrElse { error ->
                            failures += DownloadFailure(candidate.sourceName, error.message ?: "测速失败")
                            null
                        }
                    }
                }
                .awaitAll()
                .filterNotNull()
                .sortedBy { it.latencyMillis }
            for (item in measured) {
                val result = runCatching { fetch(item.candidate) }
                    .getOrElse { error ->
                        failures += DownloadFailure(item.candidate.sourceName, error.message ?: "下载失败")
                        null
                    }
                if (result != null) {
                    return@coroutineScope MirrorDownloadResult.Success(
                        value = result,
                        candidate = item.candidate,
                        failures = failures.toList(),
                    )
                }
            }
        }
        MirrorDownloadResult.Failure(
            message = failures.firstOrNull()?.message ?: "没有可用下载源",
            failures = failures.toList(),
        )
    }

    private fun loadLocalFile(
        request: DownloadRequest,
        validate: (ByteArray) -> Unit,
    ): MirrorDownloadResult<ByteArray> {
        return runCatching {
            val file = if (request.url.startsWith("file:", ignoreCase = true)) {
                File(URI(request.url))
            } else {
                File(request.url)
            }
            val bytes = file.readBytes()
            validate(bytes)
            MirrorDownloadResult.Success(bytes, DownloadCandidate("本地文件", file.absolutePath))
        }.getOrElse { error ->
            MirrorDownloadResult.Failure(
                message = error.message ?: "读取本地文件失败",
                failures = listOf(DownloadFailure("本地文件", error.message ?: "读取失败")),
            )
        }
    }

    private fun copyLocalFile(
        request: DownloadRequest,
        target: File,
        validate: (File) -> Unit,
    ): MirrorDownloadResult<File> {
        return runCatching {
            val source = if (request.url.startsWith("file:", ignoreCase = true)) {
                File(URI(request.url))
            } else {
                File(request.url)
            }
            target.parentFile?.mkdirs()
            source.inputStream().use { input ->
                target.outputStream().use { output -> input.copyTo(output) }
            }
            validate(target)
            MirrorDownloadResult.Success(target, DownloadCandidate("本地文件", source.absolutePath))
        }.getOrElse { error ->
            runCatching { target.delete() }
            MirrorDownloadResult.Failure(
                message = error.message ?: "读取本地文件失败",
                failures = listOf(DownloadFailure("本地文件", error.message ?: "读取失败")),
            )
        }
    }

    private fun requestBytes(url: String): ByteArray {
        val connection = openConnection(url, "GET")
        return connection.use { conn ->
            check(conn.responseCode in 200..299) { "HTTP ${conn.responseCode}" }
            conn.inputStream.use { it.readBytes() }
        }
    }

    private fun requestText(url: String, accept: String): String {
        val connection = openConnection(url, "GET").apply {
            setRequestProperty("Accept", accept)
        }
        return connection.use { conn ->
            check(conn.responseCode in 200..299) { "HTTP ${conn.responseCode}" }
            conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        }
    }

    private fun requestFile(url: String, file: File) {
        val connection = openConnection(url, "GET")
        file.parentFile?.mkdirs()
        connection.use { conn ->
            check(conn.responseCode in 200..299) { "HTTP ${conn.responseCode}" }
            conn.inputStream.use { input ->
                file.outputStream().use { output -> input.copyTo(output) }
            }
        }
    }

    private fun probe(url: String) {
        val headStatus = runCatching {
            openConnection(url, "HEAD").use { it.responseCode }
        }.getOrNull()
        if (headStatus != null && headStatus in 200..399) {
            return
        }
        val getStatus = openConnection(url, "GET").apply {
            setRequestProperty("Range", "bytes=0-0")
        }.use { it.responseCode }
        check(getStatus in 200..399) { "HTTP $getStatus" }
    }

    private fun openConnection(url: String, method: String): HttpURLConnection {
        return (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = NETWORK_TIMEOUT_MILLIS
            readTimeout = NETWORK_TIMEOUT_MILLIS
            requestMethod = method
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", userAgent)
        }
    }

    private fun <T> HttpURLConnection.use(block: (HttpURLConnection) -> T): T {
        try {
            return block(this)
        } finally {
            disconnect()
        }
    }

    private companion object {
        const val NETWORK_TIMEOUT_MILLIS = 8_000
    }
}
