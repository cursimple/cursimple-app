package com.x500x.cursimple.feature.plugin.extension

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

@Serializable
data class ExtensionSecureConfiguration(
    val values: JsonObject = JsonObject(emptyMap()), val hosts: List<String> = emptyList(),
    val targets: List<com.x500x.cursimple.core.data.notification.NotificationTarget> = emptyList(),
    val sessions: JsonObject = JsonObject(emptyMap()),
)

class ExtensionSecureConfig(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    suspend fun read(pluginId: String): ExtensionSecureConfiguration = withContext(Dispatchers.IO) {
        val encoded = prefs.getString(key(pluginId), null) ?: return@withContext ExtensionSecureConfiguration()
        try {
            val bytes = Base64.decode(encoded, Base64.NO_WRAP)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(128, bytes, 0, 12))
            cipher.updateAAD(pluginId.toByteArray())
            extensionJson.decodeFromString(ExtensionSecureConfiguration.serializer(), String(cipher.doFinal(bytes, 12, bytes.size - 12), Charsets.UTF_8))
        } catch (_: Exception) { error("绑定配置无法解密，请重新绑定通知目标") }
    }
    suspend fun save(pluginId: String, configuration: ExtensionSecureConfiguration) = withContext(Dispatchers.IO) {
        val text = extensionJson.encodeToString(ExtensionSecureConfiguration.serializer(), configuration)
        require(text.length <= 64 * 1024) { "配置过大" }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        cipher.updateAAD(pluginId.toByteArray())
        val bytes = cipher.iv + cipher.doFinal(text.toByteArray())
        check(prefs.edit().putString(key(pluginId), Base64.encodeToString(bytes, Base64.NO_WRAP)).commit()) { "绑定配置保存失败" }
    }
    suspend fun remove(pluginId: String) = withContext(Dispatchers.IO) { prefs.edit().remove(key(pluginId)).commit(); Unit }
    fun componentIds(): Set<String> = prefs.all.keys.toSet()
    private fun key(pluginId: String): String = pluginId.also { require(it.matches(Regex("[A-Za-z0-9._-]+"))) }
    private fun secretKey(): SecretKey = synchronized(KEY_LOCK) {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(KEY_ALIAS, null) as? SecretKey) ?: KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).setKeySize(256).build())
        }.generateKey()
    }
    companion object {
        const val PREFS_NAME = "extension_secure_config"
        private const val KEY_ALIAS = "cursimple_component_config_v1"
        private val KEY_LOCK = Any()
    }
}
