package com.x500x.cursimple.app.download

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.joinAll
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
    /** 记住每类下载上次成功的镜像，下次先单独试它。 */
    private val preferredSources = java.util.concurrent.ConcurrentHashMap<DownloadPurpose, String>()

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
        // 文本体积小，直接并发取最快返回的那个，省掉探测那一轮往返
        downloadRaced(request) { candidate ->
            val text = requestText(candidate.url, accept)
            validate(text)
            text
        }
    }

    /**
     * 并发向若干镜像发起同一次请求，取最先成功的那个。
     *
     * 探测再下载要走两次往返，而小文件的下载本身就等价于探测。
     * 上次成功的镜像排在最前单独试一轮，命中时整次只有一个请求。
     */
    private suspend fun <T> downloadRaced(
        request: DownloadRequest,
        fetch: (DownloadCandidate) -> T,
    ): MirrorDownloadResult<T> = coroutineScope {
        val rounds = raceRounds(
            candidates = mirrorPool.candidates(request),
            preferredUrl = preferredSources[request.purpose],
            roundSize = probeRoundSize.coerceAtLeast(1),
        )
        val failures = java.util.Collections.synchronizedList(mutableListOf<DownloadFailure>())
        val firstError = java.util.concurrent.atomic.AtomicReference<Throwable?>(null)
        for (round in rounds) {
            val winner = raceRound(round, fetch, failures, firstError)
            if (winner != null) {
                preferredSources[request.purpose] = winner.first.url
                return@coroutineScope MirrorDownloadResult.Success(
                    value = winner.second,
                    candidate = winner.first,
                    failures = failures.toList(),
                )
            }
        }
        MirrorDownloadResult.Failure(
            message = failures.firstOrNull()?.message ?: labels.noSource,
            reason = firstError.get()?.let { DownloadFailureReason.Thrown(it) } ?: DownloadFailureReason.NoSource,
            failures = failures.toList(),
        )
    }

    /** 同时发起一轮请求，任一成功即返回并取消其余；全部失败时返回 null。 */
    private suspend fun <T> raceRound(
        candidates: List<DownloadCandidate>,
        fetch: (DownloadCandidate) -> T,
        failures: MutableList<DownloadFailure>,
        firstError: java.util.concurrent.atomic.AtomicReference<Throwable?>,
    ): Pair<DownloadCandidate, T>? = coroutineScope {
        val winner = kotlinx.coroutines.CompletableDeferred<Pair<DownloadCandidate, T>?>()
        val jobs = candidates.map { candidate ->
            launch {
                runCatching { fetch(candidate) }
                    .onSuccess { winner.complete(candidate to it) }
                    .onFailure { error ->
                        firstError.compareAndSet(null, error)
                        failures += DownloadFailure(candidate.sourceName, error.message ?: labels.downloadFailed)
                    }
            }
        }
        val watcher = launch {
            jobs.joinAll()
            winner.complete(null)
        }
        val result = winner.await()
        jobs.forEach { it.cancel() }
        watcher.cancel()
        result
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

/**
 * 把镜像候选切成一轮轮并发请求。
 *
 * 上次成功的镜像单独占第一轮，命中时整次只发一个请求；其余按 [roundSize] 分批，
 * 免得一次把十几个镜像全打一遍。
 */
internal fun raceRounds(
    candidates: List<DownloadCandidate>,
    preferredUrl: String?,
    roundSize: Int,
): List<List<DownloadCandidate>> {
    if (candidates.isEmpty()) return emptyList()
    val size = roundSize.coerceAtLeast(1)
    val preferred = candidates.firstOrNull { it.url == preferredUrl }
        ?: return candidates.chunked(size)
    return listOf(listOf(preferred)) + candidates.filterNot { it.url == preferred.url }.chunked(size)
}
