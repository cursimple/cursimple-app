package com.x500x.cursimple.core.js

import app.cash.quickjs.QuickJs
import com.x500x.cursimple.core.kernel.model.TermSchedule
import com.x500x.cursimple.core.kernel.plugin.PluginDescriptor
import com.x500x.cursimple.core.kernel.plugin.PluginSyncRequest
import com.x500x.cursimple.core.kernel.plugin.SchedulePluginExecutor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class QuickJsScheduleExecutor(
    private val hostBridge: JsHostBridge,
    private val json: Json = Json { ignoreUnknownKeys = true; encodeDefaults = true },
    private val timeoutMs: Long = DEFAULT_TIMEOUT_MS,
) : SchedulePluginExecutor {

    override suspend fun execute(
        descriptor: PluginDescriptor,
        script: String,
        request: PluginSyncRequest,
    ): TermSchedule = withContext(Dispatchers.Default) {
        withTimeout(timeoutMs) {
            QuickJs.create().use { quickJs ->
                quickJs.set(HOST_OBJECT_NAME, HostApi::class.java, JsHostApi(hostBridge))
                quickJs.evaluate(script)
                ensurePluginShape(quickJs, descriptor.id)

                val contextJson = json.encodeToString(request.context)
                val credentialJson = json.encodeToString(request.credentials)
                val termJson = json.encodeToString(mapOf("termId" to request.context.termId))

                val sessionJson = invokeJsonFunction(
                    quickJs = quickJs,
                    functionName = "login",
                    jsonArgs = arrayOf(contextJson, credentialJson),
                )
                val rawScheduleJson = invokeJsonFunction(
                    quickJs = quickJs,
                    functionName = "fetchSchedule",
                    jsonArgs = arrayOf(contextJson, sessionJson, termJson),
                )
                val normalizedJson = invokeJsonFunction(
                    quickJs = quickJs,
                    functionName = "normalize",
                    jsonArgs = arrayOf(rawScheduleJson),
                )

                json.decodeFromString<TermSchedule>(normalizedJson)
            }
        }
    }

    private fun ensurePluginShape(quickJs: QuickJs, pluginId: String) {
        val checker = """
            (() => {
                const required = ["login", "fetchSchedule", "normalize"];
                const missing = required.filter((name) => typeof globalThis[name] !== "function");
                if (missing.length > 0) {
                    throw new Error("plugin[" + ${asJsString(pluginId)} + "] missing functions: " + missing.join(","));
                }
                return true;
            })();
        """.trimIndent()
        quickJs.evaluate(checker)
    }

    private fun invokeJsonFunction(
        quickJs: QuickJs,
        functionName: String,
        jsonArgs: Array<String>,
    ): String {
        val argsExpr = jsonArgs.joinToString(",") { "JSON.parse(${asJsString(it)})" }
        val script = "JSON.stringify($functionName($argsExpr));"
        val result = quickJs.evaluate(script)
        return result as? String
            ?: throw IllegalStateException("函数 $functionName 返回值为空或非 JSON")
    }

    private fun asJsString(raw: String): String {
        return json.encodeToString(String.serializer(), raw)
    }

    private class JsHostApi(private val delegate: JsHostBridge) : HostApi {
        override fun httpRequest(requestJson: String): String = delegate.httpRequest(requestJson)
        override fun log(message: String) = delegate.log(message)
        override fun nowIso(): String = delegate.nowIso()
    }

    private companion object {
        const val HOST_OBJECT_NAME = "Host"
        const val DEFAULT_TIMEOUT_MS = 20_000L
    }
}

interface HostApi {
    fun httpRequest(requestJson: String): String
    fun log(message: String)
    fun nowIso(): String
}
