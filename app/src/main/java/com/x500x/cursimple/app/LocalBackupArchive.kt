package com.x500x.cursimple.app

import com.x500x.cursimple.core.data.AppBackupPayload
import com.x500x.cursimple.core.data.AppBackupStores
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/** Local, user-selected backup archive. It deliberately wraps the existing app payload. */
internal object LocalBackupArchive {
    const val MANIFEST_ENTRY = "manifest.json"
    const val PAYLOAD_ENTRY = "app-backup.json"
    const val FORMAT_VERSION = 1
    const val MAX_ARCHIVE_BYTES = 96L * 1024 * 1024
    const val MAX_ENTRY_BYTES = 90L * 1024 * 1024
    private const val MAX_ENTRIES = 4

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    @Serializable
    data class Manifest(
        val formatVersion: Int,
        val payloadVersion: Int,
        val createdAt: Long?,
        val storeNames: List<String>,
        val includesMemoImages: Boolean,
    )

    data class Decoded(
        val manifest: Manifest,
        val payload: AppBackupPayload,
    )

    fun encode(
        payload: AppBackupPayload,
        selectedStores: Set<String> = payload.stores.map { it.storeName }.toSet(),
    ): ByteArray {
        require(selectedStores.isNotEmpty()) { "backup_no_stores" }
        require(selectedStores.all { it in payload.stores.map { store -> store.storeName }.toSet() }) {
            "backup_unknown_store"
        }
        val selectedPayload = payload.copy(
            stores = payload.stores.filter { it.storeName in selectedStores },
            memoImages = if (AppBackupStores.MEMOS in selectedStores) payload.memoImages else emptyList(),
        )
        val manifest = Manifest(
            formatVersion = FORMAT_VERSION,
            payloadVersion = selectedPayload.version,
            createdAt = selectedPayload.createdAt,
            storeNames = selectedPayload.stores.map { it.storeName },
            includesMemoImages = selectedPayload.memoImages.isNotEmpty(),
        )
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { zip ->
            writeEntry(zip, MANIFEST_ENTRY, json.encodeToString(Manifest.serializer(), manifest).toByteArray())
            writeEntry(
                zip,
                PAYLOAD_ENTRY,
                json.encodeToString(AppBackupPayload.serializer(), selectedPayload).toByteArray(),
            )
        }
        return output.toByteArray().also { require(it.size <= MAX_ARCHIVE_BYTES) { "backup_too_large" } }
    }

    fun decode(bytes: ByteArray): Decoded {
        require(bytes.size.toLong() <= MAX_ARCHIVE_BYTES) { "backup_too_large" }
        var manifestBytes: ByteArray? = null
        var payloadBytes: ByteArray? = null
        var entryCount = 0
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                entryCount++
                require(entryCount <= MAX_ENTRIES) { "backup_unexpected_entry" }
                require(!entry.isDirectory && entry.name in setOf(MANIFEST_ENTRY, PAYLOAD_ENTRY)) {
                    "backup_unexpected_entry"
                }
                val content = readEntry(zip)
                when (entry.name) {
                    MANIFEST_ENTRY -> require(manifestBytes == null) { "backup_duplicate_entry" }.also { manifestBytes = content }
                    PAYLOAD_ENTRY -> require(payloadBytes == null) { "backup_duplicate_entry" }.also { payloadBytes = content }
                }
                zip.closeEntry()
            }
        }
        val manifest = manifestBytes?.let {
            json.decodeFromString(Manifest.serializer(), it.toString(Charsets.UTF_8))
        } ?: error("backup_manifest_missing")
        require(manifest.formatVersion == FORMAT_VERSION) { "backup_format_too_new" }
        val payload = payloadBytes?.let {
            json.decodeFromString(AppBackupPayload.serializer(), it.toString(Charsets.UTF_8))
        } ?: error("backup_payload_missing")
        require(manifest.payloadVersion == payload.version) { "backup_manifest_mismatch" }
        require(manifest.storeNames == payload.stores.map { it.storeName }) { "backup_manifest_mismatch" }
        require(manifest.includesMemoImages == payload.memoImages.isNotEmpty()) { "backup_manifest_mismatch" }
        require(payload.stores.isNotEmpty()) { "backup_no_stores" }
        require(payload.stores.all { it.storeName in AppBackupStores.ALL }) { "backup_unknown_store" }
        return Decoded(manifest, payload)
    }

    private fun writeEntry(zip: ZipOutputStream, name: String, bytes: ByteArray) {
        require(bytes.size.toLong() <= MAX_ENTRY_BYTES) { "backup_entry_too_large" }
        zip.putNextEntry(ZipEntry(name))
        zip.write(bytes)
        zip.closeEntry()
    }

    private fun readEntry(zip: ZipInputStream): ByteArray {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var total = 0L
        while (true) {
            val count = zip.read(buffer)
            if (count < 0) break
            total += count
            require(total <= MAX_ENTRY_BYTES) { "backup_entry_too_large" }
            output.write(buffer, 0, count)
        }
        return output.toByteArray()
    }
}
