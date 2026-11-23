package com.x500x.cursimple.app

import com.x500x.cursimple.core.data.AppBackupPayload
import com.x500x.cursimple.core.data.AppBackupStores
import com.x500x.cursimple.core.data.PreferencesBackupEntry
import com.x500x.cursimple.core.data.PreferencesBackupValueType
import com.x500x.cursimple.core.data.PreferencesStoreSnapshot
import com.x500x.cursimple.core.data.memo.MemoTransferImage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalBackupDiffTest {
    private fun entry(name: String, value: String) = PreferencesBackupEntry(
        name = name,
        type = PreferencesBackupValueType.String,
        stringValue = value,
    )

    private fun payload(vararg stores: PreferencesStoreSnapshot) = AppBackupPayload(
        version = AppBackupPayload.CURRENT_VERSION,
        stores = stores.toList(),
    )

    @Test
    fun identicalSnapshotsProduceNoChangedStores() {
        val snapshot = payload(PreferencesStoreSnapshot(AppBackupStores.MEMOS, listOf(entry("title", "same"))))

        assertTrue(LocalBackupDiff.compare(snapshot, snapshot).isEmpty)
    }

    @Test
    fun sameNameWithChangedValueAppearsOnlyAsModified() {
        val current = payload(PreferencesStoreSnapshot(AppBackupStores.MEMOS, listOf(entry("title", "old"))))
        val incoming = payload(PreferencesStoreSnapshot(AppBackupStores.MEMOS, listOf(entry("title", "new"))))

        val diff = LocalBackupDiff.compare(current, incoming).stores.single()

        assertEquals(listOf("title"), diff.modified.map { it.name })
        assertTrue(diff.added.isEmpty())
        assertTrue(diff.removed.isEmpty())
    }

    @Test
    fun incomingOnlyEntryAppearsAsAdded() {
        val current = payload(PreferencesStoreSnapshot(AppBackupStores.MEMOS, emptyList()))
        val incoming = payload(PreferencesStoreSnapshot(AppBackupStores.MEMOS, listOf(entry("new", "value"))))

        assertEquals(listOf("new"), LocalBackupDiff.compare(current, incoming).stores.single().added.map { it.name })
    }

    @Test
    fun currentOnlyEntryAppearsAsRemoved() {
        val current = payload(PreferencesStoreSnapshot(AppBackupStores.MEMOS, listOf(entry("old", "value"))))
        val incoming = payload(PreferencesStoreSnapshot(AppBackupStores.MEMOS, emptyList()))

        assertEquals(listOf("old"), LocalBackupDiff.compare(current, incoming).stores.single().removed.map { it.name })
    }

    @Test
    fun partialBackupOnlyReportsChangesInIncludedStores() {
        val current = payload(PreferencesStoreSnapshot(AppBackupStores.SCHEDULE, listOf(entry("old", "value"))))
        val incoming = payload(PreferencesStoreSnapshot(AppBackupStores.MEMOS, listOf(entry("new", "value"))))

        val store = LocalBackupDiff.compare(current, incoming).stores.single()

        assertEquals(AppBackupStores.MEMOS, store.storeName)
        assertEquals(listOf("new"), store.added.map { it.name })
        assertTrue(store.removed.isEmpty())
    }

    @Test
    fun comparesActualMemoRecordsInsideTheStoredList() {
        val current = payload(PreferencesStoreSnapshot(AppBackupStores.MEMOS, listOf(entry(
            "memo_notes_json",
            """[{"id":"a","title":"Reading","body":"old"},{"id":"b","title":"Lab notes"},{"id":"c","title":"Checklist"}]""",
        ))))
        val incoming = payload(PreferencesStoreSnapshot(AppBackupStores.MEMOS, listOf(entry(
            "memo_notes_json",
            """[{"id":"a","title":"Reading","body":"new"},{"id":"d","title":"New idea"},{"id":"c","title":"Checklist"}]""",
        ))))

        val diff = LocalBackupDiff.compare(current, incoming).stores.single()

        assertEquals(listOf("New idea"), diff.added.map { it.name })
        assertEquals(listOf("Reading"), diff.modified.map { it.name })
        assertEquals(listOf("Lab notes"), diff.removed.map { it.name })
        assertEquals(3, LocalBackupDiff.exportSummary(incoming, listOf(AppBackupStores.MEMOS)).single().entryCount)
    }

    @Test
    fun previewsChangedCourseAndEventByName() {
        val current = payload(
            PreferencesStoreSnapshot(AppBackupStores.SCHEDULE, listOf(entry(
                "schedule_json__term",
                """{"updatedAt":"old","dailySchedules":[{"dayOfWeek":1,"courses":[{"id":"a","title":"Physics"}]}]}""",
            ))),
            PreferencesStoreSnapshot(AppBackupStores.SCHEDULE_EVENTS, listOf(entry(
                "schedule_events_json__term", """[{"id":"b","title":"Office hours"}]""",
            ))),
        )
        val incoming = payload(
            PreferencesStoreSnapshot(AppBackupStores.SCHEDULE, listOf(entry(
                "schedule_json__term",
                """{"updatedAt":"new","dailySchedules":[{"dayOfWeek":2,"courses":[{"id":"a","title":"Physics"}]}]}""",
            ))),
            PreferencesStoreSnapshot(AppBackupStores.SCHEDULE_EVENTS, listOf(entry(
                "schedule_events_json__term", "[]",
            ))),
        )

        val stores = LocalBackupDiff.compare(current, incoming).stores.associateBy { it.storeName }

        assertEquals(listOf("Physics"), stores.getValue(AppBackupStores.SCHEDULE).modified.map { it.name })
        assertEquals(listOf("Office hours"), stores.getValue(AppBackupStores.SCHEDULE_EVENTS).removed.map { it.name })
    }

    @Test
    fun leavesMemoImagesOutOfTheDiffForAPartialBackupWithoutMemos() {
        val schedule = PreferencesStoreSnapshot(AppBackupStores.SCHEDULE, emptyList())
        val current = payload(schedule).copy(memoImages = listOf(MemoTransferImage("image", "old")))
        val incoming = payload(schedule)

        assertTrue(LocalBackupDiff.compare(current, incoming).isEmpty)
    }

    @Test
    fun memoImageCountChangeMakesSnapshotChanged() {
        val current = AppBackupPayload(
            version = AppBackupPayload.CURRENT_VERSION,
            stores = listOf(PreferencesStoreSnapshot(AppBackupStores.MEMOS, emptyList())),
            memoImages = listOf(MemoTransferImage("old", "data")),
        )
        val incoming = AppBackupPayload(
            version = AppBackupPayload.CURRENT_VERSION,
            stores = listOf(PreferencesStoreSnapshot(AppBackupStores.MEMOS, emptyList())),
            memoImages = listOf(MemoTransferImage("new", "data"), MemoTransferImage("another", "data")),
        )

        val diff = LocalBackupDiff.compare(current, incoming)

        assertTrue(diff.memoImagesChanged)
        assertEquals(1, diff.currentMemoImageCount)
        assertEquals(2, diff.incomingMemoImageCount)
        assertTrue(!diff.isEmpty)
    }

    @Test
    fun changedMemoImageContentWithSameCountIsNotReportedAsIdentical() {
        val current = payload(PreferencesStoreSnapshot(AppBackupStores.MEMOS, emptyList()))
            .copy(memoImages = listOf(MemoTransferImage("image", "old")))
        val incoming = current.copy(memoImages = listOf(MemoTransferImage("image", "new")))

        assertTrue(LocalBackupDiff.compare(current, incoming).memoImagesChanged)
        assertTrue(!LocalBackupDiff.compare(current, incoming).isEmpty)
    }

    @Test
    fun exportSummaryPreservesSelectionOrderAndIncludesEmptyStores() {
        val payload = payload(
            PreferencesStoreSnapshot(AppBackupStores.SCHEDULE, emptyList()),
            PreferencesStoreSnapshot(AppBackupStores.MEMOS, listOf(entry("note", "value"))),
        )

        assertEquals(
            listOf(AppBackupStores.MEMOS, AppBackupStores.SCHEDULE),
            LocalBackupDiff.exportSummary(
                payload,
                listOf(AppBackupStores.MEMOS, AppBackupStores.SCHEDULE),
            ).map { it.storeName },
        )
        assertEquals(0, LocalBackupDiff.exportSummary(payload, listOf(AppBackupStores.SCHEDULE)).single().entryCount)
    }
}
