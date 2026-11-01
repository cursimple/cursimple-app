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
import com.x500x.cursimple.core.plugin.school.SchoolPortalCatalog
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
 * Remember credentials per owner and exact host with Keystore encryption, excluded from backups.
 * Autofill requires user submission and preserves interactive challenges.
 */
internal data class WebLoginCredential(
    val host: String,
    val username: String,
    val password: String,
)

internal class WebLoginCredentialStore(
    context: Context,
    private val policy: WebLoginCredentialPolicy = WebLoginCredentialPolicy(
        runCatching { SchoolPortalCatalog.load(context.applicationContext) }.getOrDefault(emptyList()),
    ),
) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun find(pluginId: String, host: String): WebLoginCredential? {
        val owners = policy.owners(pluginId, host)
        for (owner in owners) {
            val stored = read(owner, host) ?: continue
            if (owner != owners.first() || !stored.encryptedUsername) {
                // Re-encrypt legacy entries, including their formerly plaintext account name.
                save(pluginId, stored.credential)
            }
            return stored.credential
        }
        return null
    }

    private data class ReadCredential(val credential: WebLoginCredential, val encryptedUsername: Boolean)

    private fun read(owner: String, host: String): ReadCredential? {
        val raw = prefs.getString(entryKey(owner, host), null) ?: return null
        val entry = runCatching { json.decodeFromString<StoredEntry>(raw) }.getOrNull() ?: return null
        return runCatching {
            ReadCredential(
                WebLoginCredential(
                    host = normalizeHost(host),
                    username = entry.encryptedUsername?.let(::decrypt) ?: entry.username,
                    password = decrypt(entry.password),
                ),
                encryptedUsername = entry.encryptedUsername != null,
            )
        }
            .onFailure { error ->
                PluginLogger.warn(
                    "plugin.web_login.decrypt_failed",
                    mapOf("error" to (error.message ?: error.javaClass.simpleName)),
                )
            }
            .getOrNull()
    }

    fun save(pluginId: String, credential: WebLoginCredential): Boolean {
        val entry = runCatching {
            StoredEntry(password = encrypt(credential.password), encryptedUsername = encrypt(credential.username))
        }
            .onFailure { error ->
                PluginLogger.error(
                    "plugin.web_login.encrypt_failed",
                    mapOf("error" to (error.message ?: error.javaClass.simpleName)),
                )
            }
            .getOrNull() ?: return false
        val owners = policy.owners(pluginId, credential.host)
        val editor = prefs.edit()
            .putString(entryKey(owners.first(), credential.host), json.encodeToString(StoredEntry.serializer(), entry))
        owners.forEach { editor.remove(neverKey(it, credential.host)) }
        owners.drop(1).forEach { editor.remove(entryKey(it, credential.host)) }
        editor.apply()
        return true
    }

    fun isNeverSave(pluginId: String, host: String): Boolean =
        policy.owners(pluginId, host).any { prefs.getBoolean(neverKey(it, host), false) }

    fun setNeverSave(pluginId: String, host: String) {
        prefs.edit().putBoolean(neverKey(policy.owners(pluginId, host).first(), host), true).apply()
    }

    fun hasAny(pluginId: String): Boolean {
        return keysFor(pluginId, setOf(ENTRY_PREFIX, NEVER_PREFIX)).isNotEmpty()
    }

    fun hasSavedCredentials(pluginId: String): Boolean =
        keysFor(pluginId, setOf(ENTRY_PREFIX)).any { key ->
            find(pluginId, key.substringAfterLast('|')) != null
        }

    fun clear(pluginId: String) {
        val editor = prefs.edit()
        keysFor(pluginId, setOf(ENTRY_PREFIX, NEVER_PREFIX, ACCOUNT_PREFIX)).forEach(editor::remove)
        editor.apply()
    }

    private fun keysFor(pluginId: String, prefixes: Set<String>): List<String> = prefs.all.keys.filter { key ->
        val prefix = prefixes.firstOrNull(key::startsWith) ?: return@filter false
        val parts = key.removePrefix(prefix).split('|', limit = 2)
        parts.size == 2 && policy.owns(pluginId, parts[0], parts[1])
    }

    /**
     * Record the account last submitted on [host]; true when it differs from a previously recorded
     * one. Only a hash is kept, so the marker works even when the user declines saving passwords.
     */
    fun rememberAccount(pluginId: String, host: String, username: String): Boolean {
        val owners = policy.owners(pluginId, host)
        val key = accountKey(owners.first(), host)
        val marker = accountMarker(username)
        val previous = owners.firstNotNullOfOrNull { prefs.getString(accountKey(it, host), null) }
            ?: find(pluginId, host)?.username?.let(::accountMarker)
        val editor = prefs.edit().putString(key, marker)
        owners.drop(1).forEach { editor.remove(accountKey(it, host)) }
        editor.apply()
        return previous != marker
    }

    fun forgetAccounts(pluginId: String) {
        val editor = prefs.edit()
        keysFor(pluginId, setOf(ACCOUNT_PREFIX)).forEach(editor::remove)
        editor.apply()
    }

    private fun accountKey(pluginId: String, host: String) = "$ACCOUNT_PREFIX$pluginId|${normalizeHost(host)}"

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
    private data class StoredEntry(
        val username: String = "",
        val password: String,
        val encryptedUsername: String? = null,
    )

    companion object {
        /** Keep storage names aligned with app backup exclusion rules. */
        const val PREFS_NAME = "plugin_web_credentials"
        private const val ENTRY_PREFIX = "cred|"
        private const val NEVER_PREFIX = "never|"
        private const val ACCOUNT_PREFIX = "account|"
        private const val KEY_ALIAS = "cursimple_plugin_web_credentials"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_IV_BYTES = 12
        private const val GCM_TAG_BITS = 128
        private val json = Json { ignoreUnknownKeys = true }
    }
}

/** Install autofill and submit observation; [onCaptured] runs on the main thread. */
internal class WebLoginAssist(
    private val pluginId: String,
    private val allowedHosts: List<String>,
    private val store: WebLoginCredentialStore,
    private val onCaptured: (WebLoginCredential) -> Unit,
    private val onAccountChanged: (host: String) -> Unit = {},
) {

    var suppressAutofill = false

    fun install(webView: WebView) {
        webView.addJavascriptInterface(Bridge(webView), BRIDGE_NAME)
    }

    fun onPageFinished(webView: WebView, url: String?) {
        val host = hostOf(url) ?: return
        if (!isLoginPageAllowed(url)) return
        val saved = if (suppressAutofill) null else store.find(pluginId, host)
        webView.evaluateJavascript(webLoginAssistScript(host, saved), null)
    }

    private inner class Bridge(private val webView: WebView) {
        /** Clear other portal sessions while the user moves from username to password. */
        @JavascriptInterface
        fun onAccountInput(host: String?, username: String?) {
            val reportedHost = normalizeHost(host.orEmpty())
            val user = username.orEmpty().trim()
            if (reportedHost.isBlank() || user.isBlank() || user.length > MAX_FIELD_CHARS) return
            webView.post {
                val currentHost = hostOf(webView.url) ?: return@post
                if (currentHost != reportedHost || !isLoginPageAllowed(webView.url)) return@post
                if (store.rememberAccount(pluginId, currentHost, user)) onAccountChanged(currentHost)
            }
        }
        /** Accept credential reports only from the current allowlisted top-level host. */
        @JavascriptInterface
        fun onLoginSubmit(host: String?, username: String?, password: String?) {
            val reportedHost = normalizeHost(host.orEmpty())
            val user = username.orEmpty().trim().take(MAX_FIELD_CHARS)
            val pass = password.orEmpty().take(MAX_FIELD_CHARS)
            if (reportedHost.isBlank() || user.isBlank() || pass.isBlank()) return
            webView.post {
                val currentHost = hostOf(webView.url) ?: return@post
                if (currentHost != reportedHost) return@post
                if (!isLoginPageAllowed(webView.url)) return@post
                if (store.rememberAccount(pluginId, currentHost, user)) onAccountChanged(currentHost)
                if (store.isNeverSave(pluginId, currentHost)) return@post
                val saved = store.find(pluginId, currentHost)
                if (saved != null && saved.username == user && saved.password == pass) return@post
                onCaptured(WebLoginCredential(host = currentHost, username = user, password = pass))
            }
        }
    }

    private fun isLoginPageAllowed(url: String?): Boolean =
        runCatching { Uri.parse(url.orEmpty()).scheme.equals("https", true) }.getOrDefault(false) &&
            isAllowedHost(url.orEmpty(), allowedHosts)

    companion object {
        const val BRIDGE_NAME = "CurSimpleLoginBridge"
        private const val MAX_FIELD_CHARS = 256
    }
}

private fun hostOf(url: String?): String? =
    runCatching { Uri.parse(url.orEmpty()).host }.getOrNull()
        ?.let(::normalizeHost)
        ?.takeIf(String::isNotBlank)

/** SHA-256 of the account name; the name itself is never stored. */
internal fun accountMarker(username: String): String {
    val digest = java.security.MessageDigest.getInstance("SHA-256")
        .digest(username.trim().toByteArray(Charsets.UTF_8))
    return digest.joinToString("") { "%02x".format(it) }
}

private fun normalizeHost(host: String): String = host.trim().trimEnd('.').lowercase(Locale.ROOT)

/**
 * Observe clicks, Enter and form.submit before page encryption. MutationObserver handles
 * delayed forms; native setters and input/change events update framework bindings.
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
            // Prefer an account-named visible field preceding the password.
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
            // Fill once per field; preserve user clearing or account changes.
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
            // Typing another account must not submit the saved account's password.
            document.addEventListener("input", function (event) {
              var pwd = passwordField();
              if (!currentSaved || !pwd || !pwd.__cursimpleFilled || event.target === pwd) { return; }
              var user = usernameField(pwd);
              if (event.target === user && user.value !== currentSaved.u && pwd.value === currentSaved.p) {
                setValue(pwd, "");
              }
            }, true);
            document.addEventListener("focusout", function (event) {
              var pwd = passwordField();
              if (!pwd) { return; }
              var user = usernameField(pwd);
              if (event.target !== user || !user.value) { return; }
              try { window.${WebLoginAssist.BRIDGE_NAME}.onAccountInput(location.hostname, user.value); } catch (e) {}
            }, true);
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
