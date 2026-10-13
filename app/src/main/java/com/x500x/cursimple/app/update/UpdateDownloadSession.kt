package com.x500x.cursimple.app.update

import android.content.Context
import com.x500x.cursimple.app.download.SharedPrefsMirrorPreferenceStore
import com.x500x.cursimple.app.download.mirrorDownloaderLabels
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface UpdateDownloadState {
    data object Idle : UpdateDownloadState
    data class Downloading(val info: AppUpdateInfo, val progress: AppUpdateDownloadProgress) : UpdateDownloadState
    data class Done(val info: AppUpdateInfo, val file: java.io.File, val sourceName: String) : UpdateDownloadState
    data class Failed(val info: AppUpdateInfo, val reason: UpdateStatusReason) : UpdateDownloadState
}

/**
 * Runs one update download outside any screen, so closing the update dialog neither cancels it
 * nor sends the user back to checking.
 */
class UpdateDownloadSession(
    private val scope: CoroutineScope,
    private val download: suspend (AppUpdateInfo, (Long, Long) -> Unit) -> AppUpdateDownloadResult,
) {
    private val mutableState = MutableStateFlow<UpdateDownloadState>(UpdateDownloadState.Idle)
    val state: StateFlow<UpdateDownloadState> = mutableState.asStateFlow()
    private var job: Job? = null
    private var installClaimed = false

    /** Start downloading [info]; returns false when that version is already running or downloaded. */
    @Synchronized
    fun start(info: AppUpdateInfo): Boolean {
        when (val current = mutableState.value) {
            is UpdateDownloadState.Downloading -> if (current.info.versionCode == info.versionCode) return false
            is UpdateDownloadState.Done -> if (current.info.versionCode == info.versionCode && current.file.exists()) return false
            else -> Unit
        }
        job?.cancel()
        installClaimed = false
        mutableState.value = UpdateDownloadState.Downloading(info, AppUpdateDownloadProgress(0L, null))
        job = scope.launch {
            val result = download(info) { downloaded, total ->
                mutableState.value = UpdateDownloadState.Downloading(info, AppUpdateDownloadProgress(downloaded, total.takeIf { it > 0L }))
            }
            mutableState.value = when (result) {
                is AppUpdateDownloadResult.Success -> UpdateDownloadState.Done(info, result.file, result.sourceName)
                is AppUpdateDownloadResult.Failure -> UpdateDownloadState.Failed(info, result.reason)
            }
        }
        return true
    }

    /** True once per finished download, so two open panels never stack two installers. */
    @Synchronized
    fun claimInstall(): Boolean {
        if (installClaimed || mutableState.value !is UpdateDownloadState.Done) return false
        installClaimed = true
        return true
    }

    companion object {
        @Volatile private var shared: UpdateDownloadSession? = null

        fun get(context: Context): UpdateDownloadSession = shared ?: synchronized(this) {
            shared ?: run {
                val app = context.applicationContext
                val checker = AppUpdateChecker(
                    notesLanguageTag = announcementLocale(app),
                    downloaderLabels = app.mirrorDownloaderLabels(),
                    mirrorStore = SharedPrefsMirrorPreferenceStore(app),
                )
                UpdateDownloadSession(CoroutineScope(SupervisorJob() + Dispatchers.IO)) { info, onProgress ->
                    checker.download(app, info, onProgress)
                }.also { shared = it }
            }
        }
    }
}
