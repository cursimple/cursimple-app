package com.x500x.cursimple.feature.plugin.extension

import com.x500x.cursimple.core.plugin.manifest.PluginPermission
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okio.ByteString.Companion.toByteString
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.EOFException
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory

object ExtensionSmtpTransport {
    suspend fun send(request: ExtensionRunRequest, payload: JsonObject): JsonObject = withContext(Dispatchers.IO) {
        require(PluginPermission.NetworkProxy.id in request.permissions) { "组件未声明宿主网络传输权限" }
        val host = payload.fieldText("host").lowercase()
        require(host.matches(Regex("[a-z0-9](?:[a-z0-9.-]{0,251}[a-z0-9])?")) && !host.contains("..")) { "邮件服务器无效" }
        require(ExtensionUrls.isAllowed("https://$host", request.allowedHosts)) { "邮件服务器不属于已绑定目标" }
        require(payload.fieldText("port").ifBlank { "465" } == "465") { "邮件传输需要 TLS 465 端口" }
        val message = SmtpMessage.from(payload)
        var submitted = false
        val deadline = System.nanoTime() + 40_000_000_000L
        try {
            Socket().use { connection ->
                connection.connect(InetSocketAddress(host, 465), 8_000)
                ((SSLSocketFactory.getDefault() as SSLSocketFactory).createSocket(connection, host, 465, true) as SSLSocket).use { socket ->
                    socket.sslParameters = socket.sslParameters.apply { endpointIdentificationAlgorithm = "HTTPS" }
                    socket.soTimeout = 10_000
                    socket.startHandshake()
                    val reader = socket.inputStream.bufferedReader(Charsets.US_ASCII)
                    val writer = socket.outputStream.bufferedWriter(Charsets.US_ASCII)
                    smtpSend(reader, writer, message, beforeRead = {
                        val remaining = ((deadline - System.nanoTime()) / 1_000_000).toInt()
                        if (remaining <= 0) throw IOException("deadline")
                        socket.soTimeout = remaining.coerceIn(1, 10_000)
                    }, onSubmit = { submitted = true })
                    buildJsonObject { put("ok", true); put("accepted", true); put("responseCode", 250) }
                }
            }
        } catch (rejected: SmtpRejected) {
            buildJsonObject { put("ok", false); put("ambiguous", false); put("responseCode", rejected.code)
                put("error", "邮件服务器拒绝发送（SMTP ${rejected.code}），请检查 SMTP 开关、授权码和邮箱地址") }
        } catch (_: IOException) {
            buildJsonObject { put("ok", false); put("ambiguous", submitted)
                put("error", if (submitted) "邮件发送结果未确认，请检查邮箱后再重试" else "邮件连接或认证未完成，请检查网络和 SMTP 设置后重试") }
        }
    }
}

internal data class SmtpMessage(val username: String, val password: String, val from: String,
    val to: String, val subject: String, val text: String, val id: String) {
    companion object {
        fun from(payload: JsonObject): SmtpMessage {
            val username = payload.fieldText("username")
            val from = payload.fieldText("from")
            val to = payload.fieldText("to")
            val email = Regex("[A-Za-z0-9.!#$%&'*+/=?^_`{|}~-]+@[A-Za-z0-9-]+(?:\\.[A-Za-z0-9-]+)+")
            require(listOf(username, from, to).all { it.length <= 254 && email.matches(it) }) { "发件或收件邮箱无效" }
            require(from.equals(username, ignoreCase = true)) { "发件邮箱需要与认证帐号相同" }
            val password = payload.fieldText("password")
            require(password.isNotEmpty() && password.length <= 4096 && password.none { it < ' ' || it == '\u007f' }) { "邮件授权码无效" }
            val subject = payload.fieldText("subject")
            require(subject.length in 1..256 && subject.none { it < ' ' || it == '\u007f' }) { "邮件主题无效" }
            val text = payload.fieldText("text")
            require(text.toByteArray(Charsets.UTF_8).size <= 32 * 1024) { "邮件内容过长" }
            val id = payload.fieldText("messageId").ifBlank { UUID.randomUUID().toString() }
            require(id.matches(Regex("[A-Za-z0-9._-]{1,128}"))) { "邮件标识无效" }
            return SmtpMessage(username, password, from, to, subject, text, id)
        }
    }
}

internal class SmtpRejected(val code: Int) : IOException()

internal fun smtpSend(reader: BufferedReader, writer: BufferedWriter, message: SmtpMessage,
    beforeRead: () -> Unit = {}, onSubmit: () -> Unit = {}) {
    fun reply(vararg expected: Int): List<String> {
        val lines = mutableListOf<String>()
        var code: Int? = null
        repeat(64) {
            beforeRead()
            val line = buildString {
                while (true) {
                    val value = reader.read()
                    if (value < 0) throw EOFException()
                    if (value == '\n'.code) break
                    if (length >= 8192) throw IOException("reply too long")
                    append(value.toChar())
                }
            }.trimEnd('\r')
            val current = line.take(3).toIntOrNull() ?: throw IOException("invalid SMTP reply")
            if (line.length < 4 || line[3] !in " -" || (code != null && code != current)) throw IOException("invalid SMTP reply")
            code = current
            lines += line
            if (line[3] == ' ') {
                if (current !in expected) throw SmtpRejected(current)
                return lines
            }
        }
        throw IOException("SMTP reply limit")
    }
    fun command(text: String, vararg expected: Int): List<String> {
        writer.write(text + "\r\n"); writer.flush()
        return reply(*expected)
    }
    reply(220)
    val capabilities = command("EHLO cursimple.local", 250).joinToString(" ").uppercase()
    when {
        capabilities.contains("AUTH") && capabilities.contains("LOGIN") -> {
            command("AUTH LOGIN", 334)
            command(message.username.toByteArray().toByteString().base64(), 334)
            command(message.password.toByteArray().toByteString().base64(), 235)
        }
        capabilities.contains("AUTH") && capabilities.contains("PLAIN") ->
            command(("AUTH PLAIN " + ("\u0000" + message.username + "\u0000" + message.password).toByteArray().toByteString().base64()), 235)
        else -> throw SmtpRejected(504)
    }
    command("MAIL FROM:<${message.from}>", 250)
    command("RCPT TO:<${message.to}>", 250, 251)
    command("DATA", 354)
    onSubmit()
    writer.write(smtpMime(message) + "\r\n.\r\n"); writer.flush()
    reply(250)
    try { command("QUIT", 221) } catch (_: IOException) { }
}

internal fun smtpMime(message: SmtpMessage): String {
    val words = mutableListOf<String>()
    var chunk = StringBuilder()
    var bytes = 0
    fun flush() { if (chunk.isNotEmpty()) { words += "=?UTF-8?B?${chunk.toString().toByteArray().toByteString().base64()}?="; chunk = StringBuilder(); bytes = 0 } }
    var index = 0
    while (index < message.subject.length) {
        val count = Character.charCount(message.subject.codePointAt(index))
        val character = message.subject.substring(index, index + count)
        val size = character.toByteArray(Charsets.UTF_8).size
        if (bytes + size > 32) flush()
        chunk.append(character); bytes += size; index += count
    }
    flush()
    val encodedBody = message.text.replace("\r\n", "\n").replace('\r', '\n').replace("\n", "\r\n")
        .toByteArray(Charsets.UTF_8).toByteString().base64().chunked(76).joinToString("\r\n")
    return listOf("From: <${message.from}>", "To: <${message.to}>", "Subject: ${words.joinToString("\r\n ")}",
        "Date: ${DateTimeFormatter.RFC_1123_DATE_TIME.format(ZonedDateTime.now(ZoneOffset.UTC))}",
        "Message-ID: <${message.id}@${message.from.substringAfter('@')}>", "MIME-Version: 1.0",
        "Content-Type: text/plain; charset=UTF-8", "Content-Transfer-Encoding: base64", "", encodedBody).joinToString("\r\n")
}
