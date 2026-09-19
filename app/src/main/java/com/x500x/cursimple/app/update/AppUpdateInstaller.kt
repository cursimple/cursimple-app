package com.x500x.cursimple.app.update

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.core.content.FileProvider
import java.io.File
import com.x500x.cursimple.R

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
                    context.getString(R.string.update_install_no_handler)
                } else {
                    context.getString(R.string.update_install_open_failed, error.message.orEmpty())
                }
                Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
            }
    }
}
