package com.x500x.cursimple.app.download

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext
import java.io.File
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import java.util.concurrent.atomic.AtomicLong
import kotlin.system.measureTimeMillis

class MirrorDownloader(
    private val labels: MirrorDownloaderLabels,
    private val mirrorPool: DownloadMirrorPool = DownloadMirrorPool(),
    private val probeRoundSize: Int = 4,
    private val userAgent: String = "CurSimple",
    private val preferenceStore: MirrorPreferenceStore? = null,
    private val textTransport: Call.Factory = SharedHttp.text,
    transferClient: OkHttpClient = SharedHttp.transfer,
) {
    private val transfer = FastTransfer(transferClient, userAgent, preferenceStore)

    /** Process-local preferred mirror; [preferenceStore] persists it across restarts. */
    private val preferredSources = java.util.concurrent.ConcurrentHashMap<String, String>()
    private val failedTextHosts = java.util.concurrent.ConcurrentHashMap<String, Long>()

    private fun preferredCandidate(request: DownloadRequest, candidates: List<DownloadCandidate>): DownloadCandidate? {
        val key = MirrorPreferenceStore.cacheKeyOf(request)
        val name = preferredSources[key] ?: preferenceStore?.preferred(key) ?: return null
        return candidates.firstOrNull { it.sourceName == name }
    }

    private fun recordSuccess(request: DownloadRequest, sourceName: String) {
        val key = MirrorPreferenceStore.cacheKeyOf(request)
        preferredSources[key] = sourceName
        preferenceStore?.recordSuccess(key, sourceName)
    }

    private fun recordFailure(request: DownloadRequest, candidate: DownloadCandidate) {
        val key = MirrorPreferenceStore.cacheKeyOf(request)
        if (preferredSources[key] == candidate.sourceName) preferredSources.remove(key)
        preferenceStore?.recordFailure(key, candidate.sourceName)
        preferenceStore?.invalidate(MirrorPreferenceStore.hostOf(candidate.url))
    }

    /** Rank by preferred source, measured throughput and recent failures. */
    private fun rankedCandidates(request: DownloadRequest): List<DownloadCandidate> {
        val key = MirrorPreferenceStore.cacheKeyOf(request)
        return rankCandidates(
            candidates = mirrorPool.candidates(request),
            preferredName = preferredSources[key] ?: preferenceStore?.preferred(key),
            speedKBps = { host -> preferenceStore?.speedKBps(host) },
            coolingUntil = { host -> preferenceStore?.downloadFailureUntil(host) ?: 0L },
            nowMillis = System.currentTimeMillis(),
        )
    }

    private fun usesFastTransfer(request: DownloadRequest, candidates: Int): Boolean =
        request.purpose != DownloadPurpose.LocalFile && request.purpose != DownloadPurpose.DirectUrl && candidates >= 2

    suspend fun downloadBytes(
        request: DownloadRequest,
        validate: (ByteArray) -> Unit = {},
        onProgress: (downloaded: Long, total: Long) -> Unit = { _, _ -> },
    ): MirrorDownloadResult<ByteArray> = withContext(Dispatchers.IO) {
        if (request.purpose == DownloadPurpose.LocalFile) {
            return@withContext runInterruptible { loadLocalFile(request, onProgress, validate) }
        }
        val ranked = rankedCandidates(request)
        if (usesFastTransfer(request, ranked.size)) {
            // Validate each hedged response before accepting its mirror.
            val reported = AtomicLong(-1L)
            val monotonic: (Long, Long) -> Unit = { done, total ->
                // Report only the leading request's progress to keep it monotonic.
                var current = reported.get()
                while (done > current && !reported.compareAndSet(current, done)) current = reported.get()
                if (done > current) onProgress(done, total)
            }
            return@withContext when (val outcome = transfer.hedged(ranked) { candidate ->
                transfer.fetchBytes(candidate, monotonic).also(validate)
            }) {
                is HedgeOutcome.Won -> {
                    recordSuccess(request, outcome.winner.sourceName)
                    MirrorDownloadResult.Success(outcome.value, outcome.winner, outcome.failures)
                }
                is HedgeOutcome.Lost -> MirrorDownloadResult.Failure(
                    message = outcome.failures.firstOrNull()?.message ?: labels.noSource,
                    reason = outcome.firstError?.let { DownloadFailureReason.Thrown(it) } ?: DownloadFailureReason.NoSource,
                    failures = outcome.failures,
                )
            }
        }
        downloadMeasured(request) { candidate ->
            val bytes = runInterruptible { requestBytes(candidate.url, onProgress) }
            validate(bytes)
            bytes
        }
    }

    suspend fun downloadBytes(
        request: DownloadRequest,
        validate: (ByteArray) -> Unit,
    ): MirrorDownloadResult<ByteArray> = downloadBytes(request, validate, onProgress = { _, _ -> })

    /** Download into a file. Unknown totals use -1; mirror retries restart progress at zero. */
    suspend fun downloadFile(
        request: DownloadRequest,
        target: File,
        validate: (File) -> Unit = {},
        onProgress: (downloadedBytes: Long, totalBytes: Long) -> Unit = { _, _ -> },
    ): MirrorDownloadResult<File> = withContext(Dispatchers.IO) {
        if (request.purpose == DownloadPurpose.LocalFile) {
            return@withContext copyLocalFile(request, target, validate)
        }
        val ranked = rankedCandidates(request)
        val fastFailures = mutableListOf<DownloadFailure>()
        if (usesFastTransfer(request, ranked.size)) {
            val winner = try {
                fastDownloadToFile(ranked, target, onProgress, validate)
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (error: Exception) {
                // Fallback to full-file mirror downloads when probing, chunking or validation fails.
                fastFailures += DownloadFailure("fast", error.message ?: labels.downloadFailed)
                runCatching { target.delete() }
                null
            }
            if (winner != null) {
                recordSuccess(request, winner.sourceName)
                return@withContext MirrorDownloadResult.Success(target, winner, emptyList())
            }
        }
        downloadMeasured(request) { candidate ->
            runCatching { target.delete() }
            requestFile(candidate.url, target, onProgress)
            validate(target)
            target
        }
    }

    /**
     * Choose ranged parallel transfer or small-file hedging; return the source only after
     * [validate] succeeds.
     */
    private suspend fun fastDownloadToFile(
        ranked: List<DownloadCandidate>,
        target: File,
        onProgress: (downloadedBytes: Long, totalBytes: Long) -> Unit,
        validate: (File) -> Unit,
    ): DownloadCandidate {
        val probes = transfer.probe(ranked)
        check(probes.isNotEmpty()) { labels.probeFailed }
        val ranged = probes.filter { it.acceptsRange && it.total > 0L }.groupBy { it.total }
            .maxByOrNull { (_, group) -> group.size }
        runCatching { target.delete() }
        if (ranged != null && ranged.key >= FastTransfer.SEGMENT_MIN_BYTES) {
            val winner = transfer.segmentedToFile(ranged.value, ranged.key, target, onProgress)
            validate(target)
            return winner
        }
        // Large files without Range support use sequential fallback to avoid duplicate full downloads.
        check(probes.all { it.total in 1 until FastTransfer.SEGMENT_MIN_BYTES }) { "No mirror supports ranged download" }
        val order = probes.sortedBy { it.firstByteMillis }.map { it.candidate }
        val outcome = transfer.hedged(order) { candidate ->
            transfer.fetchBytes(candidate, onProgress)
        }
        check(outcome is HedgeOutcome.Won) { labels.downloadFailed }
        target.parentFile?.mkdirs()
        target.writeBytes(outcome.value)
        validate(target)
        return outcome.winner
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
        downloadRaced(request) { candidate ->
            val text = requestText(candidate.url, accept)
            validate(text)
            text
        }
    }

    /**
     * Race small-file reads directly; include preferred mirrors and the origin in the first
     * round.
     */
    private suspend fun <T> downloadRaced(
        request: DownloadRequest,
        fetch: suspend (DownloadCandidate) -> T,
    ): MirrorDownloadResult<T> = coroutineScope {
        val candidates = fastTextCandidates(mirrorPool.candidates(request).sortedBy { textHostCooling(it) })
        val preferred = preferredCandidate(request, candidates)?.takeUnless { textHostCooling(it) }
        val rounds = raceRounds(
            candidates = candidates,
            preferredUrl = preferred?.url,
            roundSize = probeRoundSize.coerceAtLeast(1),
        )
        val failures = java.util.Collections.synchronizedList(mutableListOf<DownloadFailure>())
        val firstError = java.util.concurrent.atomic.AtomicReference<Throwable?>(null)
        for (round in rounds) {
            val winner = raceRound(round, fetch, failures, firstError)
            if (winner != null) {
                recordSuccess(request, winner.first.sourceName)
                return@coroutineScope MirrorDownloadResult.Success(
                    value = winner.second,
                    candidate = winner.first,
                    failures = failures.toList(),
                )
            }
            if (preferred != null && round.any { it.sourceName == preferred.sourceName }) {
                recordFailure(request, preferred)
            }
        }
        MirrorDownloadResult.Failure(
            message = failures.firstOrNull()?.message ?: labels.noSource,
            reason = firstError.get()?.let { DownloadFailureReason.Thrown(it) } ?: DownloadFailureReason.NoSource,
            failures = failures.toList(),
        )
    }

    /** Return the first success and cancel losers; return null when every candidate fails. */
    private suspend fun <T> raceRound(
        candidates: List<DownloadCandidate>,
        fetch: suspend (DownloadCandidate) -> T,
        failures: MutableList<DownloadFailure>,
        firstError: java.util.concurrent.atomic.AtomicReference<Throwable?>,
    ): Pair<DownloadCandidate, T>? = coroutineScope {
        val winner = kotlinx.coroutines.CompletableDeferred<Pair<DownloadCandidate, T>?>()
        val jobs = candidates.map { candidate ->
            launch {
                runCatching { fetch(candidate) }
                    .onSuccess { winner.complete(candidate to it) }
                    .onFailure { error ->
                        if (error is InterruptedException ||
                            error is java.io.InterruptedIOException ||
                            error is kotlinx.coroutines.CancellationException
                        ) {
                            return@launch
                        }
                        firstError.compareAndSet(null, error)
                        failures += DownloadFailure(candidate.sourceName, error.message ?: labels.downloadFailed)
                        val host = MirrorPreferenceStore.hostOf(candidate.url)
                        failedTextHosts[host] = System.currentTimeMillis() + 5 * 60_000L
                        preferenceStore?.recordTextFailure(host)
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
        fetch: suspend (DownloadCandidate) -> T,
    ): MirrorDownloadResult<T> = coroutineScope {
        val allCandidates = mirrorPool.candidates(request)
        val failures = mutableListOf<DownloadFailure>()
        var firstError: Throwable? = null

        // Try the preferred mirror directly before probing alternatives.
        preferredCandidate(request, allCandidates)?.let { preferred ->
            val direct = runCatching { fetch(preferred) }.getOrElse { error ->
                if (error is CancellationException) throw error
                firstError = error
                failures += DownloadFailure(preferred.sourceName, error.message ?: labels.downloadFailed)
                recordFailure(request, preferred)
                null
            }
            if (direct != null) {
                return@coroutineScope MirrorDownloadResult.Success(direct, preferred, failures.toList())
            }
        }

        val remaining = allCandidates.toMutableList()
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
                            preferenceStore?.recordProbe(MirrorPreferenceStore.hostOf(candidate.url), latency)
                            MeasuredDownloadCandidate(candidate, latency)
                        }.getOrElse { error ->
                            if (error is CancellationException) throw error
                            firstError = firstError ?: error
                            preferenceStore?.recordProbe(MirrorPreferenceStore.hostOf(candidate.url), null)
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
                        if (error is CancellationException) throw error
                        firstError = firstError ?: error
                        failures += DownloadFailure(item.candidate.sourceName, error.message ?: labels.downloadFailed)
                        null
                    }
                if (result != null) {
                    recordSuccess(request, item.candidate.sourceName)
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

    private fun textHostCooling(candidate: DownloadCandidate): Boolean {
        val host = MirrorPreferenceStore.hostOf(candidate.url)
        val until = maxOf(failedTextHosts[host] ?: 0L, preferenceStore?.textFailureUntil(host) ?: 0L)
        return until > System.currentTimeMillis()
    }

    private fun loadLocalFile(
        request: DownloadRequest,
        onProgress: (Long, Long) -> Unit = { _, _ -> },
        validate: (ByteArray) -> Unit,
    ): MirrorDownloadResult<ByteArray> {
        return runCatching {
            val file = if (request.url.startsWith("file:", ignoreCase = true)) {
                File(URI(request.url))
            } else {
                File(request.url)
            }
            val bytes = file.inputStream().use { readBytes(it, file.length(), onProgress) }
            validate(bytes)
            MirrorDownloadResult.Success(bytes, DownloadCandidate(labels.localFileSource, file.absolutePath))
        }.getOrElse { error ->
            if (error is CancellationException) throw error
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

    private fun requestBytes(url: String, onProgress: (Long, Long) -> Unit): ByteArray {
        val connection = openConnection(url, "GET")
        return connection.use { conn ->
            check(conn.responseCode in 200..299) { "HTTP ${conn.responseCode}" }
            conn.inputStream.use { readBytes(it, conn.contentLengthLong, onProgress) }
        }
    }

    private fun readBytes(input: InputStream, contentLength: Long, onProgress: (Long, Long) -> Unit): ByteArray {
        val total = contentLength.takeIf { it >= 0L } ?: -1L
        if (Thread.currentThread().isInterrupted) throw CancellationException("Download cancelled")
        onProgress(0L, total)
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(DOWNLOAD_BUFFER_BYTES)
        var downloaded = 0L
        var lastReportedAt = System.nanoTime() / 1_000_000L - PROGRESS_REPORT_INTERVAL_MILLIS
        while (true) {
            if (Thread.currentThread().isInterrupted) throw CancellationException("Download cancelled")
            val read = input.read(buffer)
            if (Thread.currentThread().isInterrupted) throw CancellationException("Download cancelled")
            if (read < 0) break
            output.write(buffer, 0, read)
            downloaded += read
            val now = System.nanoTime() / 1_000_000L
            if (now - lastReportedAt >= PROGRESS_REPORT_INTERVAL_MILLIS) {
                lastReportedAt = now
                onProgress(downloaded, total)
            }
        }
        if (total >= 0L && downloaded != total) throw IOException("Incomplete download: $downloaded / $total bytes")
        if (Thread.currentThread().isInterrupted) throw CancellationException("Download cancelled")
        onProgress(downloaded, total)
        return output.toByteArray()
    }

    private suspend fun requestText(url: String, accept: String): String = suspendCancellableCoroutine { continuation ->
        val call = textTransport.newCall(Request.Builder().url(url)
            .header("User-Agent", userAgent).header("Accept", accept)
            .header("Cache-Control", "no-cache").header("Pragma", "no-cache").build())
        continuation.invokeOnCancellation { call.cancel() }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, error: IOException) {
                if (continuation.isActive) continuation.resumeWithException(error)
            }
            override fun onResponse(call: Call, response: Response) {
                try {
                    val text = response.use {
                        check(it.isSuccessful) { "HTTP ${it.code}" }
                        it.body.string()
                    }
                    if (continuation.isActive) continuation.resume(text)
                } catch (error: Exception) {
                    if (continuation.isActive) continuation.resumeWithException(error)
                }
            }
        })
    }

    private fun requestFile(
        url: String,
        file: File,
        onProgress: (downloadedBytes: Long, totalBytes: Long) -> Unit = { _, _ -> },
    ) {
        val connection = openConnection(url, "GET")
        file.parentFile?.mkdirs()
        connection.use { conn ->
            check(conn.responseCode in 200..299) { "HTTP ${conn.responseCode}" }
            val total = conn.contentLengthLong.takeIf { it > 0L } ?: -1L
            onProgress(0L, total)
            conn.inputStream.use { input ->
                file.outputStream().use { output ->
                    val buffer = ByteArray(DOWNLOAD_BUFFER_BYTES)
                    var downloaded = 0L
                    // Throttle progress by elapsed time to limit Compose recompositions.
                    var lastReportedAt = 0L
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        downloaded += read
                        val now = System.currentTimeMillis()
                        if (now - lastReportedAt >= PROGRESS_REPORT_INTERVAL_MILLIS) {
                            lastReportedAt = now
                            onProgress(downloaded, total)
                        }
                    }
                    output.flush()
                    // Publish final progress after the stream completes.
                    onProgress(downloaded, if (total > 0L) total else downloaded)
                }
            }
        }
    }

    private fun probe(url: String) {
        // Bound probe timeouts so one dead mirror cannot stall an entire round.
        val headStatus = runCatching {
            openConnection(url, "HEAD", PROBE_TIMEOUT_MILLIS).use { it.responseCode }
        }.getOrNull()
        if (headStatus != null && headStatus in 200..399) {
            return
        }
        val getStatus = openConnection(url, "GET", PROBE_TIMEOUT_MILLIS).apply {
            setRequestProperty("Range", "bytes=0-0")
        }.use { it.responseCode }
        check(getStatus in 200..399) { "HTTP $getStatus" }
    }

    private fun openConnection(
        url: String,
        method: String,
        timeoutMillis: Int = NETWORK_TIMEOUT_MILLIS,
    ): HttpURLConnection {
        return (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = timeoutMillis
            readTimeout = timeoutMillis
            requestMethod = method
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", userAgent)
            // Ask intermediaries to revalidate cached content.
            setRequestProperty("Cache-Control", "no-cache")
            setRequestProperty("Pragma", "no-cache")
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
        const val PROBE_TIMEOUT_MILLIS = 4_000
        private const val DOWNLOAD_BUFFER_BYTES = 64 * 1024
        private const val PROGRESS_REPORT_INTERVAL_MILLIS = 120L
    }
}

/**
 * Batch concurrent candidates, including the preferred mirror in the first round rather than
 * giving it an exclusive timeout window.
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
    val rest = candidates.filterNot { it.url == preferred.url }
    val firstRound = listOf(preferred) + rest.take(size - 1)
    return listOf(firstRound) + rest.drop(size - 1).chunked(size)
}

internal fun fastTextCandidates(candidates: List<DownloadCandidate>): List<DownloadCandidate> {
    val origin = candidates.firstOrNull { it.sourceName in setOf(DownloadSourceIds.GITHUB_ORIGIN, DownloadSourceIds.ORIGIN) }
        ?: return candidates
    val mirrors = candidates.filterNot { it.url == origin.url }
    return mirrors.take(2) + origin + mirrors.drop(2)
}
