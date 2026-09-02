package com.x500x.cursimple.app.download

import android.content.Context
import com.x500x.cursimple.R

/** 用当前语言的资源填充下载器兜底文案。 */
fun Context.mirrorDownloaderLabels(): MirrorDownloaderLabels = MirrorDownloaderLabels(
    localFileSource = getString(R.string.download_source_local_file),
    verifyFailed = getString(R.string.download_verify_failed),
    readFailed = getString(R.string.download_read_failed),
    localFileVerifyFailed = getString(R.string.download_local_file_verify_failed),
    localFileReadFailed = getString(R.string.download_local_file_read_failed),
    probeFailed = getString(R.string.download_probe_failed),
    downloadFailed = getString(R.string.download_failed),
    noSource = getString(R.string.download_no_source),
)
