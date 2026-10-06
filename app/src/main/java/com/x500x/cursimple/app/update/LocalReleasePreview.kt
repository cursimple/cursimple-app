package com.x500x.cursimple.app.update

import android.content.Context
import com.x500x.cursimple.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

internal const val RELEASE_PREVIEW_DIR = "release-preview"

internal data class LocalReleasePreview(
    val versionName: String,
    val markdown: String,
    val localDir: File? = null,
    val localAssetDir: String? = null,
)

/** Private preview drafts take precedence over bundled debug announcements. */
internal suspend fun loadLocalReleasePreview(context: Context): LocalReleasePreview? = withContext(Dispatchers.IO) {
    val dir = File(context.filesDir, RELEASE_PREVIEW_DIR)
    val custom = dir.listFiles { file -> file.isFile && file.extension.equals("md", ignoreCase = true) }
        ?.maxByOrNull { it.lastModified() }
    try {
        if (custom != null) {
            LocalReleasePreview(custom.nameWithoutExtension.removePrefix("v"), custom.readText(), localDir = dir)
        } else {
            val markdown = context.assets.open("$RELEASE_PREVIEW_DIR/next-local.md").bufferedReader().use { it.readText() }
            LocalReleasePreview(
                versionName = context.getString(R.string.settings_dev_release_preview_local_version),
                markdown = markdown,
                localAssetDir = RELEASE_PREVIEW_DIR,
            )
        }
    } catch (_: IOException) {
        null
    }
}
