package com.x500x.cursimple.app.download

enum class DownloadPurpose {
    GithubRelease,
    GithubRaw,
    GithubRepoFile,
    DirectUrl,
    LocalFile,
}

data class DownloadRequest(
    val purpose: DownloadPurpose,
    val url: String,
    val repository: String? = null,
    val ref: String? = null,
    val path: String? = null,
)

data class DownloadCandidate(
    val sourceName: String,
    val url: String,
)

data class MeasuredDownloadCandidate(
    val candidate: DownloadCandidate,
    val latencyMillis: Long,
)

data class DownloadFailure(
    val sourceName: String,
    val message: String,
)

/** Typed download failures for localized presentation. */
sealed interface DownloadFailureReason {
    data class Thrown(val error: Throwable) : DownloadFailureReason

    data object NoSource : DownloadFailureReason
}

data class MirrorDownloaderLabels(
    val localFileSource: String,
    val verifyFailed: String,
    val readFailed: String,
    val localFileVerifyFailed: String,
    val localFileReadFailed: String,
    val probeFailed: String,
    val downloadFailed: String,
    val noSource: String,
)

sealed interface MirrorDownloadResult<out T> {
    data class Success<T>(
        val value: T,
        val candidate: DownloadCandidate,
        val failures: List<DownloadFailure> = emptyList(),
    ) : MirrorDownloadResult<T>

    data class Failure(
        val message: String,
        val reason: DownloadFailureReason,
        val failures: List<DownloadFailure>,
    ) : MirrorDownloadResult<Nothing>
}
