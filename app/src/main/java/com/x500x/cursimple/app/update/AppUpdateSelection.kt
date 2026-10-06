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
import com.x500x.cursimple.app.download.DownloadSourceIds

/** Typed update failures; the UI supplies localized text. */
sealed interface UpdateErrorReason {
    data object UnknownHost : UpdateErrorReason

    data object Timeout : UpdateErrorReason

    data object ConnectFailed : UpdateErrorReason

    data object Unreachable : UpdateErrorReason

    /** Secure connection setup failed. */
    data object TlsFailed : UpdateErrorReason

    data object MalformedManifest : UpdateErrorReason

    /** Other network failure. */
    data object NetworkFailed : UpdateErrorReason

    data object ChecksumFailed : UpdateErrorReason

    data object NoSource : UpdateErrorReason

    data object Unknown : UpdateErrorReason

    data class HttpStatus(val statusCode: Int) : UpdateErrorReason

    data class Passthrough(val text: String) : UpdateErrorReason

    /** Update-manifest download failure with [detail]. */
    data class ManifestDownloadFailed(val detail: UpdateErrorReason) : UpdateErrorReason

    /** Download probe failure with [detail]. */
    data class ProbeFailed(val detail: UpdateErrorReason) : UpdateErrorReason
}

class UpdateException(val reason: UpdateErrorReason) : Exception()

sealed interface UpdateStatusReason {
    data class SourceHttpError(val sourceName: String, val statusCode: Int) : UpdateStatusReason

    data class SourceUnusableBody(val sourceName: String) : UpdateStatusReason

    data object SourceNoneAvailable : UpdateStatusReason

    data class SourceUnreachable(val detail: UpdateErrorReason) : UpdateStatusReason

    /** Check failure without a more specific cause. */
    data object CheckRetry : UpdateStatusReason

    data class CheckError(val detail: UpdateErrorReason) : UpdateStatusReason

    data object ManifestVersionCodeMissing : UpdateStatusReason

    data object ManifestVersionNameMissing : UpdateStatusReason

    data object AssetNoPackage : UpdateStatusReason

    data class AssetNoCompatibleAbi(
        val deviceAbi: String?,
        val availableAbis: List<String>,
    ) : UpdateStatusReason

    data object AssetNoMatch : UpdateStatusReason

    /** Download failure with [detail]. */
    data class DownloadDetail(val detail: UpdateErrorReason) : UpdateStatusReason

    /** Download failure without a more specific cause. */
    data object DownloadRetry : UpdateStatusReason
}

data class UpdateSourceResponse(
    val sourceName: String,
    val statusCode: Int,
    val body: String,
    val latencyMillis: Long,
)

/** HTTP response or transport failure from one update source. */
data class UpdateSourceAttempt(
    val sourceName: String,
    val response: UpdateSourceResponse? = null,
    val errorReason: UpdateErrorReason? = null,
)

sealed interface UpdateSourceSelection {
    data class Success(val response: UpdateSourceResponse) : UpdateSourceSelection

    data object NotFound : UpdateSourceSelection

    data class HttpError(val sourceName: String, val statusCode: Int) : UpdateSourceSelection

    data class UnusableBody(val sourceName: String) : UpdateSourceSelection

    data class Unreachable(val attempts: List<UpdateSourceAttempt>) : UpdateSourceSelection
}

object UpdateSourceSelector {
    /** Origin 404 proves absence; proxy 404 does not. */
    const val AUTHORITATIVE_SOURCE_NAME = DownloadSourceIds.GITHUB_ORIGIN

    private const val HTTP_NOT_FOUND = 404

    /**
     * [isUsableBody] rejects malformed 2xx proxy responses before they can mask authoritative
     * origin status.
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

/** Success and confirmed absence have no failure reason. */
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

    data object NoAsset : UpdateAssetSelection

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

/** Asset hits have no failure reason. */
fun updateAssetFailureMessage(selection: UpdateAssetSelection): UpdateStatusReason? = when (selection) {
    is UpdateAssetSelection.Matched -> null
    UpdateAssetSelection.NoAsset -> UpdateStatusReason.AssetNoPackage
    is UpdateAssetSelection.NoCompatibleAbi -> UpdateStatusReason.AssetNoCompatibleAbi(
        deviceAbi = selection.deviceAbis.firstOrNull()?.takeIf { it.isNotBlank() },
        availableAbis = selection.availableAbis,
    )
}

private val HTTP_STATUS_DETAIL = Regex("""HTTP (\d{3})""")

/** Keep recognized app messages and HTTP status; omit raw library exception text. */
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

fun describeUpdateError(error: Throwable): UpdateErrorReason {
    (error as? UpdateException)?.let { return it.reason }
    networkErrorReason(error)?.let { return it }
    return readableErrorReason(error.message) ?: UpdateErrorReason.Unknown
}

/** Localize typed download failures for presentation. */
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

fun updateManifestVersionProblem(versionCode: Int, versionName: String): UpdateStatusReason? = when {
    versionCode <= 0 -> UpdateStatusReason.ManifestVersionCodeMissing
    versionName.isBlank() -> UpdateStatusReason.ManifestVersionNameMissing
    else -> null
}

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

/** Store status types and arguments, resolving text with the current locale. */
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
