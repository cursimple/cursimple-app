package com.x500x.cursimple.feature.plugin

import com.x500x.cursimple.core.plugin.install.InstalledPluginRecord
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/**
 * Installed package bytes plus component archives and retained attachments; secure
 * configuration is outside this estimate.
 */
data class PluginStorageUsage(val packageBytes: Long, val dataBytes: Long) {
    val totalBytes: Long get() = packageBytes + dataBytes
}

/** Measure disk usage on IO and revalidate IDs before deriving component paths. */
suspend fun measurePluginStorage(filesDir: File, record: InstalledPluginRecord): PluginStorageUsage =
    withContext(Dispatchers.IO) {
        val packageBytes = directorySizeBytes(File(record.storagePath))
        val dataBytes = if (record.isExtension && SAFE_ID.matches(record.pluginId) && !record.pluginId.startsWith(".")) {
            directorySizeBytes(File(filesDir, "extensions-v1/${record.pluginId}.json")) +
                directorySizeBytes(File(filesDir, "extension-downloads/${record.pluginId}"))
        } else {
            0L
        }
        PluginStorageUsage(packageBytes, dataBytes)
    }

internal fun directorySizeBytes(root: File): Long {
    if (!root.exists()) return 0L
    val base = runCatching { root.canonicalFile }.getOrNull() ?: return 0L
    fun walk(file: File): Long {
        val canonical = runCatching { file.canonicalFile }.getOrNull() ?: return 0L
        if (canonical != base && !canonical.path.startsWith(base.path + File.separator)) return 0L
        if (file.isFile) return file.length()
        return file.listFiles().orEmpty().sumOf { walk(it) }
    }
    return walk(root)
}

private val SAFE_ID = Regex("[A-Za-z0-9._-]+")

internal fun formatInstalledAt(
    installedAt: String,
    zone: ZoneId = ZoneId.systemDefault(),
    locale: Locale = Locale.getDefault(),
): String = runCatching {
    OffsetDateTime.parse(installedAt)
        .atZoneSameInstant(zone)
        .format(DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT).withLocale(locale))
}.getOrDefault(installedAt)
