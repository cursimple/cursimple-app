package com.x500x.cursimple.app.download

import java.net.URI

class DownloadMirrorPool {
    fun candidates(request: DownloadRequest): List<DownloadCandidate> {
        return when (request.purpose) {
            DownloadPurpose.LocalFile -> listOf(DownloadCandidate("本地文件", request.url))
            DownloadPurpose.DirectUrl -> directCandidates(request.url)
            DownloadPurpose.GithubRelease -> githubReleaseCandidates(request.url)
            DownloadPurpose.GithubRaw -> githubRawCandidates(request)
            DownloadPurpose.GithubRepoFile -> githubRepoFileCandidates(request)
        }.distinctBy { it.url }
    }

    private fun directCandidates(url: String): List<DownloadCandidate> {
        return listOf(DownloadCandidate("源站", url))
    }

    private fun githubReleaseCandidates(url: String): List<DownloadCandidate> {
        return commonGithubProxyCandidates(url)
    }

    private fun githubRawCandidates(request: DownloadRequest): List<DownloadCandidate> {
        val raw = parseRawGithubUrl(request.url)
        val repoFile = raw ?: RepoFile(
            repository = request.repository.orEmpty(),
            ref = request.ref.orEmpty(),
            path = request.path.orEmpty(),
        ).takeIf { it.isComplete() }
        return commonGithubProxyCandidates(request.url) +
            rawProxyCandidates(request.url) +
            repoFile.orEmptyRepoFileCandidates()
    }

    private fun githubRepoFileCandidates(request: DownloadRequest): List<DownloadCandidate> {
        val repoFile = RepoFile(
            repository = request.repository.orEmpty(),
            ref = request.ref.orEmpty(),
            path = request.path.orEmpty(),
        ).takeIf { it.isComplete() } ?: parseRawGithubUrl(request.url)
        val baseUrl = repoFile?.toRawUrl() ?: request.url
        return commonGithubProxyCandidates(baseUrl) +
            rawProxyCandidates(baseUrl) +
            repoFile.orEmptyRepoFileCandidates()
    }

    private fun commonGithubProxyCandidates(url: String): List<DownloadCandidate> {
        return listOf(
            DownloadCandidate("GitHub 源站", url),
            DownloadCandidate("ghfast.top", "https://ghfast.top/$url"),
            DownloadCandidate("gh-proxy.com", "https://gh-proxy.com/$url"),
            DownloadCandidate("ghproxy.net", "https://ghproxy.net/$url"),
            DownloadCandidate("down.npee.cn", "https://down.npee.cn/?$url"),
            DownloadCandidate("cors.isteed.cc", "https://cors.isteed.cc/${stripScheme(url)}"),
        )
    }

    private fun rawProxyCandidates(url: String): List<DownloadCandidate> {
        return listOf(
            DownloadCandidate("raw.ihtw.moe", "https://raw.ihtw.moe/${stripScheme(url)}"),
        )
    }

    private fun RepoFile?.orEmptyRepoFileCandidates(): List<DownloadCandidate> {
        val repoFile = this ?: return emptyList()
        return listOf(
            DownloadCandidate("xget.xi-xu.me", "https://xget.xi-xu.me/gh/${repoFile.repository}/${repoFile.ref}/${repoFile.path}"),
            DownloadCandidate("jsDelivr CDN", "https://cdn.jsdelivr.net/gh/${repoFile.repository}@${repoFile.ref}/${repoFile.path}"),
            DownloadCandidate("jsDelivr Fastly", "https://fastly.jsdelivr.net/gh/${repoFile.repository}@${repoFile.ref}/${repoFile.path}"),
        )
    }

    private fun parseRawGithubUrl(url: String): RepoFile? {
        val uri = runCatching { URI(url) }.getOrNull() ?: return null
        if (!uri.host.equals("raw.githubusercontent.com", ignoreCase = true)) {
            return null
        }
        val segments = uri.path.trim('/').split('/').filter(String::isNotBlank)
        if (segments.size < 4) {
            return null
        }
        return RepoFile(
            repository = "${segments[0]}/${segments[1]}",
            ref = segments[2],
            path = segments.drop(3).joinToString("/"),
        )
    }

    private fun stripScheme(url: String): String {
        return url.removePrefix("https://").removePrefix("http://")
    }

    private data class RepoFile(
        val repository: String,
        val ref: String,
        val path: String,
    ) {
        fun isComplete(): Boolean = repository.count { it == '/' } == 1 && ref.isNotBlank() && path.isNotBlank()

        fun toRawUrl(): String = "https://raw.githubusercontent.com/$repository/$ref/$path"
    }
}
