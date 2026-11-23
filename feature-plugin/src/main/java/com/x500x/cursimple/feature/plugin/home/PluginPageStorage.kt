package com.x500x.cursimple.feature.plugin.home

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import java.io.File
import java.security.KeyStore
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer

/**
 * Encrypted key/value storage that one plugin's page owns. The host keeps the bytes and never reads the values:
 * what they mean is decided by the plugin that wrote them.
 */
internal class PluginPageStorage(context: Context, private val owner: String) {
    private val root = File(context.applicationContext.filesDir, "plugin-page-storage").apply { mkdirs() }
    private val file = AtomicFile(File(root, hash(owner) + ".bin"))
    private val lock = locks.getOrPut(owner) { Any() }

    fun get(key: String): String? = synchronized(lock) { read()[key] }

    fun set(key: String, value: String) = synchronized(lock) {
        if (value.length > MAX_STORAGE_VALUE_CHARS) throw PluginPageCodeException("storage_full")
        write(read() + (key to value))
    }

    fun remove(key: String) = synchronized(lock) { write(read() - key) }

    fun clear() = synchronized(lock) { file.delete() }

    /** Unreadable data (damaged file, lost key, older format) counts as empty and is replaced by the next write. */
    private fun read(): Map<String, String> = runCatching {
        val existing = file.baseFile.takeIf { it.exists() }
            ?: File(file.baseFile.path + ".bak").takeIf { it.exists() }
            ?: return@runCatching emptyMap()
        require(existing.length() <= MAX_STORAGE_TOTAL_BYTES) { "Plugin storage is too large" }
        val payload = file.readFully()
        require(payload.size >= 29 && payload.size <= MAX_STORAGE_TOTAL_BYTES && payload[0] == 1.toByte()) { "Invalid plugin storage" }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, payload.copyOfRange(1, 13)))
        cipher.updateAAD(owner.toByteArray())
        val plain = cipher.doFinal(payload, 13, payload.size - 13)
        Json.decodeFromString(serializer<Map<String, String>>(), plain.toString(Charsets.UTF_8))
    }.getOrDefault(emptyMap())

    private fun write(values: Map<String, String>) {
        val plain = Json.encodeToString(serializer<Map<String, String>>(), values).toByteArray()
        if (plain.size > MAX_STORAGE_TOTAL_BYTES - 64) throw PluginPageCodeException("storage_full")
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        cipher.updateAAD(owner.toByteArray())
        val encrypted = byteArrayOf(1) + cipher.iv + cipher.doFinal(plain)
        val output = file.startWrite()
        try {
            output.write(encrypted)
            file.finishWrite(output)
        } catch (failure: Throwable) {
            file.failWrite(output)
            throw failure
        }
    }

    private fun key(): SecretKey = synchronized(keyLock) {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(KEY_ALIAS, null) as? SecretKey) ?: KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore",
        ).apply {
            init(KeyGenParameterSpec.Builder(
                KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            ).setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        }.generateKey()
    }

    companion object {
        private const val KEY_ALIAS = "cursimple.plugin.page.storage"
        private val locks = ConcurrentHashMap<String, Any>()
        private val keyLock = Any()
        private fun hash(value: String) = MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray()).joinToString("") { "%02x".format(it) }
    }
}
