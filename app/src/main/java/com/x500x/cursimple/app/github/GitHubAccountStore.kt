package com.x500x.cursimple.app.github

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** 界面上显示的登录状态；令牌本身不往外给。 */
data class GitHubAccount(val login: String, val avatarUrl: String)

/**
 * 读私有仓库用的 GitHub 令牌。
 *
 * 令牌用 Android Keystore 里的密钥加密后存进单独的 SharedPreferences，
 * 这个文件在 backup_rules / data_extraction_rules 里排除了，也不进 WebDAV 备份：
 * 换手机要重新登录，令牌不会跟着备份四处走。
 */
class GitHubAccountStore(context: Context) {
    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    @Volatile
    private var cachedToken: String? = null

    private val _account = MutableStateFlow(readAccount())
    val account: StateFlow<GitHubAccount?> = _account
    private val _revision = MutableStateFlow(0L)
    /** 同一个账号换了令牌也会触发来源重读。 */
    val revision: StateFlow<Long> = _revision

    /** 取令牌；解不开（比如系统清过 Keystore）就当没登录。 */
    @Synchronized
    fun token(): String? {
        cachedToken?.let { return it }
        val encrypted = prefs.getString(KEY_TOKEN, null) ?: return null
        return runCatching { decrypt(encrypted) }.getOrNull()?.also { cachedToken = it }
            ?: run { clear(); null }
    }

    @Synchronized
    fun save(token: String, account: GitHubAccount) {
        require(token.isNotBlank() && account.login.isNotBlank())
        prefs.edit()
            .putString(KEY_TOKEN, encrypt(token.trim()))
            .putString(KEY_LOGIN, account.login)
            .putString(KEY_AVATAR, account.avatarUrl)
            .apply()
        cachedToken = token.trim()
        _account.value = account
        _revision.value += 1
    }

    @Synchronized
    fun clear() {
        prefs.edit().clear().apply()
        cachedToken = null
        _account.value = null
        _revision.value += 1
    }

    private fun readAccount(): GitHubAccount? {
        val login = prefs.getString(KEY_LOGIN, null)?.takeIf { it.isNotBlank() } ?: return null
        val encrypted = prefs.getString(KEY_TOKEN, null) ?: return null
        val decrypted = runCatching { decrypt(encrypted) }.getOrNull()
        if (decrypted.isNullOrBlank()) {
            prefs.edit().clear().apply()
            return null
        }
        cachedToken = decrypted
        return GitHubAccount(login, prefs.getString(KEY_AVATAR, null).orEmpty())
    }

    private fun encrypt(plain: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        val cipherText = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(cipher.iv + cipherText, Base64.NO_WRAP)
    }

    private fun decrypt(encoded: String): String {
        val bytes = Base64.decode(encoded, Base64.NO_WRAP)
        require(bytes.size > GCM_IV_BYTES)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(GCM_TAG_BITS, bytes, 0, GCM_IV_BYTES))
        return String(cipher.doFinal(bytes, GCM_IV_BYTES, bytes.size - GCM_IV_BYTES), Charsets.UTF_8)
    }

    private fun secretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return generator.generateKey()
    }

    companion object {
        /** 与 backup_rules / data_extraction_rules 里的排除项保持一致。 */
        const val PREFS_NAME = "github_account"
        private const val KEY_TOKEN = "token"
        private const val KEY_LOGIN = "login"
        private const val KEY_AVATAR = "avatar"
        private const val KEY_ALIAS = "cursimple_github_account"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_IV_BYTES = 12
        private const val GCM_TAG_BITS = 128
    }
}
