package com.x500x.cursimple.app.download

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.util.ArrayDeque
import java.util.concurrent.atomic.AtomicLong

/** First successful hedged request wins; losing requests are cancelled. */
internal sealed interface HedgeOutcome<out T> {
    data class Won<T>(val value: T, val winner: DownloadCandidate, val failures: List<DownloadFailure>) : HedgeOutcome<T>
    data class Lost(val failures: List<DownloadFailure>, val firstError: Throwable?) : HedgeOutcome<Nothing>
}

internal data class RangeProbe(
    val candidate: DownloadCandidate,
    val total: Long,
    val acceptsRange: Boolean,
    val firstByteMillis: Long,
)

/**
 * Stagger small-file requests; parallelize ranged large-file transfers across mirrors. Reassign
 * stalled chunks and validate the assembled file before accepting it.
 */
internal class FastTransfer(
    private val client: OkHttpClient,
    private val userAgent: String,
    private val store: MirrorPreferenceStore?,
    private val clock: () -> Long = { System.nanoTime() / 1_000_000L },
) {

    suspend fun <T> hedged(
        ordered: List<DownloadCandidate>,
        staggerMillis: Long = HEDGE_STAGGER_MILLIS,
        maxParallel: Int = HEDGE_MAX_PARALLEL,
        fetch: suspend (DownloadCandidate) -> T,
    ): HedgeOutcome<T> = coroutineScope {
        if (ordered.isEmpty()) return@coroutineScope HedgeOutcome.Lost(emptyList(), null)
        val results = Channel<Pair<DownloadCandidate, Result<T>>>(Channel.UNLIMITED)
        val jobs = mutableListOf<Job>()
        val failures = mutableListOf<DownloadFailure>()
        var firstError: Throwable? = null
        var next = 0
        var running = 0

        fun launchNext() {
            val candidate = ordered[next++]
            running++
            jobs += launch {
                val outcome = runCatching { fetch(candidate) }
                // Winner cancellation is not a mirror failure.
                if (outcome.exceptionOrNull() is CancellationException && !isActive) return@launch
                results.trySend(candidate to outcome)
            }
        }

        launchNext()
        while (true) {
            val received = if (next < ordered.size && running < maxParallel) {
                withTimeoutOrNull(staggerMillis) { results.receive() }
            } else {
                results.receive()
            }
            if (received == null) {
                launchNext()
                continue
            }
            running--
            val (candidate, outcome) = received
            if (outcome.isSuccess) {
                jobs.forEach { it.cancel() }
                results.close()
                return@coroutineScope HedgeOutcome.Won(outcome.getOrThrow(), candidate, failures.toList())
            }
            val error = outcome.exceptionOrNull()!!
            if (error is CancellationException) throw error
            firstError = firstError ?: error
            failures += DownloadFailure(candidate.sourceName, error.message ?: error.javaClass.simpleName)
            store?.recordDownloadFailure(MirrorPreferenceStore.hostOf(candidate.url))
            if (next < ordered.size) launchNext() else if (running == 0) {
                results.close()
                return@coroutineScope HedgeOutcome.Lost(failures.toList(), firstError)
            }
        }
        @Suppress("UNREACHABLE_CODE")
        HedgeOutcome.Lost(failures.toList(), firstError)
    }

    /** Return responsive probes after a bounded grace period; return empty if none respond. */
    suspend fun probe(candidates: List<DownloadCandidate>): List<RangeProbe> = coroutineScope {
        val wave = candidates.take(PROBE_WAVE)
        if (wave.isEmpty()) return@coroutineScope emptyList()
        val answers = Channel<Result<RangeProbe?>>(Channel.UNLIMITED)
        val jobs = wave.map { candidate ->
            launch { answers.trySend(runCatching { probeOne(candidate) }) }
        }
        val got = mutableListOf<RangeProbe>()
        var pending = wave.size
        var deadline = Long.MAX_VALUE
        while (pending > 0) {
            val remaining = if (deadline == Long.MAX_VALUE) PROBE_TIMEOUT_MILLIS else (deadline - clock()).coerceAtLeast(0L)
            val outcome = withTimeoutOrNull(remaining) { answers.receive() } ?: break
            pending--
            val answer = outcome.getOrNull()
            if (answer != null) {
                got += answer
                if (deadline == Long.MAX_VALUE) deadline = clock() + PROBE_GRACE_MILLIS
            }
        }
        jobs.forEach { it.cancel() }
        answers.close()
        got
    }

    private suspend fun probeOne(candidate: DownloadCandidate): RangeProbe? {
        val started = clock()
        return execute(request(candidate.url).header("Range", "bytes=0-0").build()) { response ->
            val firstByte = clock() - started
            when {
                response.code == 206 -> {
                    val total = response.header("Content-Range")?.substringAfterLast('/')?.toLongOrNull() ?: -1L
                    RangeProbe(candidate, total, acceptsRange = total > 0L, firstByteMillis = firstByte)
                }
                response.code in 200..299 ->
                    // For servers ignoring Range, inspect headers and close without reading the full body.
                    RangeProbe(candidate, response.body.contentLength(), acceptsRange = false, firstByteMillis = firstByte)
                else -> null
            }
        }
    }

    suspend fun fetchBytes(
        candidate: DownloadCandidate,
        onProgress: (downloaded: Long, total: Long) -> Unit,
    ): ByteArray {
        val started = clock()
        val bytes = execute(request(candidate.url).build()) { response ->
            check(response.code in 200..299) { "HTTP ${response.code}" }
            val total = response.body.contentLength()
            onProgress(0L, total)
            val out = java.io.ByteArrayOutputStream(if (total in 1..MAX_IN_MEMORY) total.toInt() else 32 * 1024)
            val buffer = ByteArray(BUFFER_BYTES)
            val input = response.body.byteStream()
            var downloaded = 0L
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                out.write(buffer, 0, read)
                downloaded += read
                onProgress(downloaded, total)
            }
            if (total >= 0L && downloaded != total) throw IOException("Incomplete download: $downloaded / $total bytes")
            out.toByteArray()
        }
        recordSpeed(candidate, bytes.size.toLong(), clock() - started)
        return bytes
    }

    /**
     * Download [target] from Range-capable [mirrors] with matching size; return the largest
     * byte contributor.
     */
    suspend fun segmentedToFile(
        mirrors: List<RangeProbe>,
        total: Long,
        target: File,
        onProgress: (downloaded: Long, total: Long) -> Unit,
    ): DownloadCandidate {
        require(mirrors.isNotEmpty() && total > 0L)
        target.parentFile?.mkdirs()
        val chunkSize = chunkSizeFor(total)
        val state = SegmentState(((total + chunkSize - 1) / chunkSize).toInt(), clock)
        val downloaded = AtomicLong(0L)
        val perMirrorBytes = mirrors.associate { it.candidate.url to AtomicLong(0L) }
        val perMirrorMillis = mirrors.associate { it.candidate.url to AtomicLong(0L) }
        RandomAccessFile(target, "rw").use { file ->
            file.setLength(total)
            val channel = file.channel
            onProgress(0L, total)
            coroutineScope {
                val ticker = launch {
                    while (isActive) {
                        delay(PROGRESS_INTERVAL_MILLIS)
                        onProgress(downloaded.get().coerceAtMost(total), total)
                    }
                }
                try {
                    val connectionsPerMirror = if (mirrors.size == 1) 2 else 1
                    val slots = mirrors.flatMap { probe -> List(connectionsPerMirror) { probe.candidate } }.take(MAX_CONNECTIONS)
                    slots.map { candidate ->
                        async {
                            segmentWorker(candidate, total, chunkSize, state, channel, downloaded, perMirrorBytes, perMirrorMillis)
                        }
                    }.awaitAll()
                } finally {
                    ticker.cancel()
                }
            }
            if (!state.allDone()) throw IOException("Download incomplete: ${state.doneCount()} / ${state.chunks} chunks")
            channel.force(false)
        }
        onProgress(total, total)
        mirrors.forEach { probe ->
            val bytes = perMirrorBytes.getValue(probe.candidate.url).get()
            val millis = perMirrorMillis.getValue(probe.candidate.url).get()
            if (bytes > 0L) recordSpeed(probe.candidate, bytes, millis)
        }
        return mirrors.maxByOrNull { perMirrorBytes.getValue(it.candidate.url).get() }!!.candidate
    }

    private suspend fun segmentWorker(
        candidate: DownloadCandidate,
        total: Long,
        chunkSize: Long,
        state: SegmentState,
        channel: java.nio.channels.FileChannel,
        downloaded: AtomicLong,
        perMirrorBytes: Map<String, AtomicLong>,
        perMirrorMillis: Map<String, AtomicLong>,
    ) {
        var consecutiveFailures = 0
        while (!state.allDone()) {
            val index = state.take()
            if (index == null) {
                // Keep idle workers alive while other workers can return failed chunks.
                delay(WORKER_IDLE_MILLIS)
                continue
            }
            val start = index * chunkSize
            val end = minOf(start + chunkSize, total) - 1
            val begun = clock()
            var counted = 0L
            try {
                execute(request(candidate.url).header("Range", "bytes=$start-$end").build()) { response ->
                    check(response.code == 206) { "HTTP ${response.code}" }
                    val range = response.header("Content-Range").orEmpty()
                    check(range.startsWith("bytes $start-$end/")) { "Unexpected range: $range" }
                    val input = response.body.byteStream()
                    val buffer = ByteArray(BUFFER_BYTES)
                    var position = start
                    while (position <= end) {
                        if (state.isDone(index)) throw ChunkSuperseded()
                        val read = input.read(buffer, 0, minOf(buffer.size.toLong(), end - position + 1).toInt())
                        if (read < 0) throw IOException("Chunk ended early at $position")
                        channel.write(ByteBuffer.wrap(buffer, 0, read), position)
                        position += read
                        counted += read
                        downloaded.addAndGet(read.toLong())
                    }
                }
                if (state.complete(index)) {
                    perMirrorBytes.getValue(candidate.url).addAndGet(end - start + 1)
                } else {
                    // A losing hedge must not count completed bytes twice.
                    downloaded.addAndGet(-counted)
                }
                perMirrorMillis.getValue(candidate.url).addAndGet(clock() - begun)
                consecutiveFailures = 0
            } catch (cancel: CancellationException) {
                downloaded.addAndGet(-counted)
                state.release(index)
                throw cancel
            } catch (superseded: ChunkSuperseded) {
                downloaded.addAndGet(-counted)
            } catch (error: Exception) {
                downloaded.addAndGet(-counted)
                val attempts = state.fail(index)
                store?.recordDownloadFailure(MirrorPreferenceStore.hostOf(candidate.url))
                if (attempts > MAX_CHUNK_ATTEMPTS) throw IOException("Chunk $index failed on every mirror", error)
                consecutiveFailures++
                // Stop repeatedly failing mirrors so others can finish their chunks.
                if (consecutiveFailures >= 2) return
            }
        }
    }

    private fun request(url: String): Request.Builder = Request.Builder().url(url)
        .header("User-Agent", userAgent)
        // Disable compression to preserve exact byte ranges.
        .header("Accept-Encoding", "identity")
        .header("Cache-Control", "no-cache")
        .header("Pragma", "no-cache")

    /**
     * Cancellation closes the underlying call immediately instead of waiting for read timeout.
     */
    private suspend fun <T> execute(request: Request, block: (Response) -> T): T = coroutineScope {
        val call = client.newCall(request)
        val watcher = launch(start = CoroutineStart.UNDISPATCHED) {
            try {
                awaitCancellation()
            } finally {
                call.cancel()
            }
        }
        try {
            withContext(Dispatchers.IO) { call.execute().use(block) }
        } finally {
            watcher.cancel()
        }
    }

    private fun recordSpeed(candidate: DownloadCandidate, bytes: Long, millis: Long) {
        if (bytes < MIN_SPEED_SAMPLE_BYTES || millis < 50L) return
        store?.recordSpeed(MirrorPreferenceStore.hostOf(candidate.url), bytes * 1000L / millis / 1024L)
    }

    private class ChunkSuperseded : Exception()

    companion object {
        const val HEDGE_STAGGER_MILLIS = 350L
        const val HEDGE_MAX_PARALLEL = 3
        const val PROBE_WAVE = 6
        const val PROBE_TIMEOUT_MILLIS = 5_000L
        const val PROBE_GRACE_MILLIS = 600L

        const val SEGMENT_MIN_BYTES = 1L * 1024 * 1024
        const val MAX_CONNECTIONS = 6
        const val MAX_CHUNK_ATTEMPTS = 5
        private const val BUFFER_BYTES = 32 * 1024
        private const val MAX_IN_MEMORY = 64L * 1024 * 1024
        private const val PROGRESS_INTERVAL_MILLIS = 120L
        private const val WORKER_IDLE_MILLIS = 40L
        private const val MIN_SPEED_SAMPLE_BYTES = 64L * 1024

        fun chunkSizeFor(total: Long): Long =
            (total / 12).coerceIn(256L * 1024, 2L * 1024 * 1024)
    }
}

/**
 * Synchronized chunk ownership and progress. Idle workers hedge stalled chunks; only the first
 * completed copy counts.
 */
internal class SegmentState(val chunks: Int, private val clock: () -> Long) {
    private val pending = ArrayDeque<Int>().apply { repeat(chunks) { add(it) } }
    private val startedAt = HashMap<Int, Long>()
    private val hedged = HashSet<Int>()
    private val done = BooleanArray(chunks)
    private val attempts = IntArray(chunks)
    private val durations = ArrayList<Long>()
    private var doneChunks = 0

    @Synchronized
    fun take(): Int? {
        pending.pollFirst()?.let { index ->
            if (!done[index]) {
                startedAt[index] = clock()
                return index
            }
        }
        val now = clock()
        val threshold = hedgeAfterMillis()
        val straggler = startedAt.entries
            .filter { it.key !in hedged && !done[it.key] && now - it.value >= threshold }
            .minByOrNull { it.value }?.key ?: return null
        hedged += straggler
        return straggler
    }

    @Synchronized
    fun complete(index: Int): Boolean {
        startedAt.remove(index)?.let { durations += clock() - it }
        if (done[index]) return false
        done[index] = true
        doneChunks++
        return true
    }

    /** Requeue failed chunks at the front and return their failure count. */
    @Synchronized
    fun fail(index: Int): Int {
        startedAt.remove(index)
        attempts[index]++
        if (!done[index]) pending.addFirst(index)
        return attempts[index]
    }

    /** Cancellation returns the chunk without recording failure. */
    @Synchronized
    fun release(index: Int) {
        startedAt.remove(index)
        if (!done[index]) pending.addFirst(index)
    }

    @Synchronized fun isDone(index: Int) = done[index]
    @Synchronized fun allDone() = doneChunks == chunks
    @Synchronized fun doneCount() = doneChunks

    /**
     * Hedge after twice the median completed-chunk duration, bounded by [MIN_HEDGE_MILLIS]; use
     * [DEFAULT_HEDGE_MILLIS] without samples.
     */
    private fun hedgeAfterMillis(): Long {
        if (durations.size < 2) return DEFAULT_HEDGE_MILLIS
        val median = durations.sorted()[durations.size / 2]
        return (median * 2).coerceIn(MIN_HEDGE_MILLIS, DEFAULT_HEDGE_MILLIS)
    }

    private companion object {
        const val DEFAULT_HEDGE_MILLIS = 1_200L
        const val MIN_HEDGE_MILLIS = 500L
    }
}
