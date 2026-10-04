package com.x500x.cursimple.core.plugin.market.github

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Required
import kotlinx.serialization.json.Json
import okhttp3.FormBody
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.time.Duration
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * 直连 api.github.com 的小客户端，私有仓库全靠它。
 *
 * 令牌只在这里加到请求头上，而且只发给 api.github.com：公有仓库走的那些镜像、代理
 * 都是第三方，令牌绝不能经过它们。下载 Release 附件时 GitHub 会 302 到对象存储，
 * 手动处理 HTTPS 重定向：离开 API 主机后移除凭据，禁止降级到 HTTP。
 */
class GitHubApiClient(
    client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(Duration.ofSeconds(8))
        .callTimeout(Duration.ofSeconds(40))
        .build(),
    private val json: Json = Json { ignoreUnknownKeys = true },
    /** 测试用的单跳 transport；不能自行跟随重定向。生产路径始终禁用自动重定向。 */
    private val transport: Call.Factory = client.newBuilder()
        .followRedirects(false)
        .followSslRedirects(false)
        .build(),
) {
    class HttpError(val code: Int, message: String) : IOException(message)

    suspend fun repo(slug: String, token: String?): ApiRepo = get(repoUrl(slug).build().toString(), token).let {
        json.decodeFromString(ApiRepo.serializer(), it.decodeToString())
    }

    /** 读仓库某个分支上的文件原文。 */
    suspend fun fileText(slug: String, branch: String, path: String, token: String?): String =
        get(
            repoUrl(slug).addPathSegment("contents").apply {
                require(path.isNotBlank() && path.split('/').none { it.isBlank() || it == "." || it == ".." })
                path.split('/').forEach { addPathSegment(it) }
            }.addQueryParameter("ref", branch).build().toString(),
            token,
            accept = "application/vnd.github.raw+json",
        ).decodeToString()

    suspend fun latestRelease(slug: String, token: String?): ApiRelease =
        get(repoUrl(slug).addPathSegment("releases").addPathSegment("latest").build().toString(), token).let {
            json.decodeFromString(ApiRelease.serializer(), it.decodeToString())
        }

    /** 下载 Release 附件；[assetApiUrl] 是 `https://api.github.com/repos/o/r/releases/assets/<id>`。 */
    suspend fun downloadAsset(
        assetApiUrl: String,
        token: String?,
        onProgress: (downloaded: Long, total: Long) -> Unit = { _, _ -> },
    ): ByteArray {
        require(isAssetApiUrl(assetApiUrl)) { "不是 GitHub 附件地址" }
        return get(assetApiUrl, token, accept = "application/octet-stream", onProgress = onProgress)
    }

    suspend fun viewer(token: String): GitHubViewer = get("$API/user", token).let {
        val user = json.decodeFromString(ApiUser.serializer(), it.decodeToString())
        GitHubViewer(login = user.login, avatarUrl = user.avatarUrl)
    }

    /** 设备码登录第一步：拿到让用户去网页上输的码。 */
    suspend fun requestDeviceCode(clientId: String, scope: String): DeviceCode = post(
        "https://github.com/login/device/code",
        FormBody.Builder().add("client_id", clientId).add("scope", scope).build(),
    ).let { json.decodeFromString(DeviceCode.serializer(), it) }

    /** 设备码登录第二步：按间隔轮询，用户在网页上点了授权才会拿到令牌。 */
    suspend fun pollDeviceToken(clientId: String, deviceCode: String): DeviceTokenResponse = post(
        "https://github.com/login/oauth/access_token",
        FormBody.Builder()
            .add("client_id", clientId)
            .add("device_code", deviceCode)
            .add("grant_type", "urn:ietf:params:oauth:grant-type:device_code")
            .build(),
    ).let { json.decodeFromString(DeviceTokenResponse.serializer(), it) }

    private suspend fun get(
        url: String,
        token: String?,
        accept: String = "application/vnd.github+json",
        onProgress: (Long, Long) -> Unit = { _, _ -> },
    ): ByteArray =
        withContext(Dispatchers.IO) {
            val target = url.toHttpUrl()
            require(isApiOrigin(target)) { "只往 api.github.com 发令牌" }
            var request = Request.Builder()
                .url(target)
                .header("Accept", accept)
                .header("X-GitHub-Api-Version", "2022-11-28")
                .apply { token?.takeIf { it.isNotBlank() }?.let { header("Authorization", "Bearer $it") } }
                .build()
            for (hop in 0..MAX_REDIRECTS) {
                val response = send(request, onProgress)
                if (response.code !in REDIRECT_CODES) {
                    if (response.code !in 200..299) throw HttpError(response.code, "GitHub ${response.code}")
                    return@withContext response.bytes
                }
                if (hop == MAX_REDIRECTS) throw IOException("GitHub 重定向次数过多")
                val next = response.location?.let { request.url.resolve(it) }
                    ?: throw IOException("GitHub 重定向地址无效")
                if (!next.isHttps || next.username.isNotEmpty() || next.password.isNotEmpty()) {
                    throw IOException("GitHub 重定向地址不安全")
                }
                request = request.newBuilder().url(next).apply {
                    if (!isApiOrigin(next)) {
                        removeHeader("Authorization")
                        removeHeader("X-GitHub-Api-Version")
                    }
                }.build()
            }
            error("unreachable")
        }

    private suspend fun post(url: String, body: FormBody): String = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(url).header("Accept", "application/json").post(body).build()
        val response = send(request)
        if (response.code !in 200..299) throw HttpError(response.code, "GitHub ${response.code}")
        response.bytes.decodeToString()
    }

    private data class Reply(val code: Int, val location: String?, val bytes: ByteArray)

    private suspend fun send(request: Request, onProgress: (Long, Long) -> Unit = { _, _ -> }): Reply = suspendCancellableCoroutine { continuation ->
        val call = transport.newCall(request)
        continuation.invokeOnCancellation { call.cancel() }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (continuation.isActive) continuation.resumeWithException(e)
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    if (!continuation.isActive) return
                    try {
                        val bytes = if (response.isSuccessful) readDownloadBody(response.body, onProgress) {
                            if (!continuation.isActive) throw CancellationException("Download cancelled")
                        } else byteArrayOf()
                        continuation.resume(Reply(response.code, response.header("Location"), bytes))
                    } catch (error: Exception) {
                        if (continuation.isActive) continuation.resumeWithException(error)
                    }
                }
            }
        })
    }

    private fun repoUrl(slug: String): HttpUrl.Builder {
        require(GitHubRepoAddress.isSlug(slug)) { "不是 GitHub 仓库地址" }
        return API.toHttpUrl().newBuilder().addPathSegment("repos")
            .addPathSegment(slug.substringBefore('/')).addPathSegment(slug.substringAfter('/'))
    }

    @Serializable
    data class ApiRepo(
        @SerialName("full_name") val fullName: String = "",
        @SerialName("name") val name: String = "",
        @SerialName("description") val description: String? = null,
        @SerialName("stargazers_count") val stars: Int = 0,
        @SerialName("language") val language: String? = null,
        @SerialName("html_url") val htmlUrl: String = "",
        // 缺少 visibility 的异常响应不能被当成「已确认公有」交给镜像。
        @Required @SerialName("private") val isPrivate: Boolean = false,
        @SerialName("owner") val owner: ApiOwner = ApiOwner(),
    )

    @Serializable
    data class ApiOwner(
        @SerialName("login") val login: String = "",
        @SerialName("avatar_url") val avatarUrl: String = "",
    )

    @Serializable
    data class ApiRelease(
        @SerialName("tag_name") val tagName: String = "",
        @SerialName("assets") val assets: List<ApiAsset> = emptyList(),
    )

    @Serializable
    data class ApiAsset(
        @SerialName("name") val name: String = "",
        @SerialName("url") val url: String = "",
        @SerialName("size") val size: Long = 0,
    )

    @Serializable
    private data class ApiUser(
        @SerialName("login") val login: String = "",
        @SerialName("avatar_url") val avatarUrl: String = "",
    )

    @Serializable
    data class DeviceCode(
        @SerialName("device_code") val deviceCode: String = "",
        @SerialName("user_code") val userCode: String = "",
        @SerialName("verification_uri") val verificationUri: String = "https://github.com/login/device",
        @SerialName("expires_in") val expiresIn: Int = 900,
        @SerialName("interval") val interval: Int = 5,
    )

    @Serializable
    data class DeviceTokenResponse(
        @SerialName("access_token") val accessToken: String? = null,
        /** authorization_pending / slow_down / expired_token / access_denied */
        @SerialName("error") val error: String? = null,
        @SerialName("interval") val interval: Int? = null,
    )

    companion object {
        const val API = "https://api.github.com"

        private const val MAX_REDIRECTS = 10
        private val REDIRECT_CODES = setOf(301, 302, 303, 307, 308)
        private val ASSET_PATH = Regex("^/repos/([A-Za-z0-9-]+)/([A-Za-z0-9_.-]+)/releases/assets/[1-9][0-9]*$")

        private fun isApiOrigin(url: HttpUrl): Boolean = url.isHttps && url.host == "api.github.com" &&
            url.port == 443 && url.username.isEmpty() && url.password.isEmpty()

        fun isAssetApiUrl(url: String): Boolean {
            val parsed = url.toHttpUrlOrNull() ?: return false
            if (!isApiOrigin(parsed) || parsed.query != null || parsed.fragment != null) return false
            val match = ASSET_PATH.matchEntire(parsed.encodedPath) ?: return false
            return GitHubRepoAddress.isSlug("${match.groupValues[1]}/${match.groupValues[2]}")
        }
    }
}

/** 只根据响应体读取的字节报告进度；没有 Content-Length 时，完成回调也保留 -1。 */
internal fun readDownloadBody(
    body: ResponseBody,
    onProgress: (Long, Long) -> Unit,
    ensureActive: () -> Unit,
): ByteArray {
    val total = body.contentLength().takeIf { it >= 0L } ?: -1L
    ensureActive()
    onProgress(0L, total)
    val output = ByteArrayOutputStream()
    val buffer = ByteArray(64 * 1024)
    var downloaded = 0L
    var lastReportedAt = System.nanoTime() / 1_000_000L - 120L
    body.byteStream().use { input ->
        while (true) {
            ensureActive()
            val read = input.read(buffer)
            ensureActive()
            if (read < 0) break
            output.write(buffer, 0, read)
            downloaded += read
            val now = System.nanoTime() / 1_000_000L
            if (now - lastReportedAt >= 120L) {
                lastReportedAt = now
                onProgress(downloaded, total)
            }
        }
    }
    if (total >= 0L && downloaded != total) throw IOException("Incomplete download: $downloaded / $total bytes")
    ensureActive()
    onProgress(downloaded, total)
    return output.toByteArray()
}
