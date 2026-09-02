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
import kotlin.system.measureTimeMillis

class MirrorDownloader(
    private val labels: MirrorDownloaderLabels,
    private val mirrorPool: DownloadMirrorPool = DownloadMirrorPool(),
    private val probeRoundSize: Int = 4,
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
                            message = error.message ?: labels.localFileVerifyFailed,
                            reason = DownloadFailureReason.Thrown(error),
                            failures = bytesResult.failures +
                                DownloadFailure(labels.localFileSource, error.message ?: labels.verifyFailed),
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
        var firstError: Throwable? = null
        while (remaining.isNotEmpty()) {
            val sampled = remaining
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
                            firstError = firstError ?: error
                            failures += DownloadFailure(candidate.sourceName, error.message ?: labels.probeFailed)
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
                        firstError = firstError ?: error
                        failures += DownloadFailure(item.candidate.sourceName, error.message ?: labels.downloadFailed)
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
            message = failures.firstOrNull()?.message ?: labels.noSource,
            reason = firstError?.let { DownloadFailureReason.Thrown(it) } ?: DownloadFailureReason.NoSource,
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
            MirrorDownloadResult.Success(bytes, DownloadCandidate(labels.localFileSource, file.absolutePath))
        }.getOrElse { error ->
            MirrorDownloadResult.Failure(
                message = error.message ?: labels.localFileReadFailed,
                reason = DownloadFailureReason.Thrown(error),
                failures = listOf(DownloadFailure(labels.localFileSource, error.message ?: labels.readFailed)),
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
            MirrorDownloadResult.Success(target, DownloadCandidate(labels.localFileSource, source.absolutePath))
        }.getOrElse { error ->
            runCatching { target.delete() }
            MirrorDownloadResult.Failure(
                message = error.message ?: labels.localFileReadFailed,
                reason = DownloadFailureReason.Thrown(error),
                failures = listOf(DownloadFailure(labels.localFileSource, error.message ?: labels.readFailed)),
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
