package com.x500x.cursimple.app.download

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.runInterruptible
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
    private val preferenceStore: MirrorPreferenceStore? = null,
) {
    /** 进程内的上次成功镜像名；落盘的偏好在 [preferenceStore] 里，重启后仍生效。 */
    private val preferredSources = java.util.concurrent.ConcurrentHashMap<String, String>()

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

    /**
     * 下载到文件。
     *
     * [onProgress] 报告已下载字节数与总字节数，服务端没给 Content-Length 时总数为 -1。
     * 换镜像重试会从头下载，所以每次进入都会先回一次 0，界面据此重置进度条。
     */
    suspend fun downloadFile(
        request: DownloadRequest,
        target: File,
        validate: (File) -> Unit = {},
        onProgress: (downloadedBytes: Long, totalBytes: Long) -> Unit = { _, _ -> },
    ): MirrorDownloadResult<File> = withContext(Dispatchers.IO) {
        if (request.purpose == DownloadPurpose.LocalFile) {
            return@withContext copyLocalFile(request, target, validate)
        }
        downloadMeasured(request) { candidate ->
            runCatching { target.delete() }
            requestFile(candidate.url, target, onProgress)
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
        val candidates = mirrorPool.candidates(request)
        val preferred = preferredCandidate(request, candidates)
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
            // 记住的镜像失效时清除记录，让下次直接竞速全部镜像
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
                // runInterruptible：赢家确定后取消落败协程时中断其线程，Android 的 HttpURLConnection
                // 会立即中止阻塞的 socket 读，否则 coroutineScope 要等最慢镜像或 8s 超时才返回
                runCatching { runInterruptible { fetch(candidate) } }
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
        val allCandidates = mirrorPool.candidates(request)
        val failures = mutableListOf<DownloadFailure>()
        var firstError: Throwable? = null

        // 记住的镜像直接下载，省掉探测那一轮往返；失败才落回逐批探测
        preferredCandidate(request, allCandidates)?.let { preferred ->
            val direct = runCatching { fetch(preferred) }.getOrElse { error ->
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
                    // 按时间间隔而不是按块数上报：块小的时候一秒能刷几百次，
                    // 每块都回调会把重组压垮，进度条反而更卡。
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
                    // 收尾补一次，保证界面停在 100% 而不是最后一次采样的数字
                    onProgress(downloaded, if (total > 0L) total else downloaded)
                }
            }
        }
    }

    private fun probe(url: String) {
        // 探测用更短的超时：一批候选并发探测后要 awaitAll 排序，死镜像若按 8s 连接超时会把整轮拖满
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
            // 中间代理各自按 URL 缓存，源站更新后用户那边的边缘节点可能还发旧文件。
            // 肯听这两个头的代理会回源，不听的至少不会更糟。
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
 * 把镜像候选切成一轮轮并发请求。
 *
 * 上次成功的镜像排在第一轮最前，和其余几个一起竞速；其余按 [roundSize] 分批，
 * 免得一次把十几个镜像全打一遍。
 *
 * 以前记住的镜像单独占一轮，它要是卡住（国内代理说挂就挂），要等连接加读取两段超时
 * 十几秒才轮到别的镜像，导课页第一次检索插件就卡在这里。小文件多发三个请求不值什么。
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
