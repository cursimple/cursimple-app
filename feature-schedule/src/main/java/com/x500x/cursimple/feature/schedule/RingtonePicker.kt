package com.x500x.cursimple.feature.schedule

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import android.widget.Toast
import androidx.core.content.IntentCompat

internal fun alarmRingtonePickerIntent(existingUri: String? = null): Intent =
    Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
        putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALARM)
        putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, false)
        putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
        existingUri
            ?.takeUnless(::isLocalAudioRingtoneUri)
            ?.let { runCatching { Uri.parse(it) }.getOrNull() }
            ?.let { putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, it) }
    }

internal fun launchAlarmRingtonePicker(
    context: Context,
    existingUri: String? = null,
    launch: (Intent) -> Unit,
) {
    try {
        launch(alarmRingtonePickerIntent(existingUri))
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(context, "当前系统没有可用的铃声选择器", Toast.LENGTH_SHORT).show()
    }
}

internal fun Intent.pickedAlarmRingtoneUri(): Uri? =
    IntentCompat.getParcelableExtra(this, RingtoneManager.EXTRA_RINGTONE_PICKED_URI, Uri::class.java)

internal fun takePersistableAudioReadPermission(context: Context, uri: Uri): Boolean =
    runCatching {
        context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }.isSuccess

internal fun showAudioPermissionFailedToast(context: Context) {
    Toast.makeText(context, "音频授权失败，请重新选择", Toast.LENGTH_SHORT).show()
}

internal fun isLocalAudioRingtoneUri(uriString: String?): Boolean =
    uriString?.contains("/document/", ignoreCase = true) == true
