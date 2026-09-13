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
        // api.github.com 与文件下载分开：多数镜像不代理 API，或把上游错误改写成自己的 200/404 页面，
        // 混进 API 候选会污染“没有发布版本”的判定
        val host = runCatching { URI(url).host }.getOrNull()
        return if (host.equals("api.github.com", ignoreCase = true)) {
            apiCapableProxyCandidates(url)
        } else {
            commonGithubProxyCandidates(url)
        }
    }

    /** 实测能透传 api.github.com 真实状态码的镜像；down.npee.cn 出错时把错误包成 200 文本，靠正文校验兜住。 */
    private fun apiCapableProxyCandidates(url: String): List<DownloadCandidate> {
        return listOf(
            DownloadCandidate("gh-proxy.com", "https://gh-proxy.com/$url"),
            DownloadCandidate("edgeone.gh-proxy.com", "https://edgeone.gh-proxy.com/$url"),
            DownloadCandidate("hk.gh-proxy.com", "https://hk.gh-proxy.com/$url"),
            DownloadCandidate("down.npee.cn", "https://down.npee.cn/?$url"),
            DownloadCandidate("cors.isteed.cc", "https://cors.isteed.cc/${stripScheme(url)}"),
            DownloadCandidate("GitHub 源站", url),
        )
    }

    private fun githubRawCandidates(request: DownloadRequest): List<DownloadCandidate> {
        val raw = parseRawGithubUrl(request.url)
        val repoFile = raw ?: RepoFile(
            repository = request.repository.orEmpty(),
            ref = request.ref.orEmpty(),
            path = request.path.orEmpty(),
        ).takeIf { it.isComplete() }
        val baseUrl = repoFile?.toRawUrl() ?: request.url
        return repoFile.orEmptyRepoFileCandidates() + commonGithubProxyCandidates(baseUrl)
    }

    private fun githubRepoFileCandidates(request: DownloadRequest): List<DownloadCandidate> {
        val repoFile = RepoFile(
            repository = request.repository.orEmpty(),
            ref = request.ref.orEmpty(),
            path = request.path.orEmpty(),
        ).takeIf { it.isComplete() } ?: parseRawGithubUrl(request.url)
        val baseUrl = repoFile?.toRawUrl() ?: request.url
        return repoFile.orEmptyRepoFileCandidates() + commonGithubProxyCandidates(baseUrl)
    }

    private fun commonGithubProxyCandidates(url: String): List<DownloadCandidate> {
        return listOf(
            DownloadCandidate("ghfast.top", "https://ghfast.top/$url"),
            DownloadCandidate("edgeone.gh-proxy.com", "https://edgeone.gh-proxy.com/$url"),
            DownloadCandidate("gh-proxy.com", "https://gh-proxy.com/$url"),
            DownloadCandidate("hk.gh-proxy.com", "https://hk.gh-proxy.com/$url"),
            DownloadCandidate("gh.llkk.cc", "https://gh.llkk.cc/$url"),
            DownloadCandidate("ghproxy.net", "https://ghproxy.net/$url"),
            DownloadCandidate("down.npee.cn", "https://down.npee.cn/?$url"),
            DownloadCandidate("cors.isteed.cc", "https://cors.isteed.cc/${stripScheme(url)}"),
            DownloadCandidate("GitHub 源站", url),
        )
    }

    private fun RepoFile?.orEmptyRepoFileCandidates(): List<DownloadCandidate> {
        val repoFile = this ?: return emptyList()
        return listOf(
            DownloadCandidate("jsDelivr testingcf", "https://testingcf.jsdelivr.net/gh/${repoFile.repository}@${repoFile.ref}/${repoFile.path}"),
            DownloadCandidate("jsDelivr CDN", "https://cdn.jsdelivr.net/gh/${repoFile.repository}@${repoFile.ref}/${repoFile.path}"),
            DownloadCandidate("jsDelivr Fastly", "https://fastly.jsdelivr.net/gh/${repoFile.repository}@${repoFile.ref}/${repoFile.path}"),
            DownloadCandidate("jsDelivr gcore", "https://gcore.jsdelivr.net/gh/${repoFile.repository}@${repoFile.ref}/${repoFile.path}"),
            DownloadCandidate("xget.xi-xu.me", "https://xget.xi-xu.me/gh/${repoFile.repository}/raw/${repoFile.ref}/${repoFile.path}"),
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
