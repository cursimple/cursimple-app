package com.x500x.cursimple.app

import com.x500x.cursimple.core.data.AppBackupPayload
import com.x500x.cursimple.core.data.AppBackupStores
import com.x500x.cursimple.core.data.PreferencesBackupEntry
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

internal data class LocalBackupChange(val name: String)

internal data class LocalBackupStoreDiff(
    val storeName: String,
    val added: List<LocalBackupChange>,
    val modified: List<LocalBackupChange>,
    val removed: List<LocalBackupChange>,
) {
    val isChanged: Boolean
        get() = added.isNotEmpty() || modified.isNotEmpty() || removed.isNotEmpty()
}

internal data class LocalBackupDiffResult(
    val stores: List<LocalBackupStoreDiff>,
    val currentMemoImageCount: Int,
    val incomingMemoImageCount: Int,
    val memoImagesChanged: Boolean,
) {
    val isEmpty: Boolean
        get() = stores.isEmpty() && !memoImagesChanged
}

data class LocalBackupExportSummary(
    val storeName: String,
    val entryCount: Int,
    val memoImageCount: Int,
)

internal object LocalBackupDiff {
    fun compare(current: AppBackupPayload, incoming: AppBackupPayload): LocalBackupDiffResult {
        val currentByStore = current.stores.associateBy { it.storeName }
        val includesMemos = incoming.store(AppBackupStores.MEMOS) != null

        return LocalBackupDiffResult(
            stores = incoming.stores.mapNotNull { store ->
                val old = previewRecords(store.storeName, currentByStore[store.storeName]?.entries.orEmpty())
                val new = previewRecords(store.storeName, store.entries)
                LocalBackupStoreDiff(
                    storeName = store.storeName,
                    added = new.keys.minus(old.keys).map { LocalBackupChange(new.getValue(it).name) },
                    modified = new.keys.intersect(old.keys).mapNotNull { key ->
                        new.getValue(key).takeIf { it != old[key] }?.let { LocalBackupChange(it.name) }
                    },
                    removed = old.keys.minus(new.keys).map { LocalBackupChange(old.getValue(it).name) },
                ).takeIf { it.isChanged }
            },
            currentMemoImageCount = current.memoImages.size,
            incomingMemoImageCount = incoming.memoImages.size,
            memoImagesChanged = includesMemos &&
                current.memoImages.associateBy { it.id } != incoming.memoImages.associateBy { it.id },
        )
    }

    fun exportSummary(
        payload: AppBackupPayload,
        selectedStoreNames: List<String>,
    ): List<LocalBackupExportSummary> {
        val stores = payload.stores.associateBy { it.storeName }
        return selectedStoreNames.map { storeName ->
            LocalBackupExportSummary(
                storeName = storeName,
                entryCount = previewRecords(storeName, stores[storeName]?.entries.orEmpty()).size,
                memoImageCount = if (storeName == AppBackupStores.MEMOS) payload.memoImages.size else 0,
            )
        }
    }

    private data class PreviewRecord(val name: String, val value: Any)

    private fun previewRecords(
        storeName: String,
        entries: List<PreferencesBackupEntry>,
    ): Map<Pair<String, String?>, PreviewRecord> = buildMap {
        entries.forEach { entry ->
            val records = recordsInEntry(storeName, entry)
            if (records == null) put(entry.name to null, PreviewRecord(entry.name, entry))
            else records.forEach { (id, record) -> put(entry.name to id, record) }
        }
    }

    private fun recordsInEntry(
        storeName: String,
        entry: PreferencesBackupEntry,
    ): Map<String, PreviewRecord>? {
        val field = entry.name.substringBefore("__")
        val supportedFields = when (storeName) {
            AppBackupStores.SCHEDULE -> setOf("schedule_json")
            AppBackupStores.MANUAL_COURSES -> setOf("manual_courses_json", "manual_courses_deleted")
            AppBackupStores.SCHEDULE_EVENTS -> setOf("schedule_events_json")
            AppBackupStores.COURSE_NOTES -> setOf("course_notes_json")
            AppBackupStores.MEMOS -> setOf("memo_notes_json")
            AppBackupStores.TERM_PROFILES -> setOf("term_profiles_json")
            AppBackupStores.REMINDERS -> setOf("reminder_rules", "custom_occupancies", "system_alarm_records")
            AppBackupStores.PLUGIN_REGISTRY -> setOf("installed_plugins")
            AppBackupStores.PLUGIN_COMPONENTS -> setOf("installed_components")
            else -> return null
        }
        if (field !in supportedFields) return null
        val raw = entry.stringValue ?: return null
        val data = runCatching { Json.parseToJsonElement(raw) }.getOrNull()
        val records = linkedMapOf<String, PreviewRecord>()
        if (field == "schedule_json") {
            val schedule = data as? JsonObject ?: return null
            val days = schedule["dailySchedules"] as? JsonArray ?: return null
            for (value in days) {
                val day = value as? JsonObject ?: return null
                val weekday = day["dayOfWeek"] ?: return null
                val courses = day["courses"] as? JsonArray ?: return null
                for (course in courses) {
                    val item = course as? JsonObject ?: return null
                    val id = item.text("id") ?: return null
                    val title = item.text("title") ?: id
                    if (records.put(id, PreviewRecord(title, weekday to item)) != null) return null
                }
            }
            return records
        }

        val items = data as? JsonArray ?: return null
        for (value in items) {
            val item = value as? JsonObject ?: return null
            val details = when (field) {
                "course_notes_json" -> item["anchor"] as? JsonObject ?: return null
                "manual_courses_deleted" -> item["course"] as? JsonObject ?: return null
                else -> item
            }
            val id = details.text("id") ?: details.text("courseId") ?: details.text("pluginId") ?: return null
            val label = details.text("title") ?: details.text("name")
                ?: details.text("body")?.lineSequence()?.firstOrNull()?.take(60) ?: id
            val recordId = if (field == "installed_plugins") "${item.text("source")}:$id" else id
            if (records.put(recordId, PreviewRecord(label, item)) != null) return null
        }
        return records
    }

    private fun JsonObject.text(field: String): String? =
        (get(field) as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }
}
