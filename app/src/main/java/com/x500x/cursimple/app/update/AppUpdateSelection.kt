package com.x500x.cursimple.app.update

import java.io.IOException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.PortUnreachableException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException

/** 单个更新源返回的文本响应 */
data class UpdateSourceResponse(
    val sourceName: String,
    val statusCode: Int,
    val body: String,
    val latencyMillis: Long,
)

/** 单个更新源的请求结果：要么拿到响应，要么在网络层失败 */
data class UpdateSourceAttempt(
    val sourceName: String,
    val response: UpdateSourceResponse? = null,
    val errorMessage: String? = null,
)

sealed interface UpdateSourceSelection {
    /** 至少有一个源返回 2xx，取其中最快的一个 */
    data class Success(val response: UpdateSourceResponse) : UpdateSourceSelection

    /** 拿到响应的源一致指向“资源不存在” */
    data object NotFound : UpdateSourceSelection

    /** 拿到响应，但状态码不可用 */
    data class HttpError(val sourceName: String, val statusCode: Int) : UpdateSourceSelection

    /** 所有源都返回 2xx，但正文都不是期望格式 */
    data class UnusableBody(val sourceName: String) : UpdateSourceSelection

    /** 没有任何源返回响应 */
    data class Unreachable(val attempts: List<UpdateSourceAttempt>) : UpdateSourceSelection
}

object UpdateSourceSelector {
    /** GitHub 源站的 404 可以直接判定为没有该资源，代理源的 404 不具备同等权威性 */
    const val AUTHORITATIVE_SOURCE_NAME = "GitHub 源站"

    private const val HTTP_NOT_FOUND = 404

    /**
     * [isUsableBody] 用来剔除返回 2xx 但正文不是期望格式的代理源，
     * 这类响应不能算命中，否则会盖掉源站给出的权威状态码。
     */
    fun select(
        attempts: List<UpdateSourceAttempt>,
        isUsableBody: (String) -> Boolean = { true },
    ): UpdateSourceSelection {
        val responses = attempts.mapNotNull { it.response }
        if (responses.isEmpty()) {
            return UpdateSourceSelection.Unreachable(attempts)
        }

        val successful = responses.filter { it.statusCode in 200..299 && isUsableBody(it.body) }
        if (successful.isNotEmpty()) {
            return UpdateSourceSelection.Success(successful.minWith(RESPONSE_ORDER))
        }

        val usableResponses = responses.filter { it.statusCode !in 200..299 }
        if (usableResponses.isEmpty()) {
            return UpdateSourceSelection.UnusableBody(responses.minWith(RESPONSE_ORDER).sourceName)
        }

        val authoritativeNotFound = usableResponses.any {
            it.sourceName == AUTHORITATIVE_SOURCE_NAME && it.statusCode == HTTP_NOT_FOUND
        }
        if (authoritativeNotFound || usableResponses.all { it.statusCode == HTTP_NOT_FOUND }) {
            return UpdateSourceSelection.NotFound
        }

        val reported = usableResponses.firstOrNull { it.sourceName == AUTHORITATIVE_SOURCE_NAME }
            ?: usableResponses.filter { it.statusCode != HTTP_NOT_FOUND }.minWith(RESPONSE_ORDER)
        return UpdateSourceSelection.HttpError(reported.sourceName, reported.statusCode)
    }

    private val RESPONSE_ORDER = compareBy<UpdateSourceResponse>({ it.latencyMillis }, { it.sourceName })
}

/** 把选择结果转成用户可读的失败提示，成功与“没有发布版本”返回 null */
fun updateSourceFailureMessage(selection: UpdateSourceSelection): String? = when (selection) {
    is UpdateSourceSelection.Success -> null
    UpdateSourceSelection.NotFound -> null
    is UpdateSourceSelection.HttpError ->
        "检查更新失败：${selection.sourceName} 返回 HTTP ${selection.statusCode}，请稍后重试。"
    is UpdateSourceSelection.UnusableBody ->
        "检查更新失败：${selection.sourceName} 返回的内容不是更新信息，请稍后重试。"
    is UpdateSourceSelection.Unreachable -> {
        val detail = selection.attempts.firstNotNullOfOrNull { attempt ->
            attempt.errorMessage?.takeIf { it.isNotBlank() }
        }
        if (detail == null) {
            "检查更新失败：没有可用的更新源。"
        } else {
            "检查更新失败：无法连接任何更新源（$detail），请检查网络后重试。"
        }
    }
}

sealed interface UpdateAssetSelection {
    data class Matched(val asset: AppUpdateAsset) : UpdateAssetSelection

    /** 清单里没有任何字段完整的安装包 */
    data object NoAsset : UpdateAssetSelection

    /** 有安装包，但没有本机 ABI 也没有 universal */
    data class NoCompatibleAbi(
        val deviceAbis: List<String>,
        val availableAbis: List<String>,
    ) : UpdateAssetSelection
}

object UpdateAssetSelector {
    const val UNIVERSAL_ABI = "universal"

    fun select(assets: List<AppUpdateAsset>, deviceAbis: List<String>): UpdateAssetSelection {
        if (assets.isEmpty()) {
            return UpdateAssetSelection.NoAsset
        }
        deviceAbis
            .firstNotNullOfOrNull { abi -> assets.firstOrNull { it.abi.equals(abi, ignoreCase = true) } }
            ?.let { return UpdateAssetSelection.Matched(it) }
        assets
            .firstOrNull { it.abi.equals(UNIVERSAL_ABI, ignoreCase = true) }
            ?.let { return UpdateAssetSelection.Matched(it) }
        return UpdateAssetSelection.NoCompatibleAbi(
            deviceAbis = deviceAbis,
            availableAbis = assets.map { it.abi }.distinct(),
        )
    }
}

/** 把资产选择结果转成用户可读的失败提示，命中时返回 null */
fun updateAssetFailureMessage(selection: UpdateAssetSelection): String? = when (selection) {
    is UpdateAssetSelection.Matched -> null
    UpdateAssetSelection.NoAsset -> "更新清单没有提供可用的安装包，请前往 Release 页面手动下载。"
    is UpdateAssetSelection.NoCompatibleAbi -> buildString {
        append("没有适配本机的安装包")
        selection.deviceAbis.firstOrNull()?.takeIf { it.isNotBlank() }?.let { append("（本机 $it）") }
        append("。清单提供：")
        append(selection.availableAbis.joinToString("、").ifBlank { "无" })
        append("。请前往 Release 页面手动下载。")
    }
}

private val HTTP_STATUS_DETAIL = Regex("""HTTP \d{3}""")

/**
 * 过滤第三方库与 JDK 抛出的英文异常文本，只保留自己写的中文提示或 HTTP 状态描述。
 * 无法识别时返回 null，由调用方给出统一文案。
 */
fun readableFailureDetail(message: String?): String? {
    val trimmed = message?.trim().orEmpty()
    if (trimmed.isEmpty()) {
        return null
    }
    if (trimmed.any { it.code in 0x4E00..0x9FFF }) {
        return trimmed
    }
    return HTTP_STATUS_DETAIL.find(trimmed)?.value
}

/** 把更新链路上抛出的异常转成用户可读的中文描述 */
fun describeUpdateError(error: Throwable): String {
    networkErrorDescription(error)?.let { return it }
    return readableFailureDetail(error.message) ?: "未知错误"
}

/** 下载环节的失败提示，屏蔽底层英文异常文本 */
fun updateDownloadFailureMessage(rawMessage: String?): String =
    "下载失败：${readableFailureDetail(rawMessage) ?: "请检查网络后重试"}"

private const val JSON_EXCEPTION_CLASS_NAME = "org.json.JSONException"

private fun networkErrorDescription(error: Throwable): String? = when {
    error is UnknownHostException -> "无法解析服务器地址"
    error is SocketTimeoutException -> "连接超时"
    error is ConnectException -> "无法连接服务器"
    error is NoRouteToHostException || error is PortUnreachableException -> "网络不可达"
    error is SSLException -> "安全连接失败"
    error.javaClass.name == JSON_EXCEPTION_CLASS_NAME -> "更新信息格式错误"
    error is IOException -> "网络请求失败"
    else -> null
}

/** 更新清单里的版本字段是否足以判断新旧，不足时返回用户可读的原因。 */
fun updateManifestVersionProblem(versionCode: Int, versionName: String): String? = when {
    versionCode <= 0 -> "更新清单缺少版本号，无法判断是否有新版本。"
    versionName.isBlank() -> "更新清单缺少版本名称，无法确认新版本。"
    else -> null
}
