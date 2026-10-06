package com.x500x.cursimple.app.download

import java.net.URI

/** Persist language-independent source IDs; localize labels only when rendering. */
object DownloadSourceIds {
    const val LOCAL_FILE = "local-file"
    const val ORIGIN = "origin"
    const val GITHUB_ORIGIN = "github-origin"
}

open class DownloadMirrorPool {
    open fun candidates(request: DownloadRequest): List<DownloadCandidate> {
        return when (request.purpose) {
            DownloadPurpose.LocalFile -> listOf(DownloadCandidate(DownloadSourceIds.LOCAL_FILE, request.url))
            DownloadPurpose.DirectUrl -> directCandidates(request.url)
            DownloadPurpose.GithubRelease -> githubReleaseCandidates(request.url)
            DownloadPurpose.GithubRaw -> githubRawCandidates(request)
            DownloadPurpose.GithubRepoFile -> githubRepoFileCandidates(request)
        }.distinctBy { it.url }
    }

    private fun directCandidates(url: String): List<DownloadCandidate> {
        return listOf(DownloadCandidate(DownloadSourceIds.ORIGIN, url))
    }

    private fun githubReleaseCandidates(url: String): List<DownloadCandidate> {
        // Keep GitHub API candidates separate because asset proxies may rewrite error responses.
        val host = runCatching { URI(url).host }.getOrNull()
        return if (host.equals("api.github.com", ignoreCase = true)) {
            apiCapableProxyCandidates(url)
        } else {
            commonGithubProxyCandidates(url)
        }
    }

    /**
     * Fallback API proxies must preserve GitHub status codes; repository update feeds are
     * preferred.
     */
    private fun apiCapableProxyCandidates(url: String): List<DownloadCandidate> {
        return listOf(
            DownloadCandidate("hk.gh-proxy.com", "https://hk.gh-proxy.com/$url"),
            DownloadCandidate("github.chenc.dev", "https://github.chenc.dev/$url"),
            DownloadCandidate("gh-proxy.org", "https://gh-proxy.org/$url"),
            DownloadCandidate("gh-proxy.com", "https://gh-proxy.com/$url"),
            DownloadCandidate("hub.ilatency.com", "https://hub.ilatency.com/$url"),
            DownloadCandidate("cdn.gh-proxy.com", "https://cdn.gh-proxy.com/$url"),
            DownloadCandidate("edgeone.gh-proxy.com", "https://edgeone.gh-proxy.com/$url"),
            DownloadCandidate(DownloadSourceIds.GITHUB_ORIGIN, url),
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
        // Initial order comes from integrity and throughput checks; runtime measurements reorder candidates and downgrade failures. Include the origin early for networks where it is faster.
        return listOf(
            DownloadCandidate("gh.monlor.com", "https://gh.monlor.com/$url"),
            DownloadCandidate("ghproxy.imciel.com", "https://ghproxy.imciel.com/$url"),
            DownloadCandidate("gh.llkk.cc", "https://gh.llkk.cc/$url"),
            DownloadCandidate("gh.wglee.org", "https://gh.wglee.org/$url"),
            DownloadCandidate("ghfast.top", "https://ghfast.top/$url"),
            DownloadCandidate(DownloadSourceIds.GITHUB_ORIGIN, url),
            DownloadCandidate("gh.noki.icu", "https://gh.noki.icu/$url"),
            DownloadCandidate("gh-proxy.org", "https://gh-proxy.org/$url"),
            DownloadCandidate("gh.halonice.com", "https://gh.halonice.com/$url"),
            DownloadCandidate("cors.isteed.cc", "https://cors.isteed.cc/${stripScheme(url)}"),
            DownloadCandidate("fastgit.cc", "https://fastgit.cc/$url"),
            DownloadCandidate("ghproxy.vip", "https://ghproxy.vip/$url"),
            DownloadCandidate("gh.dpik.top", "https://gh.dpik.top/$url"),
            DownloadCandidate("gh-proxy.com", "https://gh-proxy.com/$url"),
            DownloadCandidate("gh.sixyin.com", "https://gh.sixyin.com/$url"),
            DownloadCandidate("ghp.keleyaa.com", "https://ghp.keleyaa.com/$url"),
            DownloadCandidate("gh.qninq.cn", "https://gh.qninq.cn/$url"),
            DownloadCandidate("ghfile.geekertao.top", "https://ghfile.geekertao.top/$url"),
            DownloadCandidate("cdn.gh-proxy.com", "https://cdn.gh-proxy.com/$url"),
            DownloadCandidate("edgeone.gh-proxy.com", "https://edgeone.gh-proxy.com/$url"),
            DownloadCandidate("hk.gh-proxy.com", "https://hk.gh-proxy.com/$url"),
            DownloadCandidate("gh.nxnow.top", "https://gh.nxnow.top/$url"),
            DownloadCandidate("ghproxy.net", "https://ghproxy.net/$url"),
            DownloadCandidate("down.npee.cn", "https://down.npee.cn/?$url"),
        )
    }

    private fun RepoFile?.orEmptyRepoFileCandidates(): List<DownloadCandidate> {
        val repoFile = this ?: return emptyList()
        return listOf(
            DownloadCandidate("jsdmirror CDN", "https://cdn.jsdmirror.com/gh/${repoFile.repository}@${repoFile.ref}/${repoFile.path}"),
            DownloadCandidate("jsd.onmicrosoft.cn", "https://jsd.onmicrosoft.cn/gh/${repoFile.repository}@${repoFile.ref}/${repoFile.path}"),
            DownloadCandidate("jsdelivr.net.cn", "https://cdn.jsdelivr.net.cn/gh/${repoFile.repository}@${repoFile.ref}/${repoFile.path}"),
            DownloadCandidate("jsdmirror.cn", "https://cdn.jsdmirror.cn/gh/${repoFile.repository}@${repoFile.ref}/${repoFile.path}"),
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
        // Preserve query strings when rewriting paths so cache-busting survives CDN URLs.
        val query = uri.query?.takeIf { it.isNotBlank() }?.let { "?$it" }.orEmpty()
        return RepoFile(
            repository = "${segments[0]}/${segments[1]}",
            ref = segments[2],
            path = segments.drop(3).joinToString("/") + query,
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
