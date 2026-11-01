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
import kotlinx.serialization.encodeToString

/** Persistent, encrypted, source-scoped storage. Package upgrades keep the same installation key. */
internal class PluginHomeCache(context: Context, private val owner: String) {
    private val root = File(context.applicationContext.filesDir, "plugin-home-cache").apply { mkdirs() }
    private val file = AtomicFile(File(root, hash(owner) + ".bin"))
    private val lock = locks.getOrPut(owner) { Any() }

    fun read(): PluginHomeCacheState = synchronized(lock) {
        val existing = file.baseFile.takeIf { it.exists() }
            ?: File(file.baseFile.path + ".bak").takeIf { it.exists() }
            ?: return@synchronized PluginHomeCacheState()
        require(existing.length() <= MAX_BYTES) { "Home cache is too large" }
        val payload = file.readFully()
        require(payload.size in 29..MAX_BYTES && payload[0] == 1.toByte()) { "Invalid home cache" }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, payload.copyOfRange(1, 13)))
        cipher.updateAAD(owner.toByteArray())
        val plain = cipher.doFinal(payload, 13, payload.size - 13)
        homeJson.decodeFromString<PluginHomeCacheState>(plain.toString(Charsets.UTF_8))
    }

    fun update(change: (PluginHomeCacheState) -> PluginHomeCacheState): PluginHomeCacheState = synchronized(lock) {
        val next = change(read())
        val plain = homeJson.encodeToString(next).toByteArray()
        require(plain.size <= MAX_BYTES - 64) { "Home cache is too large" }
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
        next
    }

    fun clear() = synchronized(lock) { file.delete() }

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
        private const val KEY_ALIAS = "cursimple.plugin.home.cache"
        private const val MAX_BYTES = 4L * 1024 * 1024
        private val locks = ConcurrentHashMap<String, Any>()
        private val keyLock = Any()
        private fun hash(value: String) = MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray()).joinToString("") { "%02x".format(it) }
    }
}
