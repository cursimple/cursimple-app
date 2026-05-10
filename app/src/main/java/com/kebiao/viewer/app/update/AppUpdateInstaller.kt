package com.kebiao.viewer.app.update

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.core.content.FileProvider
import java.io.File

object AppUpdateInstaller {
    fun openInstall(context: Context, apkFile: File) {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            apkFile,
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { context.startActivity(intent) }
            .onFailure { error ->
                val message = if (error is ActivityNotFoundException) {
                    "没有可安装 APK 的应用"
                } else {
                    "无法打开安装界面：${error.message}"
                }
                Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
            }
    }
}
