package com.x500x.cursimple.app

import android.content.Intent
import android.net.Uri
import androidx.core.content.IntentCompat
import com.x500x.cursimple.core.data.memo.MemoTransferCodec
import com.x500x.cursimple.core.data.memo.MemoShareCode

internal fun memoTransferCode(intent: Intent?): String? {
    if (intent?.action != Intent.ACTION_SEND || intent.type != "text/plain") return null
    return intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString()?.takeIf(MemoShareCode::recognizes)
}

internal fun memoTransferUri(intent: Intent?): Uri? {
    intent ?: return null
    val uri = when (intent.action) {
        Intent.ACTION_SEND -> IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
        Intent.ACTION_VIEW -> intent.data
        else -> null
    } ?: return null
    if (uri.scheme !in setOf("content", "file")) return null
    return uri.takeIf { intent.type == MemoTransferCodec.MIME || uri.lastPathSegment?.endsWith(".csnotes", ignoreCase = true) == true }
}
