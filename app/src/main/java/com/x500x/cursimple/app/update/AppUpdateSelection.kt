package com.x500x.cursimple.app.update

import android.content.Context
import com.x500x.cursimple.R
import com.x500x.cursimple.app.download.DownloadFailureReason
import java.io.IOException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.PortUnreachableException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException

/** 更新链路上某个错误的可读原因，逻辑层只判定种类，文字由界面层按当前语言渲染。 */
sealed interface UpdateErrorReason {
    /** 域名无法解析。 */
    data object UnknownHost : UpdateErrorReason

    /** 连接超时。 */
    data object Timeout : UpdateErrorReason

    /** 无法建立连接。 */
    data object ConnectFailed : UpdateErrorReason

    /** 路由不可达或端口不可达。 */
    data object Unreachable : UpdateErrorReason

    /** 安全连接建立失败。 */
    data object TlsFailed : UpdateErrorReason

    /** 更新信息不是合法 JSON。 */
    data object MalformedManifest : UpdateErrorReason

    /** 其余网络请求失败。 */
    data object NetworkFailed : UpdateErrorReason

    /** 校验安装包摘要未通过。 */
    data object ChecksumFailed : UpdateErrorReason

    /** 没有任何可用的下载源。 */
    data object NoSource : UpdateErrorReason

    /** 无法归类的错误。 */
    data object Unknown : UpdateErrorReason

    /** 从异常文本里识别出的 HTTP 状态码。 */
    data class HttpStatus(val statusCode: Int) : UpdateErrorReason

    /** 异常文本已是可直接展示的内容，原样透传。 */
    data class Passthrough(val text: String) : UpdateErrorReason

    /** 下载更新清单失败，[detail] 为具体原因。 */
    data class ManifestDownloadFailed(val detail: UpdateErrorReason) : UpdateErrorReason

    /** 下载测速失败，[detail] 为具体原因。 */
    data class ProbeFailed(val detail: UpdateErrorReason) : UpdateErrorReason
}

/** 携带可读原因的更新异常，跨协程边界传递而不丢失类型。 */
class UpdateException(val reason: UpdateErrorReason) : Exception()

/** 展示给用户的更新状态文案的原因，界面层用 Context 渲染。 */
sealed interface UpdateStatusReason {
    /** 拿到响应但状态码不可用。 */
    data class SourceHttpError(val sourceName: String, val statusCode: Int) : UpdateStatusReason

    /** 源返回的正文不是更新信息。 */
    data class SourceUnusableBody(val sourceName: String) : UpdateStatusReason

    /** 没有任何可用的更新源。 */
    data object SourceNoneAvailable : UpdateStatusReason

    /** 所有源都连不上，[detail] 为首个可读原因。 */
    data class SourceUnreachable(val detail: UpdateErrorReason) : UpdateStatusReason

    /** 检查失败但拿不到更具体的原因。 */
    data object CheckRetry : UpdateStatusReason

    /** 检查过程中抛出异常，[detail] 为具体原因。 */
    data class CheckError(val detail: UpdateErrorReason) : UpdateStatusReason

    /** 更新清单缺少版本号。 */
    data object ManifestVersionCodeMissing : UpdateStatusReason

    /** 更新清单缺少版本名称。 */
    data object ManifestVersionNameMissing : UpdateStatusReason

    /** 更新清单没有提供任何可用安装包。 */
    data object AssetNoPackage : UpdateStatusReason

    /** 有安装包但没有适配本机的架构，[deviceAbi] 为本机首选架构。 */
    data class AssetNoCompatibleAbi(
        val deviceAbi: String?,
        val availableAbis: List<String>,
    ) : UpdateStatusReason

    /** 没有匹配当前设备的安装包，兜底提示。 */
    data object AssetNoMatch : UpdateStatusReason

    /** 下载失败，[detail] 为具体原因。 */
    data class DownloadDetail(val detail: UpdateErrorReason) : UpdateStatusReason

    /** 下载失败但拿不到更具体的原因。 */
    data object DownloadRetry : UpdateStatusReason
}

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
    val errorReason: UpdateErrorReason? = null,
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

/** 把选择结果转成失败原因，成功与“没有发布版本”返回 null */
fun updateSourceFailureMessage(selection: UpdateSourceSelection): UpdateStatusReason? = when (selection) {
    is UpdateSourceSelection.Success -> null
    UpdateSourceSelection.NotFound -> null
    is UpdateSourceSelection.HttpError ->
        UpdateStatusReason.SourceHttpError(selection.sourceName, selection.statusCode)
    is UpdateSourceSelection.UnusableBody ->
        UpdateStatusReason.SourceUnusableBody(selection.sourceName)
    is UpdateSourceSelection.Unreachable -> {
        val detail = selection.attempts.firstNotNullOfOrNull { it.errorReason }
        if (detail == null) {
            UpdateStatusReason.SourceNoneAvailable
        } else {
            UpdateStatusReason.SourceUnreachable(detail)
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

/** 把资产选择结果转成失败原因，命中时返回 null */
fun updateAssetFailureMessage(selection: UpdateAssetSelection): UpdateStatusReason? = when (selection) {
    is UpdateAssetSelection.Matched -> null
    UpdateAssetSelection.NoAsset -> UpdateStatusReason.AssetNoPackage
    is UpdateAssetSelection.NoCompatibleAbi -> UpdateStatusReason.AssetNoCompatibleAbi(
        deviceAbi = selection.deviceAbis.firstOrNull()?.takeIf { it.isNotBlank() },
        availableAbis = selection.availableAbis,
    )
}

private val HTTP_STATUS_DETAIL = Regex("""HTTP (\d{3})""")

/**
 * 过滤第三方库与 JDK 抛出的英文异常文本，只保留自己写的中文提示或 HTTP 状态。
 * 无法识别时返回 null，由调用方给出统一原因。
 */
fun readableErrorReason(message: String?): UpdateErrorReason? {
    val trimmed = message?.trim().orEmpty()
    if (trimmed.isEmpty()) {
        return null
    }
    if (trimmed.any { it.code in 0x4E00..0x9FFF }) {
        return UpdateErrorReason.Passthrough(trimmed)
    }
    return HTTP_STATUS_DETAIL.find(trimmed)
        ?.groupValues?.get(1)?.toIntOrNull()
        ?.let { UpdateErrorReason.HttpStatus(it) }
}

/** 把更新链路上抛出的异常转成可读原因 */
fun describeUpdateError(error: Throwable): UpdateErrorReason {
    (error as? UpdateException)?.let { return it.reason }
    networkErrorReason(error)?.let { return it }
    return readableErrorReason(error.message) ?: UpdateErrorReason.Unknown
}

/** 把下载环节的失败原因转成用户可读的失败提示 */
fun downloadFailureStatus(reason: DownloadFailureReason): UpdateStatusReason = when (reason) {
    is DownloadFailureReason.Thrown -> {
        val detail = describeUpdateError(reason.error)
        if (detail == UpdateErrorReason.Unknown) {
            UpdateStatusReason.DownloadRetry
        } else {
            UpdateStatusReason.DownloadDetail(detail)
        }
    }
    DownloadFailureReason.NoSource -> UpdateStatusReason.DownloadDetail(UpdateErrorReason.NoSource)
}

private const val JSON_EXCEPTION_CLASS_NAME = "org.json.JSONException"

private fun networkErrorReason(error: Throwable): UpdateErrorReason? = when {
    error is UnknownHostException -> UpdateErrorReason.UnknownHost
    error is SocketTimeoutException -> UpdateErrorReason.Timeout
    error is ConnectException -> UpdateErrorReason.ConnectFailed
    error is NoRouteToHostException || error is PortUnreachableException -> UpdateErrorReason.Unreachable
    error is SSLException -> UpdateErrorReason.TlsFailed
    error.javaClass.name == JSON_EXCEPTION_CLASS_NAME -> UpdateErrorReason.MalformedManifest
    error is IOException -> UpdateErrorReason.NetworkFailed
    else -> null
}

/** 更新清单里的版本字段是否足以判断新旧，不足时返回可读的原因。 */
fun updateManifestVersionProblem(versionCode: Int, versionName: String): UpdateStatusReason? = when {
    versionCode <= 0 -> UpdateStatusReason.ManifestVersionCodeMissing
    versionName.isBlank() -> UpdateStatusReason.ManifestVersionNameMissing
    else -> null
}

/** 把错误原因渲染成用户可读的中文/英文描述。 */
fun Context.updateErrorText(reason: UpdateErrorReason): String = when (reason) {
    UpdateErrorReason.UnknownHost -> getString(R.string.update_error_unknown_host)
    UpdateErrorReason.Timeout -> getString(R.string.update_error_timeout)
    UpdateErrorReason.ConnectFailed -> getString(R.string.update_error_connect_failed)
    UpdateErrorReason.Unreachable -> getString(R.string.update_error_unreachable)
    UpdateErrorReason.TlsFailed -> getString(R.string.update_error_tls)
    UpdateErrorReason.MalformedManifest -> getString(R.string.update_error_malformed_manifest)
    UpdateErrorReason.NetworkFailed -> getString(R.string.update_error_network)
    UpdateErrorReason.ChecksumFailed -> getString(R.string.update_error_checksum_failed)
    UpdateErrorReason.NoSource -> getString(R.string.update_error_no_source)
    UpdateErrorReason.Unknown -> getString(R.string.update_error_unknown)
    is UpdateErrorReason.HttpStatus -> getString(R.string.update_error_http_status, reason.statusCode)
    is UpdateErrorReason.Passthrough -> reason.text
    is UpdateErrorReason.ManifestDownloadFailed ->
        getString(R.string.update_error_manifest_download_failed, updateErrorText(reason.detail))
    is UpdateErrorReason.ProbeFailed ->
        getString(R.string.update_error_probe_failed, updateErrorText(reason.detail))
}

/** 把更新状态原因渲染成用户可读的中文/英文描述。 */
fun Context.updateStatusText(reason: UpdateStatusReason): String = when (reason) {
    is UpdateStatusReason.SourceHttpError ->
        getString(R.string.update_check_source_http_error, reason.sourceName, reason.statusCode)
    is UpdateStatusReason.SourceUnusableBody ->
        getString(R.string.update_check_source_unusable_body, reason.sourceName)
    UpdateStatusReason.SourceNoneAvailable -> getString(R.string.update_check_source_none)
    is UpdateStatusReason.SourceUnreachable ->
        getString(R.string.update_check_source_unreachable, updateErrorText(reason.detail))
    UpdateStatusReason.CheckRetry -> getString(R.string.update_check_retry)
    is UpdateStatusReason.CheckError ->
        getString(R.string.update_check_error, updateErrorText(reason.detail))
    UpdateStatusReason.ManifestVersionCodeMissing -> getString(R.string.update_manifest_no_version_code)
    UpdateStatusReason.ManifestVersionNameMissing -> getString(R.string.update_manifest_no_version_name)
    UpdateStatusReason.AssetNoPackage -> getString(R.string.update_asset_no_package)
    is UpdateStatusReason.AssetNoCompatibleAbi -> {
        val available = reason.availableAbis
            .joinToString(getString(R.string.update_asset_available_separator))
            .ifBlank { getString(R.string.update_asset_available_none) }
        val device = reason.deviceAbi
        if (device == null) {
            getString(R.string.update_asset_no_compatible_abi, available)
        } else {
            getString(R.string.update_asset_no_compatible_abi_with_device, device, available)
        }
    }
    UpdateStatusReason.AssetNoMatch -> getString(R.string.update_asset_no_match)
    is UpdateStatusReason.DownloadDetail ->
        getString(R.string.update_download_detail, updateErrorText(reason.detail))
    UpdateStatusReason.DownloadRetry -> getString(R.string.update_download_retry)
}

/**
 * 更新面板当前展示的状态。
 * 只记录种类与参数，文字在界面层按当前语言渲染，切换应用内语言后才会跟着变。
 */
sealed interface UpdatePanelStatus {
    data object Idle : UpdatePanelStatus

    data object Checking : UpdatePanelStatus

    data object NoRelease : UpdatePanelStatus

    data object ManifestMissing : UpdatePanelStatus

    data object UpToDate : UpdatePanelStatus

    data class Available(val versionName: String) : UpdatePanelStatus

    data class Rollback(val versionName: String) : UpdatePanelStatus

    data class Ignored(val versionName: String) : UpdatePanelStatus

    data class IgnoredManual(val versionName: String) : UpdatePanelStatus

    data class Downloading(val fileName: String) : UpdatePanelStatus

    data class Downloaded(val sourceName: String) : UpdatePanelStatus

    data class Failed(val reason: UpdateStatusReason) : UpdatePanelStatus
}
