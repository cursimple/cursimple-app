package com.x500x.cursimple.feature.plugin

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.webkit.JavascriptInterface
import android.webkit.WebView
import com.x500x.cursimple.core.plugin.logging.PluginLogger
import java.security.KeyStore
import java.util.Locale
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive

/**
 * 插件网页会话里的「记住密码」，替代 WebView 早已废弃的 `setSavePassword`。
 *
 * 用户在登录页提交时，注入脚本把账号密码交给 App，App 询问是否保存；下次打开同一域名的
 * 登录页时自动填好，由用户自己点登录（不代为提交，验证码、滑块之类照常由用户处理）。
 *
 * - 按「插件 + 域名」分开存，插件之间互相看不到；
 * - 密码用 Android Keystore 里不可导出的 AES-GCM 密钥加密，存储文件已排除在备份之外；
 *   换机或密钥丢失时解不开，就当作没存过。
 */
internal data class WebLoginCredential(
    val host: String,
    val username: String,
    val password: String,
)

internal class WebLoginCredentialStore(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun find(pluginId: String, host: String): WebLoginCredential? {
        val raw = prefs.getString(entryKey(pluginId, host), null) ?: return null
        val entry = runCatching { json.decodeFromString<StoredEntry>(raw) }.getOrNull() ?: return null
        val password = runCatching { decrypt(entry.password) }
            .onFailure { error ->
                PluginLogger.warn(
                    "plugin.web_login.decrypt_failed",
                    mapOf("error" to (error.message ?: error.javaClass.simpleName)),
                )
            }
            .getOrNull() ?: return null
        return WebLoginCredential(host = normalizeHost(host), username = entry.username, password = password)
    }

    fun save(pluginId: String, credential: WebLoginCredential): Boolean {
        val encrypted = runCatching { encrypt(credential.password) }
            .onFailure { error ->
                PluginLogger.error(
                    "plugin.web_login.encrypt_failed",
                    mapOf("error" to (error.message ?: error.javaClass.simpleName)),
                )
            }
            .getOrNull() ?: return false
        val entry = StoredEntry(username = credential.username, password = encrypted)
        prefs.edit()
            .putString(entryKey(pluginId, credential.host), json.encodeToString(StoredEntry.serializer(), entry))
            .remove(neverKey(pluginId, credential.host))
            .apply()
        return true
    }

    fun isNeverSave(pluginId: String, host: String): Boolean =
        prefs.getBoolean(neverKey(pluginId, host), false)

    fun setNeverSave(pluginId: String, host: String) {
        prefs.edit().putBoolean(neverKey(pluginId, host), true).apply()
    }

    fun hasAny(pluginId: String): Boolean {
        val entryPrefix = "$ENTRY_PREFIX${pluginId}|"
        val neverPrefix = "$NEVER_PREFIX${pluginId}|"
        return prefs.all.keys.any { it.startsWith(entryPrefix) || it.startsWith(neverPrefix) }
    }

    /** 清掉这个插件存过的全部密码和「永不保存」标记。 */
    fun clear(pluginId: String) {
        val entryPrefix = "$ENTRY_PREFIX${pluginId}|"
        val neverPrefix = "$NEVER_PREFIX${pluginId}|"
        val editor = prefs.edit()
        prefs.all.keys
            .filter { it.startsWith(entryPrefix) || it.startsWith(neverPrefix) }
            .forEach(editor::remove)
        editor.apply()
    }

    private fun entryKey(pluginId: String, host: String) = "$ENTRY_PREFIX$pluginId|${normalizeHost(host)}"

    private fun neverKey(pluginId: String, host: String) = "$NEVER_PREFIX$pluginId|${normalizeHost(host)}"

    private fun encrypt(plain: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        val cipherText = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(cipher.iv + cipherText, Base64.NO_WRAP)
    }

    private fun decrypt(encoded: String): String {
        val bytes = Base64.decode(encoded, Base64.NO_WRAP)
        require(bytes.size > GCM_IV_BYTES) { "密文长度不对" }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(
            Cipher.DECRYPT_MODE,
            secretKey(),
            GCMParameterSpec(GCM_TAG_BITS, bytes, 0, GCM_IV_BYTES),
        )
        val plain = cipher.doFinal(bytes, GCM_IV_BYTES, bytes.size - GCM_IV_BYTES)
        return String(plain, Charsets.UTF_8)
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

    @Serializable
    private data class StoredEntry(val username: String, val password: String)

    companion object {
        /** 与 app 模块的 backup_rules / data_extraction_rules 里的排除项保持一致。 */
        const val PREFS_NAME = "plugin_web_credentials"
        private const val ENTRY_PREFIX = "cred|"
        private const val NEVER_PREFIX = "never|"
        private const val KEY_ALIAS = "cursimple_plugin_web_credentials"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_IV_BYTES = 12
        private const val GCM_TAG_BITS = 128
        private val json = Json { ignoreUnknownKeys = true }
    }
}

/**
 * 把「记住密码」接到一个 WebView 上：装 JS 桥、每次页面加载完注入填充与监听脚本。
 * [onCaptured] 在主线程回调，交给界面决定要不要弹「保存密码」。
 */
internal class WebLoginAssist(
    private val pluginId: String,
    private val allowedHosts: List<String>,
    private val store: WebLoginCredentialStore,
    private val onCaptured: (WebLoginCredential) -> Unit,
) {

    fun install(webView: WebView) {
        webView.addJavascriptInterface(Bridge(webView), BRIDGE_NAME)
    }

    fun onPageFinished(webView: WebView, url: String?) {
        val host = hostOf(url) ?: return
        if (!isAllowedHost(url.orEmpty(), allowedHosts)) return
        val saved = store.find(pluginId, host)
        webView.evaluateJavascript(webLoginAssistScript(host, saved), null)
    }

    private inner class Bridge(private val webView: WebView) {
        /**
         * 登录表单提交时由注入脚本调用。桥对页面里所有脚本都可见，所以只信任
         * 与 WebView 当前主文档同域、且在白名单里的上报。
         */
        @JavascriptInterface
        fun onLoginSubmit(host: String?, username: String?, password: String?) {
            val reportedHost = normalizeHost(host.orEmpty())
            val user = username.orEmpty().trim().take(MAX_FIELD_CHARS)
            val pass = password.orEmpty().take(MAX_FIELD_CHARS)
            if (reportedHost.isBlank() || user.isBlank() || pass.isBlank()) return
            webView.post {
                val currentHost = hostOf(webView.url) ?: return@post
                if (currentHost != reportedHost) return@post
                if (!isAllowedHost(webView.url.orEmpty(), allowedHosts)) return@post
                if (store.isNeverSave(pluginId, currentHost)) return@post
                val saved = store.find(pluginId, currentHost)
                if (saved != null && saved.username == user && saved.password == pass) return@post
                onCaptured(WebLoginCredential(host = currentHost, username = user, password = pass))
            }
        }
    }

    companion object {
        const val BRIDGE_NAME = "CurSimpleLoginBridge"
        private const val MAX_FIELD_CHARS = 256
    }
}

private fun hostOf(url: String?): String? =
    runCatching { Uri.parse(url.orEmpty()).host }.getOrNull()
        ?.let(::normalizeHost)
        ?.takeIf(String::isNotBlank)

private fun normalizeHost(host: String): String = host.trim().trimEnd('.').lowercase(Locale.ROOT)

/**
 * 登录页脚本：有存过的账号就填进空着的输入框；监听提交，把账号密码交给 [WebLoginAssist]。
 *
 * - 很多登录页（统一认证、aTrust 门户）是点按钮后由脚本调 `form.submit()` 或直接发请求，
 *   不会触发 submit 事件，所以同时监听点击「登录」类按钮、在密码框里按回车，并包一层
 *   `HTMLFormElement.prototype.submit`；
 * - 这些监听都在捕获阶段、页面自己的处理之前读值，页面随后把密码框换成加密值也不影响；
 * - 登录表单可能晚于 onPageFinished 才渲染（单页应用），用 MutationObserver 持续补填；
 * - 填值走原生 setter 再派发 input/change，Vue / React 的双向绑定才认。
 */
internal fun webLoginAssistScript(host: String, saved: WebLoginCredential?): String {
    fun literal(value: String) = JsonPrimitive(value).toString().replace("<", "\\u003c")
    val hostLiteral = literal(host)
    val userLiteral = saved?.let { literal(it.username) } ?: "null"
    val passLiteral = saved?.let { literal(it.password) } ?: "null"
    return """
        (function () {
          try {
            if ((location.hostname || "").toLowerCase() !== $hostLiteral) { return; }
            var saved = ($userLiteral !== null) ? { u: $userLiteral, p: $passLiteral } : null;
            var state = window.__cursimpleLoginAssist;
            if (state) { state.fill(saved); return; }
            state = window.__cursimpleLoginAssist = { lastReport: "" };
            var currentSaved = null;

            var USER_HINT = /user|account|login|name|uid|xh|sno|stu|phone|mobile|mail|zh|yhm/i;
            function visible(el) {
              if (!el || el.disabled || el.readOnly) { return false; }
              var style = window.getComputedStyle(el);
              if (style.display === "none" || style.visibility === "hidden") { return false; }
              var rect = el.getBoundingClientRect();
              return rect.width > 0 && rect.height > 0;
            }
            function passwordField(root) {
              var list = (root || document).querySelectorAll('input[type="password"]');
              for (var i = 0; i < list.length; i++) { if (visible(list[i])) { return list[i]; } }
              return null;
            }
            function isTextInput(el) {
              var type = (el.getAttribute("type") || "text").toLowerCase();
              return type === "text" || type === "email" || type === "tel" || type === "number";
            }
            // 密码框之前、离它最近的可见文本框；名字像账号的优先
            function usernameField(pwd) {
              var scope = pwd.form || document;
              var inputs = scope.querySelectorAll("input");
              var before = [];
              for (var i = 0; i < inputs.length; i++) {
                var el = inputs[i];
                if (el === pwd) { break; }
                if (isTextInput(el) && visible(el)) { before.push(el); }
              }
              for (var j = before.length - 1; j >= 0; j--) {
                var hint = (before[j].name || "") + " " + (before[j].id || "") + " " +
                  (before[j].getAttribute("autocomplete") || "") + " " + (before[j].placeholder || "");
                if (USER_HINT.test(hint) || /账号|学号|工号|用户名|手机/.test(hint)) { return before[j]; }
              }
              return before.length ? before[before.length - 1] : null;
            }
            function setValue(el, value) {
              var setter = Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, "value").set;
              setter.call(el, value);
              el.dispatchEvent(new Event("input", { bubbles: true }));
              el.dispatchEvent(new Event("change", { bubbles: true }));
            }
            // 每个密码框只填一次：用户清空了想换个账号，不要又给填回去
            function fillNow() {
              if (!currentSaved) { return; }
              var pwd = passwordField();
              if (!pwd || pwd.value || pwd.__cursimpleFilled) { return; }
              pwd.__cursimpleFilled = true;
              var user = usernameField(pwd);
              if (user && !user.value) { setValue(user, currentSaved.u); }
              if (!user || user.value === currentSaved.u) { setValue(pwd, currentSaved.p); }
            }
            state.fill = function (value) { currentSaved = value; fillNow(); };
            function report(root) {
              var pwd = passwordField(root) || passwordField();
              if (!pwd || !pwd.value) { return; }
              var user = usernameField(pwd);
              var username = user ? user.value : "";
              if (!username) { return; }
              var key = username + "\u0000" + pwd.value;
              if (key === state.lastReport) { return; }
              state.lastReport = key;
              try {
                window.${WebLoginAssist.BRIDGE_NAME}.onLoginSubmit(location.hostname, username, pwd.value);
              } catch (e) {}
            }
            var LOGIN_TEXT = /^\s*(登\s*录|登\s*入|login|log\s*in|sign\s*in|确\s*定|提\s*交)\s*$/i;
            document.addEventListener("submit", function (event) { report(event.target); }, true);
            document.addEventListener("click", function (event) {
              var el = event.target && event.target.closest &&
                event.target.closest('button, input[type="submit"], input[type="button"], a, [role="button"]');
              if (!el) { return; }
              var text = el.value || el.textContent || "";
              var hint = (el.id || "") + " " + (el.className || "");
              if (el.type === "submit" || LOGIN_TEXT.test(text) || /login|submit/i.test(hint)) {
                report(el.form || null);
              }
            }, true);
            document.addEventListener("keydown", function (event) {
              if (event.key === "Enter" && event.target && event.target.type === "password") {
                report(event.target.form || null);
              }
            }, true);
            var nativeSubmit = HTMLFormElement.prototype.submit;
            HTMLFormElement.prototype.submit = function () {
              try { report(this); } catch (e) {}
              return nativeSubmit.apply(this, arguments);
            };

            state.fill(saved);
            var pending = false;
            new MutationObserver(function () {
              if (pending) { return; }
              pending = true;
              setTimeout(function () { pending = false; fillNow(); }, 150);
            }).observe(document.documentElement, { childList: true, subtree: true });
          } catch (e) {}
        })();
    """.trimIndent()
}
