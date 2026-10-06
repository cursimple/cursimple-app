package com.x500x.cursimple.feature.plugin.extension

import java.io.StringReader
import java.io.StringWriter
import java.io.EOFException
import java.util.Base64
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.*
import org.junit.Test

class ExtensionSmtpTransportTest {
    private fun payload() = buildJsonObject {
        put("username", "sender@example.com"); put("password", "fixture-auth-code")
        put("from", "sender@example.com"); put("to", "receiver@example.com")
        put("subject", "课程🙂提醒".repeat(8)); put("text", "第一行\n.\nQUIT\n最后一行")
        put("messageId", "stable-fixture-id")
    }
    private val beforeData = "220 mail.example.com\r\n250-mail.example.com\r\n250-AUTH LOGIN PLAIN\r\n250 OK\r\n334 VXNlcm5hbWU6\r\n334 UGFzc3dvcmQ6\r\n235 authenticated\r\n250 sender accepted\r\n250 recipient accepted\r\n354 send data\r\n"

    @Test fun waitsForFinalAcceptanceAndEncodesUnicodeWithoutSplittingEmoji() {
        val message = SmtpMessage.from(payload())
        val output = StringWriter()
        var submitted = false
        smtpSend(StringReader(beforeData + "250 queued\r\n221 bye\r\n").buffered(), output.buffered(), message, onSubmit = { submitted = true })
        assertTrue(submitted)
        val transcript = output.toString()
        assertTrue(transcript.startsWith("EHLO cursimple.local\r\nAUTH LOGIN\r\n"))
        assertTrue(transcript.contains("MAIL FROM:<sender@example.com>\r\nRCPT TO:<receiver@example.com>\r\nDATA\r\n"))
        assertFalse(transcript.contains("fixture-auth-code"))
        assertFalse(transcript.contains("第一行"))
        assertEquals(1, Regex("\\r\\n\\.\\r\\n").findAll(transcript).count())
        val mime = smtpMime(message)
        val words = Regex("=\\?UTF-8\\?B\\?([^?]+)\\?=").findAll(mime).toList()
        assertTrue(words.all { it.value.length <= 75 })
        assertEquals(message.subject, words.joinToString("") { String(Base64.getDecoder().decode(it.groupValues[1]), Charsets.UTF_8) })
        val body = mime.substringAfter("\r\n\r\n").replace("\r\n", "")
        assertEquals("第一行\r\n.\r\nQUIT\r\n最后一行", String(Base64.getDecoder().decode(body), Charsets.UTF_8))
    }

    @Test fun authenticationRejectionStopsBeforeMessageSubmission() {
        var submitted = false
        val output = StringWriter()
        try {
            smtpSend(StringReader("220 ready\r\n250-AUTH LOGIN\r\n250 OK\r\n334 user\r\n334 pass\r\n535 auth rejected\r\n").buffered(), output.buffered(), SmtpMessage.from(payload()), onSubmit = { submitted = true })
            fail("must reject")
        } catch (error: SmtpRejected) { assertEquals(535, error.code) }
        assertFalse(submitted)
        assertFalse(output.toString().contains("MAIL FROM"))
    }

    @Test fun connectionLossAfterDataIsUnconfirmedAndQuitFailureDoesNotUndoAcceptance() {
        var submitted = false
        try {
            smtpSend(StringReader(beforeData).buffered(), StringWriter().buffered(), SmtpMessage.from(payload()), onSubmit = { submitted = true })
            fail("missing final reply")
        } catch (_: EOFException) { assertTrue(submitted) }
        smtpSend(StringReader(beforeData + "250 queued\r\n").buffered(), StringWriter().buffered(), SmtpMessage.from(payload()))
    }

    @Test fun headerInjectionAndForgedSenderAreRejected() {
        for (field in listOf("from", "to", "subject")) {
            val invalid = buildJsonObject { payload().forEach { (key, value) -> put(key, value) }; put(field, "value\r\nBcc: other@example.com") }
            assertThrows(IllegalArgumentException::class.java) { SmtpMessage.from(invalid) }
        }
        val changed = buildJsonObject { payload().forEach { (key, value) -> put(key, value) }; put("from", "other@example.com") }
        assertThrows(IllegalArgumentException::class.java) { SmtpMessage.from(changed) }
    }

    @Test fun malformedOrExcessiveMultilineRepliesFailBeforeAuthentication() {
        for (reply in listOf("not SMTP\r\n", "220-" + "x".repeat(9000) + "\r\n", "220-more\r\n".repeat(65))) {
            assertThrows(java.io.IOException::class.java) { smtpSend(StringReader(reply).buffered(), StringWriter().buffered(), SmtpMessage.from(payload())) }
        }
    }
}
