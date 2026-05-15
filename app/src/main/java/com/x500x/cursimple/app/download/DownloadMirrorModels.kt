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

sealed interface MirrorDownloadResult<out T> {
    data class Success<T>(
        val value: T,
        val candidate: DownloadCandidate,
        val failures: List<DownloadFailure> = emptyList(),
    ) : MirrorDownloadResult<T>

    data class Failure(
        val message: String,
        val failures: List<DownloadFailure>,
    ) : MirrorDownloadResult<Nothing>
}
