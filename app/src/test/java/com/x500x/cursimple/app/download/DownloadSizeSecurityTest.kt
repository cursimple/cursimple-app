package com.x500x.cursimple.app.download

import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody
import okio.Buffer
import okio.BufferedSource
import okio.Source
import okio.Timeout
import okio.buffer
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.IOException
import java.io.RandomAccessFile

class DownloadSizeSecurityTest {
    @get:Rule val folder = TemporaryFolder()

    @Test fun oversizedLocalByteDownloadIsRejectedBeforeAllocation() = runBlocking {
        val file = folder.newFile()
        RandomAccessFile(file, "rw").use { it.setLength(64L * 1024 * 1024 + 1) }
        val labels = MirrorDownloaderLabels("local", "verify", "read", "local verify", "local read", "probe", "download", "no source")
        val result = MirrorDownloader(labels).downloadBytes(DownloadRequest(DownloadPurpose.LocalFile, file.path))
        assertTrue(result is MirrorDownloadResult.Failure)
    }

    @Test fun unknownLengthNetworkResponseIsStoppedWhileStreaming() = runBlocking {
        var emitted = 0L
        val source = object : Source {
            private val chunk = ByteArray(8192)
            override fun read(sink: Buffer, byteCount: Long): Long {
                val count = minOf(byteCount, chunk.size.toLong()).toInt()
                sink.write(chunk, 0, count)
                emitted += count
                return count.toLong()
            }
            override fun timeout() = Timeout.NONE
            override fun close() = Unit
        }.buffer()
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                .body(object : ResponseBody() {
                    override fun contentType(): okhttp3.MediaType? = null
                    override fun contentLength() = -1L
                    override fun source(): BufferedSource = source
                }).build()
        }.build()
        val error = runCatching { FastTransfer(client, "test", null).fetchBytes(DownloadCandidate("test", "https://example.test/data")) { _, _ -> } }.exceptionOrNull()
        assertTrue(error.toString(), error is IOException)
        assertTrue("Infinite stream must be bounded", emitted in (64L * 1024 * 1024 + 1)..(65L * 1024 * 1024))
    }
}
