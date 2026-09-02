package com.x500x.cursimple.app.update

import com.x500x.cursimple.app.download.DownloadFailureReason
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

class AppUpdateSelectionTest {

    private fun response(
        sourceName: String,
        statusCode: Int,
        latencyMillis: Long,
        body: String = "",
    ) = UpdateSourceResponse(
        sourceName = sourceName,
        statusCode = statusCode,
        body = body,
        latencyMillis = latencyMillis,
    )

    private fun ok(sourceName: String, latencyMillis: Long, body: String = "{}") =
        UpdateSourceAttempt(sourceName, response(sourceName, 200, latencyMillis, body))

    private fun status(sourceName: String, statusCode: Int, latencyMillis: Long) =
        UpdateSourceAttempt(sourceName, response(sourceName, statusCode, latencyMillis))

    private fun failed(sourceName: String, reason: UpdateErrorReason) =
        UpdateSourceAttempt(sourceName, response = null, errorReason = reason)

    private val jsonBody: (String) -> Boolean = { it.trimStart().startsWith("{") }

    @Test
    fun `proxy answering 200 with a non json page does not mask the origin 404`() {
        val selection = UpdateSourceSelector.select(
            listOf(
                ok("down.npee.cn", latencyMillis = 20, body = "Request forbidden by administrative rules."),
                status("GitHub 源站", 404, latencyMillis = 300),
            ),
            jsonBody,
        )

        assertEquals(UpdateSourceSelection.NotFound, selection)
        assertNull(updateSourceFailureMessage(selection))
    }

    @Test
    fun `proxy answering 200 with a non json page does not mask another source http error`() {
        val selection = UpdateSourceSelector.select(
            listOf(
                ok("down.npee.cn", latencyMillis = 20, body = "<html>403</html>"),
                status("GitHub 源站", 503, latencyMillis = 300),
            ),
            jsonBody,
        )

        assertEquals(UpdateSourceSelection.HttpError("GitHub 源站", 503), selection)
    }

    @Test
    fun `every source answering 200 with an unusable body reports that instead of parsing it`() {
        val selection = UpdateSourceSelector.select(
            listOf(
                ok("down.npee.cn", latencyMillis = 200, body = "Request forbidden by administrative rules."),
                ok("ghfast.top", latencyMillis = 20, body = "Invalid input."),
            ),
            jsonBody,
        )

        assertEquals(UpdateSourceSelection.UnusableBody("ghfast.top"), selection)
        assertEquals(UpdateStatusReason.SourceUnusableBody("ghfast.top"), updateSourceFailureMessage(selection))
    }

    @Test
    fun `a usable json source still wins over a faster unusable one`() {
        val selection = UpdateSourceSelector.select(
            listOf(
                ok("down.npee.cn", latencyMillis = 5, body = "Request forbidden by administrative rules."),
                ok("GitHub 源站", latencyMillis = 300, body = "{\"tag_name\":\"v1\"}"),
            ),
            jsonBody,
        )

        assertEquals("GitHub 源站", (selection as UpdateSourceSelection.Success).response.sourceName)
    }

    @Test
    fun `all sources failing reports unreachable instead of no release`() {
        val selection = UpdateSourceSelector.select(
            listOf(
                failed("ghfast.top", UpdateErrorReason.UnknownHost),
                failed("GitHub 源站", UpdateErrorReason.Timeout),
            ),
        )

        assertTrue(selection is UpdateSourceSelection.Unreachable)
        assertEquals(
            UpdateStatusReason.SourceUnreachable(UpdateErrorReason.UnknownHost),
            updateSourceFailureMessage(selection),
        )
    }

    @Test
    fun `empty candidate list reports unreachable`() {
        val selection = UpdateSourceSelector.select(emptyList())

        assertEquals(UpdateSourceSelection.Unreachable(emptyList()), selection)
        assertEquals(UpdateStatusReason.SourceNoneAvailable, updateSourceFailureMessage(selection))
    }

    @Test
    fun `fast mirror 404 does not beat slow source 200`() {
        val selection = UpdateSourceSelector.select(
            listOf(
                status("ghfast.top", 404, 80),
                ok("GitHub 源站", 800, body = "release"),
            ),
        )

        assertTrue(selection is UpdateSourceSelection.Success)
        assertEquals("GitHub 源站", (selection as UpdateSourceSelection.Success).response.sourceName)
        assertEquals("release", selection.response.body)
        assertNull(updateSourceFailureMessage(selection))
    }

    @Test
    fun `fastest successful response wins among multiple 2xx`() {
        val selection = UpdateSourceSelector.select(
            listOf(
                ok("GitHub 源站", 900),
                ok("gh-proxy.com", 120),
                ok("ghproxy.net", 400),
                status("down.npee.cn", 404, 10),
            ),
        )

        assertEquals(
            "gh-proxy.com",
            (selection as UpdateSourceSelection.Success).response.sourceName,
        )
    }

    @Test
    fun `only 404 responses report no release`() {
        val selection = UpdateSourceSelector.select(
            listOf(
                status("ghfast.top", 404, 80),
                status("GitHub 源站", 404, 300),
            ),
        )

        assertEquals(UpdateSourceSelection.NotFound, selection)
        assertNull(updateSourceFailureMessage(selection))
    }

    @Test
    fun `source 404 is authoritative even when a mirror returns another error`() {
        val selection = UpdateSourceSelector.select(
            listOf(
                status("ghfast.top", 500, 40),
                status("GitHub 源站", 404, 600),
            ),
        )

        assertEquals(UpdateSourceSelection.NotFound, selection)
    }

    @Test
    fun `mirror 404 mixed with server errors reports failure not no release`() {
        val selection = UpdateSourceSelector.select(
            listOf(
                status("ghfast.top", 404, 40),
                status("gh-proxy.com", 502, 300),
                failed("GitHub 源站", UpdateErrorReason.Timeout),
            ),
        )

        assertEquals(UpdateSourceSelection.HttpError("gh-proxy.com", 502), selection)
        assertEquals(
            UpdateStatusReason.SourceHttpError("gh-proxy.com", 502),
            updateSourceFailureMessage(selection),
        )
    }

    @Test
    fun `source status is reported when the source itself errors`() {
        val selection = UpdateSourceSelector.select(
            listOf(
                status("ghfast.top", 502, 20),
                status("GitHub 源站", 403, 700),
            ),
        )

        assertEquals(UpdateSourceSelection.HttpError("GitHub 源站", 403), selection)
    }

    @Test
    fun `asset selection prefers exact abi in device priority order`() {
        val arm64 = asset("arm64-v8a")
        val armv7 = asset("armeabi-v7a")
        val selection = UpdateAssetSelector.select(
            assets = listOf(armv7, asset("universal"), arm64),
            deviceAbis = listOf("arm64-v8a", "armeabi-v7a"),
        )

        assertEquals(UpdateAssetSelection.Matched(arm64), selection)
        assertNull(updateAssetFailureMessage(selection))
    }

    @Test
    fun `asset selection falls back to universal`() {
        val universal = asset("universal")
        val selection = UpdateAssetSelector.select(
            assets = listOf(asset("x86_64"), universal),
            deviceAbis = listOf("arm64-v8a", "armeabi-v7a"),
        )

        assertEquals(UpdateAssetSelection.Matched(universal), selection)
    }

    @Test
    fun `asset selection reports failure instead of picking an incompatible abi`() {
        val selection = UpdateAssetSelector.select(
            assets = listOf(asset("x86"), asset("x86_64")),
            deviceAbis = listOf("arm64-v8a", "armeabi-v7a"),
        )

        assertEquals(
            UpdateAssetSelection.NoCompatibleAbi(
                deviceAbis = listOf("arm64-v8a", "armeabi-v7a"),
                availableAbis = listOf("x86", "x86_64"),
            ),
            selection,
        )
        assertEquals(
            UpdateStatusReason.AssetNoCompatibleAbi(
                deviceAbi = "arm64-v8a",
                availableAbis = listOf("x86", "x86_64"),
            ),
            updateAssetFailureMessage(selection),
        )
    }

    @Test
    fun `asset selection reports empty manifest separately`() {
        val selection = UpdateAssetSelector.select(assets = emptyList(), deviceAbis = listOf("arm64-v8a"))

        assertEquals(UpdateAssetSelection.NoAsset, selection)
        assertEquals(UpdateStatusReason.AssetNoPackage, updateAssetFailureMessage(selection))
    }

    @Test
    fun `asset abi matching ignores case`() {
        val matched = asset("ARM64-V8A")
        val selection = UpdateAssetSelector.select(
            assets = listOf(matched),
            deviceAbis = listOf("arm64-v8a"),
        )

        assertEquals(UpdateAssetSelection.Matched(matched), selection)
    }

    @Test
    fun `unknown device abi list still falls back to universal`() {
        val universal = asset("universal")
        val selection = UpdateAssetSelector.select(assets = listOf(universal), deviceAbis = emptyList())

        assertEquals(UpdateAssetSelection.Matched(universal), selection)
    }

    @Test
    fun `network errors are described in readable reasons`() {
        assertEquals(UpdateErrorReason.UnknownHost, describeUpdateError(UnknownHostException("api.github.com")))
        assertEquals(UpdateErrorReason.Timeout, describeUpdateError(SocketTimeoutException("connect timed out")))
        assertEquals(UpdateErrorReason.ConnectFailed, describeUpdateError(ConnectException("Connection refused")))
    }

    @Test
    fun `english exception text is not surfaced to the user`() {
        assertEquals(UpdateErrorReason.Unknown, describeUpdateError(IllegalStateException("Unexpected end of stream")))
        assertEquals(UpdateErrorReason.Unknown, describeUpdateError(IllegalStateException("")))
    }

    @Test
    fun `chinese messages and http status details pass through`() {
        assertEquals(
            UpdateErrorReason.Passthrough("无法下载更新清单（HTTP 404）"),
            describeUpdateError(IllegalStateException("无法下载更新清单（HTTP 404）")),
        )
        assertEquals(UpdateErrorReason.HttpStatus(503), readableErrorReason("HTTP 503"))
        assertNull(readableErrorReason("Unable to resolve host \"api.github.com\""))
        assertNull(readableErrorReason(null))
    }

    @Test
    fun `update exception carries its typed reason across boundaries`() {
        assertEquals(
            UpdateErrorReason.ChecksumFailed,
            describeUpdateError(UpdateException(UpdateErrorReason.ChecksumFailed)),
        )
    }

    @Test
    fun `download failure maps unreadable details to a retry hint`() {
        assertEquals(
            UpdateStatusReason.DownloadRetry,
            downloadFailureStatus(DownloadFailureReason.Thrown(IllegalStateException("Software caused connection abort"))),
        )
        assertEquals(
            UpdateStatusReason.DownloadDetail(UpdateErrorReason.ChecksumFailed),
            downloadFailureStatus(DownloadFailureReason.Thrown(UpdateException(UpdateErrorReason.ChecksumFailed))),
        )
        assertEquals(
            UpdateStatusReason.DownloadDetail(UpdateErrorReason.HttpStatus(404)),
            downloadFailureStatus(DownloadFailureReason.Thrown(IllegalStateException("HTTP 404"))),
        )
        assertEquals(
            UpdateStatusReason.DownloadDetail(UpdateErrorReason.NoSource),
            downloadFailureStatus(DownloadFailureReason.NoSource),
        )
    }

    private fun asset(abi: String) = AppUpdateAsset(
        abi = abi,
        fileName = "cursimple-$abi.apk",
        sha256 = "0".repeat(64),
        downloadUrl = "https://github.com/cursimple/cursimple-app/releases/download/v1/cursimple-$abi.apk",
    )

    @Test
    fun `a manifest without a version code cannot be called up to date`() {
        assertEquals(
            UpdateStatusReason.ManifestVersionCodeMissing,
            updateManifestVersionProblem(versionCode = -1, versionName = "1.2.0"),
        )
        assertEquals(
            UpdateStatusReason.ManifestVersionCodeMissing,
            updateManifestVersionProblem(versionCode = 0, versionName = "1.2.0"),
        )
    }

    @Test
    fun `a manifest without a version name is rejected`() {
        assertEquals(
            UpdateStatusReason.ManifestVersionNameMissing,
            updateManifestVersionProblem(versionCode = 42, versionName = ""),
        )
        assertEquals(
            UpdateStatusReason.ManifestVersionNameMissing,
            updateManifestVersionProblem(versionCode = 42, versionName = "   "),
        )
    }

    @Test
    fun `a complete manifest reports no problem`() {
        assertNull(updateManifestVersionProblem(versionCode = 42, versionName = "1.2.0"))
    }
}
