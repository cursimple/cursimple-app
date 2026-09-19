package com.x500x.cursimple.app.download

import java.net.URI


/**
 * 三个非主机名的下载源标识。
 *
 * sourceName 同时是界面上显示的名字和镜像偏好里持久化的键，直接把它翻译掉会让
 * 换了语言之后对不上以前记下的偏好。所以这里固定用与语言无关的标识，
 * 显示时再由界面层换成当前语言（见 downloadSourceLabel）。
 */
object DownloadSourceIds {
    const val LOCAL_FILE = "local-file"
    const val ORIGIN = "origin"
    const val GITHUB_ORIGIN = "github-origin"
}

class DownloadMirrorPool {
    fun candidates(request: DownloadRequest): List<DownloadCandidate> {
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
            DownloadCandidate("github.chenc.dev", "https://github.chenc.dev/$url"),
            DownloadCandidate("hk.gh-proxy.com", "https://hk.gh-proxy.com/$url"),
            DownloadCandidate("hub.ilatency.com", "https://hub.ilatency.com/$url"),
            DownloadCandidate("github.yuansi.xyz", "https://github.yuansi.xyz/$url"),
            DownloadCandidate("down.npee.cn", "https://down.npee.cn/?$url"),
            DownloadCandidate("cors.isteed.cc", "https://cors.isteed.cc/${stripScheme(url)}"),
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
        // 注意：monlor / imciel / fastgit / llkk 等对 API 会返回自家的 404 页面，
        // 只能用于文件下载，加进 API 候选会污染“没有发布版本”的判定
        return listOf(
            DownloadCandidate("ghfast.top", "https://ghfast.top/$url"),
            DownloadCandidate("edgeone.gh-proxy.com", "https://edgeone.gh-proxy.com/$url"),
            DownloadCandidate("gh-proxy.com", "https://gh-proxy.com/$url"),
            DownloadCandidate("gh.monlor.com", "https://gh.monlor.com/$url"),
            DownloadCandidate("ghproxy.imciel.com", "https://ghproxy.imciel.com/$url"),
            DownloadCandidate("gh.jasonzeng.dev", "https://gh.jasonzeng.dev/$url"),
            DownloadCandidate("hk.gh-proxy.com", "https://hk.gh-proxy.com/$url"),
            DownloadCandidate("fastgit.cc", "https://fastgit.cc/$url"),
            DownloadCandidate("gh.llkk.cc", "https://gh.llkk.cc/$url"),
            DownloadCandidate("ghp.keleyaa.com", "https://ghp.keleyaa.com/$url"),
            DownloadCandidate("ghproxy.net", "https://ghproxy.net/$url"),
            DownloadCandidate("down.npee.cn", "https://down.npee.cn/?$url"),
            DownloadCandidate("cors.isteed.cc", "https://cors.isteed.cc/${stripScheme(url)}"),
            DownloadCandidate(DownloadSourceIds.GITHUB_ORIGIN, url),
        )
    }

    private fun RepoFile?.orEmptyRepoFileCandidates(): List<DownloadCandidate> {
        val repoFile = this ?: return emptyList()
        return listOf(
            DownloadCandidate("jsdmirror CDN", "https://cdn.jsdmirror.com/gh/${repoFile.repository}@${repoFile.ref}/${repoFile.path}"),
            DownloadCandidate("jsDelivr testingcf", "https://testingcf.jsdelivr.net/gh/${repoFile.repository}@${repoFile.ref}/${repoFile.path}"),
            DownloadCandidate("jsdelivr.net.cn", "https://cdn.jsdelivr.net.cn/gh/${repoFile.repository}@${repoFile.ref}/${repoFile.path}"),
            DownloadCandidate("jsdmirror.cn", "https://cdn.jsdmirror.cn/gh/${repoFile.repository}@${repoFile.ref}/${repoFile.path}"),
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
        // 查询串一并带进 path：调用方用它击穿 CDN 缓存，
        // 丢掉的话 jsDelivr 这类按路径改写的候选就又读回缓存里的旧文件了
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
