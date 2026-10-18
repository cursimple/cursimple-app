package com.x500x.cursimple.app.util

import android.content.ClipData
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.annotation.RequiresApi
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

internal suspend fun writeSharePng(context: Context, bitmap: Bitmap, uri: Uri) = withContext(Dispatchers.IO) {
    checkNotNull(context.contentResolver.openOutputStream(uri)).use {
        check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it))
    }
}

/** Publish only a fully written image; remove failed pending entries. */
@RequiresApi(Build.VERSION_CODES.Q)
internal suspend fun saveQrToPictures(context: Context, bitmap: Bitmap): Uri = withContext(Dispatchers.IO) {
    val resolver = context.contentResolver
    val uri = checkNotNull(resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, ContentValues().apply {
        put(MediaStore.Images.Media.DISPLAY_NAME, "cursimple-qr-${System.currentTimeMillis()}.png")
        put(MediaStore.Images.Media.MIME_TYPE, "image/png")
        put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/CurSimple")
        put(MediaStore.Images.Media.IS_PENDING, 1)
    }))
    try {
        writeSharePng(context, bitmap, uri)
        check(resolver.update(uri, ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }, null, null) == 1)
        uri
    } catch (error: Throwable) {
        resolver.delete(uri, null, null)
        throw error
    }
}

/** Copy schedule image to system Pictures directory on Android 10+. */
@RequiresApi(Build.VERSION_CODES.Q)
internal suspend fun saveScheduleImageToPictures(
    context: Context,
    sourceUri: Uri,
    bitmap: Bitmap? = null,
    termName: String? = null,
    weekNumber: Int? = null,
    allWeeks: Boolean = false,
    wallpaperMode: Boolean = false,
): Uri = withContext(Dispatchers.IO) {
    val resolver = context.contentResolver
    val slug = termName?.trim()?.map { if (it.isLetterOrDigit()) it else '-' }?.joinToString("")?.trim('-')?.take(30)
    val prefix = if (!slug.isNullOrBlank()) "cursimple-$slug" else "cursimple-schedule"
    val scope = if (allWeeks) "all-weeks" else (weekNumber?.let { "week$it" } ?: "schedule")
    val suffix = if (wallpaperMode) "-wallpaper" else ""
    val displayName = "$prefix-$scope$suffix-${System.currentTimeMillis()}.png"

    val uri = checkNotNull(resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, ContentValues().apply {
        put(MediaStore.Images.Media.DISPLAY_NAME, displayName)
        put(MediaStore.Images.Media.MIME_TYPE, "image/png")
        put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/CurSimple")
        put(MediaStore.Images.Media.IS_PENDING, 1)
    }))
    try {
        val streamOpened = runCatching {
            resolver.openInputStream(sourceUri)?.use { input ->
                checkNotNull(resolver.openOutputStream(uri)).use { output ->
                    input.copyTo(output)
                }
            }
        }.isSuccess
        if (!streamOpened && bitmap != null) {
            writeSharePng(context, bitmap, uri)
        }
        check(resolver.update(uri, ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }, null, null) == 1)
        uri
    } catch (error: Throwable) {
        resolver.delete(uri, null, null)
        throw error
    }
}

internal suspend fun copyImageUri(context: Context, sourceUri: Uri, targetUri: Uri) = withContext(Dispatchers.IO) {
    context.contentResolver.openInputStream(sourceUri)?.use { input ->
        checkNotNull(context.contentResolver.openOutputStream(targetUri)).use { output ->
            input.copyTo(output)
        }
    } ?: throw IllegalStateException("Cannot copy image")
}


internal suspend fun qrImageShareIntent(context: Context, bitmap: Bitmap): Intent = withContext(Dispatchers.IO) {
    val folder = File(context.cacheDir, "images").apply { mkdirs() }
    val file = File.createTempFile("cursimple-qr-", ".png", folder)
    file.outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    Intent(Intent.ACTION_SEND).apply {
        type = "image/png"
        putExtra(Intent.EXTRA_STREAM, uri)
        clipData = ClipData.newUri(context.contentResolver, file.name, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
}

internal fun launchScheduleShare(context: Context, intent: Intent, title: String) {
    context.startActivity(Intent.createChooser(intent, title).apply {
        clipData = intent.clipData
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
    })
}
