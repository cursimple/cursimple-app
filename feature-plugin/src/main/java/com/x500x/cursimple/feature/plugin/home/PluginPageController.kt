package com.x500x.cursimple.feature.plugin.home

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.webkit.JavascriptInterface
import com.x500x.cursimple.core.plugin.install.InstalledPluginRecord
import com.x500x.cursimple.core.plugin.manifest.PluginManifest
import com.x500x.cursimple.feature.plugin.WebLoginCredential
import com.x500x.cursimple.feature.plugin.WebLoginCredentialStore
import com.x500x.cursimple.feature.plugin.extension.ExtensionFailureKind
import com.x500x.cursimple.feature.plugin.extension.ExtensionRunMode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.*

/** The object a plugin page calls into. */
internal class PluginPageJsBridge(private val onCall: (String, String, String) -> Unit) {
    @JavascriptInterface
    fun call(id: String, method: String, args: String) = onCall(id, method, args)
}

/**
 * Answers a plugin page's calls. The commands are generic: run one of the plugin's script functions, keep the
 * plugin's own key/value storage, open the plugin's sign-in page, and show a sheet of buttons the plugin names.
 * The host never reads what the page stores or what the script returns.
 */
internal class PluginPageController(
    private val context: Context,
    private val record: InstalledPluginRecord,
    private val manifest: PluginManifest,
    private val script: String,
    private val storage: PluginPageStorage,
    private val credentials: WebLoginCredentialStore,
    private val scope: CoroutineScope,
    private val language: String,
    private val theme: () -> JsonObject,
    private val respond: (String, Boolean, String) -> Unit,
    private val emit: (String, String) -> Unit,
    private val onOpenLogin: (PluginPageLoginRequest) -> Unit,
    private val onCloseLogin: () -> Unit,
    private val onLoginMessage: (String) -> Unit,
    /** Shows a working overlay over the sign-in sheet with the page's own words; null hides it. */
    private val onLoginBusy: (PluginPageLoginBusy?) -> Unit = {},
    /** Moves the open sign-in sheet to another allowed address. */
    private val onLoginNavigate: (String) -> Unit = {},
) {
    /** The sign-in page the user saved from the sheet, held until the page's next run uses it. */
    private var capturedPage: String? = null
    /** The account the user just typed into the sign-in sheet; saved only when the page confirms the login. */
    private var typedCredential: WebLoginCredential? = null
    private val runLock = Mutex()

    /** One page call in, one response out. Unknown commands and malformed input are refused. */
    fun handle(id: String, method: String, args: String) {
        if (id.length > 32 || method.length > 64 || args.length > MAX_PAGE_REQUEST_CHARS) return
        scope.launch {
            try {
                respond(id, true, dispatch(method, parseArgs(args)).toString())
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Throwable) {
                respond(id, false, errorJson(failure).toString())
            }
        }
    }

    /** A sign-in sheet button was tapped. [html] is the page the sheet shows; it is passed only for buttons that capture. */
    fun onLoginAction(id: String, html: String?) {
        val accepted = acceptedCapturedPage(html)
        capturedPage = accepted
        emit("loginAction", buildJsonObject {
            put("id", id)
            put("captured", accepted != null)
        }.toString())
    }

    fun onLoginDismissed() {
        capturedPage = null
        typedCredential = null
        emit("loginClosed", "{}")
    }

    /**
     * The sign-in sheet started ([loading]) or finished loading a page.
     * Only the address without its query goes to the page.
     */
    fun onLoginPage(url: String, loading: Boolean = false) {
        val uri = runCatching { java.net.URI(url) }.getOrNull() ?: return
        val address = "${uri.scheme}://${uri.rawAuthority}${uri.rawPath.orEmpty()}"
        // Single-page portals name their screen in the fragment (e.g. "/app_center"); its own query stays private.
        val route = uri.rawFragment.orEmpty().substringBefore('?').take(120)
        emit("loginPage", buildJsonObject {
            put("url", address)
            put("route", route)
            put("loading", loading)
        }.toString())
    }

    /** The user submitted an account in the sign-in sheet. */
    fun onCredentialTyped(credential: WebLoginCredential) {
        typedCredential = credential
    }

    private suspend fun dispatch(method: String, args: JsonObject): JsonObject = when (method) {
        "init" -> init()
        "run" -> runScript(args)
        "storageGet" -> storageGet(args)
        "storageSet" -> storageSet(args)
        "storageRemove" -> storageRemove(args)
        "clearCredentials" -> clearCredentials()
        "openLogin" -> openLogin(args)
        "closeLogin" -> closeLogin()
        "setLoginMessage" -> setLoginMessage(args)
        "saveLogin" -> saveLogin()
        "setLoginBusy" -> setLoginBusy(args)
        "probe" -> probe()
        "navigateLogin" -> navigateLogin(args)
        else -> throw PluginPageCodeException("unknown_command")
    }

    private suspend fun init(): JsonObject {
        val saved = withContext(Dispatchers.IO) { credentials.hasSavedCredentials(record.pluginId) }
        return buildJsonObject {
            put("locale", localeTag())
            put("pluginName", record.name)
            put("theme", theme())
            put("online", isOnline())
            put("credentialsSaved", saved)
            put("lastSyncAt", PluginSyncStamp.last(context, record.pluginId))
            manifest.home?.runUrl?.let { url ->
                runCatching { java.net.URI(url) }.getOrNull()?.let { put("runOrigin", "${it.scheme}://${it.rawAuthority}") }
            }
        }
    }

    /** Keeps the account typed in the sign-in sheet once the page has confirmed it signs in. */
    private suspend fun saveLogin(): JsonObject {
        val credential = typedCredential
        typedCredential = null
        val saved = withContext(Dispatchers.IO) {
            if (credential != null) credentials.save(record.pluginId, credential)
            credentials.hasSavedCredentials(record.pluginId)
        }
        return buildJsonObject { put("credentialsSaved", saved) }
    }

    private suspend fun runScript(args: JsonObject): JsonObject {
        return runLock.withLock { runScriptLocked(args) }
    }

    private suspend fun runScriptLocked(args: JsonObject): JsonObject {
        val mode = when (args.string("mode", 32)) {
            "checkLogin" -> ExtensionRunMode.CheckLogin
            "sync" -> ExtensionRunMode.Sync
            else -> throw PluginPageCodeException("invalid_argument")
        }
        val raw = args["action"] as? JsonObject ?: JsonObject(emptyMap())
        val fromCapture = (raw["pageFromLogin"] as? JsonPrimitive)?.booleanOrNull == true
        val action = JsonObject(raw - "pageFromLogin")
        if (action.toString().length > MAX_ACTION_CHARS) throw PluginPageCodeException("invalid_argument")
        val page = if (fromCapture) capturedPage ?: throw PluginPageCodeException("no_captured_page") else null
        capturedPage = null
        return withContext(Dispatchers.IO) {
            runPluginPageScript(context, manifest, script, mode, action, language, page)
        }
    }

    private suspend fun storageGet(args: JsonObject): JsonObject {
        val key = args.storageKey()
        val value = withContext(Dispatchers.IO) { storage.get(key) }
        return buildJsonObject { put("value", value?.let { JsonPrimitive(it) } ?: JsonNull) }
    }

    private suspend fun storageSet(args: JsonObject): JsonObject {
        val key = args.storageKey()
        val value = args.string("value", MAX_STORAGE_VALUE_CHARS)
        withContext(Dispatchers.IO) { storage.set(key, value) }
        return JsonObject(emptyMap())
    }

    private suspend fun storageRemove(args: JsonObject): JsonObject {
        val key = args.storageKey()
        withContext(Dispatchers.IO) { storage.remove(key) }
        return JsonObject(emptyMap())
    }

    private suspend fun clearCredentials(): JsonObject {
        withContext(Dispatchers.IO) { credentials.clear(record.pluginId) }
        return buildJsonObject { put("credentialsSaved", false) }
    }

    private fun openLogin(args: JsonObject): JsonObject {
        val url = args.string("url", 2048)
        if (!validLoginUrl(url, manifest.allowedHosts)) throw PluginPageCodeException("invalid_url")
        val help = args.stringField("helpText").orEmpty().take(2_000)
        val title = args.stringField("title").orEmpty().take(40)
        val autoSubmit = (args["autoSubmit"] as? JsonPrimitive)?.booleanOrNull == true
        onOpenLogin(PluginPageLoginRequest(url, help, parseLoginActions(args["actions"]), title, autoSubmit))
        return JsonObject(emptyMap())
    }

    private fun closeLogin(): JsonObject {
        onLoginDismissed()
        onCloseLogin()
        return JsonObject(emptyMap())
    }

    /**
     * Whether the plugin's system answers, before the page starts a full query, and where its run address lands.
     * Follows up to five redirects with the sign-in cookies the sheet already holds, reading only status lines.
     * [landedUrl] is the final address without its query, so the page can tell a lost login from a slow system.
     */
    private suspend fun probe(): JsonObject {
        if (!isOnline()) return buildJsonObject { put("online", false); put("reachable", false) }
        val target = manifest.home?.runUrl?.takeIf { validLoginUrl(it, manifest.allowedHosts) }
            ?: throw PluginPageCodeException("invalid_url")
        val landed = withContext(Dispatchers.IO) {
            withTimeoutOrNull(PROBE_TIMEOUT_MS * 2) { followRunAddress(target) }
        }
        return buildJsonObject {
            put("online", true)
            put("reachable", landed != null)
            landed?.let { put("landedUrl", it) }
        }
    }

    /** The address the run URL finally lands on, or null when the system does not answer. */
    private fun followRunAddress(start: String): String? {
        var url = start
        repeat(PROBE_MAX_REDIRECTS + 1) {
            val code: Int
            val location: String?
            val connection = runCatching { java.net.URL(url).openConnection() as java.net.HttpURLConnection }.getOrNull() ?: return null
            try {
                connection.instanceFollowRedirects = false
                connection.useCaches = false
                connection.connectTimeout = PROBE_TIMEOUT_MS.toInt()
                connection.readTimeout = PROBE_TIMEOUT_MS.toInt()
                manifest.userAgent?.takeIf { it.isNotBlank() }?.let { connection.setRequestProperty("User-Agent", it) }
                android.webkit.CookieManager.getInstance().getCookie(url)?.let { connection.setRequestProperty("Cookie", it) }
                code = runCatching { connection.responseCode }.getOrNull() ?: return null
                location = connection.getHeaderField("Location")
            } finally {
                connection.disconnect()
            }
            if (code >= 500) return null
            val next = location?.takeIf { code in 300..399 }
                ?.let { runCatching { java.net.URI(url).resolve(it).toString() }.getOrNull() }
            if (next == null || !next.startsWith("http")) {
                return runCatching { java.net.URI(url).let { "${it.scheme}://${it.rawAuthority}${it.rawPath.orEmpty()}" } }.getOrNull()
            }
            url = next
        }
        return runCatching { java.net.URI(url).let { "${it.scheme}://${it.rawAuthority}${it.rawPath.orEmpty()}" } }.getOrNull()
    }

    private fun navigateLogin(args: JsonObject): JsonObject {
        val url = args.string("url", 2048)
        if (!validLoginUrl(url, manifest.allowedHosts)) throw PluginPageCodeException("invalid_url")
        onLoginNavigate(url)
        return JsonObject(emptyMap())
    }

    private fun setLoginBusy(args: JsonObject): JsonObject {
        val title = args.stringField("title")?.take(80)?.takeIf { it.isNotBlank() }
        onLoginBusy(title?.let { PluginPageLoginBusy(it, args.stringField("text")?.take(200).orEmpty()) })
        return JsonObject(emptyMap())
    }

    private fun setLoginMessage(args: JsonObject): JsonObject {
        onLoginMessage(args.string("text", 500))
        return JsonObject(emptyMap())
    }

    private fun localeTag(): String = when {
        language.startsWith("zh", true) && listOf("TW", "HK", "Hant").any { language.contains(it, true) } -> "zh-TW"
        language.startsWith("zh", true) -> "zh-CN"
        else -> "en"
    }

    /** The code tells the page what happened; the page decides the words. */
    private fun errorJson(failure: Throwable): JsonObject {
        val offline = !isOnline()
        val (code, detail) = when (failure) {
            is PluginPageCodeException -> failure.code to failure.detail
            is PluginPageRunException -> (if (offline) "offline" else failure.kind.code()) to failure.message.orEmpty()
            else -> (if (offline) "offline" else "failed") to failure.message.orEmpty()
        }
        return buildJsonObject {
            put("code", code)
            put("detail", detail.take(300))
        }
    }

    private fun ExtensionFailureKind.code(): String = when (this) {
        ExtensionFailureKind.Unreachable -> "unreachable"
        ExtensionFailureKind.Timeout -> "timeout"
        ExtensionFailureKind.Script -> "script"
    }

    private fun isOnline(): Boolean = runCatching {
        val manager = context.getSystemService(ConnectivityManager::class.java)
        val capabilities = manager.getNetworkCapabilities(manager.activeNetwork)
        capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
    }.getOrDefault(true)

    private fun parseArgs(args: String): JsonObject =
        runCatching { Json.parseToJsonElement(args) as? JsonObject }.getOrNull()
            ?: throw PluginPageCodeException("invalid_argument")

    private fun JsonObject.string(key: String, max: Int): String {
        val value = stringField(key) ?: throw PluginPageCodeException("invalid_argument")
        if (value.length > max) throw PluginPageCodeException("invalid_argument")
        return value
    }

    private fun JsonObject.storageKey(): String {
        val key = string("key", 64)
        if (!validStorageKey(key)) throw PluginPageCodeException("invalid_argument")
        return key
    }
}

/** How long the reachability check waits to connect and to read, per request. */
private const val PROBE_TIMEOUT_MS = 4_000L
private const val PROBE_MAX_REDIRECTS = 5
