package com.x500x.cursimple.app.update

import java.io.File

data class AppUpdateInfo(
    val versionCode: Int,
    val versionName: String,
    val tagName: String,
    val releaseUrl: String,
    val releaseNotes: String,
    val asset: AppUpdateAsset,
    val candidates: List<AppUpdateDownloadCandidate>,
)

data class AppUpdateAsset(
    val abi: String,
    val fileName: String,
    val sha256: String,
    val downloadUrl: String,
)

data class AppUpdateDownloadCandidate(
    val sourceName: String,
    val url: String,
    val latencyMillis: Long? = null,
)

sealed interface AppUpdateCheckResult {
    data object NoRelease : AppUpdateCheckResult
    data object ManifestMissing : AppUpdateCheckResult
    data object UpToDate : AppUpdateCheckResult
    data class Available(val info: AppUpdateInfo) : AppUpdateCheckResult

    /** 当前装的是测试版，而线上正式版版本号更低，可以回退过去。 */
    data class Rollback(val info: AppUpdateInfo) : AppUpdateCheckResult
    data class Failure(val reason: UpdateStatusReason) : AppUpdateCheckResult
}

sealed interface AppUpdateDownloadResult {
    data class Success(val file: File, val sourceName: String) : AppUpdateDownloadResult
    data class Failure(val reason: UpdateStatusReason) : AppUpdateDownloadResult
}
