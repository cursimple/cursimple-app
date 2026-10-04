package com.x500x.cursimple.core.plugin.market.github

import okhttp3.Call
import okhttp3.MediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody
import okhttp3.ResponseBody.Companion.toResponseBody
import java.io.IOException
import java.util.concurrent.CopyOnWriteArrayList
import okio.Buffer
import okio.BufferedSource

/** Scripted single-hop HTTP responses: no DNS, credentials or live GitHub access. */
internal class GitHubTestTransport(private val respond: (Request) -> Reply) : Call.Factory {
    data class Reply(
        val body: String = "",
        val code: Int = 200,
        val location: String? = null,
        val failure: IOException? = null,
        val unknownLength: Boolean = false,
    )

    val requests = CopyOnWriteArrayList<Request>()
    private val client = OkHttpClient.Builder().followRedirects(false).addInterceptor { chain ->
        val request = chain.request()
        requests += request
        val reply = respond(request)
        reply.failure?.let { throw it }
        val body = if (reply.unknownLength) object : ResponseBody() {
            private val content = Buffer().writeUtf8(reply.body)
            override fun contentType(): MediaType? = null
            override fun contentLength(): Long = -1L
            override fun source(): BufferedSource = content
        } else reply.body.toResponseBody()
        Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(reply.code)
            .message("fixture").body(body)
            .apply { reply.location?.let { header("Location", it) } }.build()
    }.build()

    override fun newCall(request: Request): Call = client.newCall(request)
    fun api() = GitHubApiClient(transport = this)
}

internal fun repoFixture(slug: String, private: Boolean = false): String = """
    {"full_name":"$slug","name":"${slug.substringAfter('/')}","private":$private,
    "description":"fixture repo","owner":{"login":"${slug.substringBefore('/')}","avatar_url":"https://github.com/avatar.png"}}
""".trimIndent()

internal fun releaseFixture(slug: String, filename: String = "demo.zip"): String = """
    {"tag_name":"v2","assets":[
    {"name":"manifest.json","url":"https://api.github.com/repos/$slug/releases/assets/1","size":99},
    {"name":"$filename","url":"https://api.github.com/repos/$slug/releases/assets/2","size":1234}]}
""".trimIndent()

internal fun publicRepoApi() = GitHubTestTransport { request ->
    GitHubTestTransport.Reply(repoFixture(request.url.pathSegments.drop(1).take(2).joinToString("/")))
}.api()
