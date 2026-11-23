# Local Backup Diff Preview Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make local ZIP backup export and import confirmations show only the data that will actually be written or changed, including added, modified, and deleted entries, without changing the archive format.

**Architecture:** Keep `AppBackupPayload`, `PreferencesStoreSnapshot`, and `LocalBackupArchive` unchanged on disk. Add a pure snapshot-diff model/helper that compares the decoded backup payload with the current payload by store and entry key/value, then render the result in the existing Compose dialogs. Both `LocalBackupSettingsSection` and the legacy local-backup block in `ImportExportScreen` consume the same helper and presentation composable so the two entry points behave identically.

**Tech Stack:** Kotlin, Jetpack Compose, existing `PreferencesStoreSnapshot`/`PreferencesBackupEntry`, JUnit, Android Compose UI tests.

**Spec:** User request in the conversation: import should show only actual changes, additions, and deletions instead of all backup categories; export should show what will be exported and require confirmation.

## Global Constraints

- Do not change the ZIP manifest, payload schema, format version, or archive compatibility.
- Do not include WebDAV passwords or AI API keys in any preview or archive.
- Do not show unchanged categories or unchanged entries in the import preview.
- Keep existing local backup entry points and shared dialog/button components.
- Preserve the current destructive restore warning and require an explicit restore confirmation.
- UI changes must follow `docs/ui-guidelines.local.md`, including 48 dp touch targets, theme semantic colors, scrolling for long content, and checks at narrow width, light/dark mode, and enlarged text.
- Do not reset, clean, commit, tag, or push the user's existing working-tree changes.

## Review Focus

- A backup contains a store with identical entries: the import dialog should omit it entirely rather than showing a misleading category.
- An entry exists in both snapshots but has a different type or value: it should appear once under modified, not as both added and deleted.
- A store exists only in the backup or only on the device: the preview should report all incoming additions or all device-side deletions without crashing.
- A selected export category has an empty snapshot: it should still appear as exported with a zero-entry count, while unselected categories remain absent.
- Large entry lists or 1.3× text: the dialog should remain scrollable and should not hide the confirmation actions.

---

### Task 1: Snapshot diff model and tests

**Files:**
- Create: `app/src/main/java/com/x500x/cursimple/app/LocalBackupDiff.kt`
- Test: `app/src/test/java/com/x500x/cursimple/app/LocalBackupDiffTest.kt`

**Interfaces:**
- Consumes: `AppBackupPayload`, `PreferencesStoreSnapshot`, and `PreferencesBackupEntry` from `core-data`.
- Produces: `LocalBackupDiff.compare(current: AppBackupPayload, incoming: AppBackupPayload): LocalBackupDiffResult`; result types expose per-store `added`, `modified`, and `removed` entry lists plus the incoming store name and entry count needed by the UI.

- [ ] **Step 1: Write the failing tests**

Add pure JUnit tests covering:

- identical snapshots produce no changed stores;
- a same-name entry with a changed type/value appears only in `modified`;
- an incoming-only entry appears in `added`;
- a current-only entry appears in `removed`;
- an incoming-only store and a current-only store are both handled;
- the export summary preserves selected store order and includes empty selected stores.

Use real `PreferencesStoreSnapshot` and `PreferencesBackupEntry` values; do not mock repositories.

- [ ] **Step 2: Run the tests to verify they fail**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests 'com.x500x.cursimple.app.LocalBackupDiffTest' --no-configuration-cache
```

Expected: compilation failure because `LocalBackupDiff` and its result types do not exist.

- [ ] **Step 3: Implement the minimal diff model**

Implement `LocalBackupDiff.kt` with stable source order. Compare entries by `name`; compare the complete `PreferencesBackupEntry` value for modification. Treat missing store snapshots as an empty entry map. Expose only data needed by the UI, and keep the helper independent of Compose and Android.

- [ ] **Step 4: Run the tests to verify they pass**

Run the same Gradle test command. Expected: all `LocalBackupDiffTest` tests pass.

- [ ] **Step 5: Commit**

Do not commit unless the user explicitly requests a commit. Leave the changes in the existing working tree.

---

### Task 2: Export confirmation summary

**Files:**
- Modify: `app/src/main/java/com/x500x/cursimple/app/LocalBackupScreen.kt`
- Modify: `app/src/main/java/com/x500x/cursimple/app/ImportExportScreen.kt`
- Modify: `app/src/main/res/values/strings.xml`
- Modify: `app/src/main/res/values-en/strings.xml`
- Modify: `app/src/main/res/values-zh-rTW/strings.xml`
- Test: `app/src/androidTest/java/com/x500x/cursimple/app/BackupAndImportHelpUiQaTest.kt`

**Interfaces:**
- Consumes: `LocalBackupSelection` and the Task 1 export-summary/result types.
- Produces: a confirmation state shown before `CreateDocument` is launched; the system save picker must open only after the user confirms the displayed export summary.

- [ ] **Step 1: Write the failing UI test**

Extend the existing local-backup Compose coverage so clicking Export first shows a confirmation dialog containing only the selected store labels and entry counts, with the explicit export action. Assert that the existing ZIP save launcher callback has not been invoked before confirmation, then confirm and assert the callback is invoked once. Add a subset-selection assertion proving unselected categories are not shown.

- [ ] **Step 2: Run the targeted test to verify it fails**

Run:

```bash
./gradlew :app:connectedDebugAndroidTest --no-configuration-cache -Pandroid.testInstrumentationRunnerArguments.class=com.x500x.cursimple.app.BackupAndImportHelpUiQaTest
```

Expected: the new test fails because the current Export button opens the system save picker immediately and has no export-content confirmation state.

- [ ] **Step 3: Implement the minimal export confirmation flow**

In both local backup UI entry points, keep payload loading and category selection as-is, but make the Export button open a confirmation dialog first. Render the selected categories in source order with their actual entry counts and memo image count when the memo store is selected. Launch `CreateDocument` only from the dialog's explicit confirm action. Keep cancellation and the existing category close button behavior intact. Reuse a shared composable/helper where practical rather than maintaining different copy or list logic in the two screens.

Add localized strings for the export confirmation title, summary, item count, image count, confirm, and cancel text in all existing locales.

- [ ] **Step 4: Run the targeted test to verify it passes**

Run the same connected Android test command. Expected: the export confirmation test and existing backup/import-help tests pass.

- [ ] **Step 5: Commit**

Do not commit unless the user explicitly requests a commit.

---

### Task 3: Import change-only preview

**Files:**
- Modify: `app/src/main/java/com/x500x/cursimple/app/LocalBackupScreen.kt`
- Modify: `app/src/main/java/com/x500x/cursimple/app/ImportExportScreen.kt`
- Modify: `app/src/main/res/values/strings.xml`
- Modify: `app/src/main/res/values-en/strings.xml`
- Modify: `app/src/main/res/values-zh-rTW/strings.xml`
- Test: `app/src/androidTest/java/com/x500x/cursimple/app/BackupAndImportHelpUiQaTest.kt`

**Interfaces:**
- Consumes: `LocalBackupDiff.compare` and the current payload provider already used by `LocalBackupSettingsSection`/`ImportExportScreen`.
- Produces: import confirmation UI that lists only non-empty added, modified, and removed sections and keeps the existing irreversible restore confirmation action.

- [ ] **Step 1: Write the failing UI test**

Add coverage with a current payload and decoded incoming payload containing one unchanged store, one added entry, one modified entry, and one removed entry. Assert that the dialog shows the three change sections and their entry names, does not show the unchanged store, and still shows the destructive restore confirmation. Assert that cancelling does not call `onRestoreAppBackup` and confirming calls it once.

- [ ] **Step 2: Run the targeted test to verify it fails**

Run the existing targeted connected Android test command. Expected: failure because the current preview lists every incoming store and never computes current-device differences.

- [ ] **Step 3: Implement the minimal import diff flow**

After decoding a ZIP, load the current payload using the existing `onCreateAppBackup` callback, compute `LocalBackupDiff.compare(current, decoded.payload)`, and store the decoded payload plus diff for the dialog. Render only non-empty store changes under localized Added, Modified, and Deleted headings. Show a concise empty-state message when there are no differences, while retaining the warning that confirmation overwrites matching data. Keep the restore callback unchanged so the archive still restores the incoming payload exactly as before.

For stores represented by whole serialized values, display the snapshot entry name and store label rather than dumping the full serialized value. Keep the content in a vertically scrollable dialog and preserve the existing danger-colored final action.

- [ ] **Step 4: Run the targeted test to verify it passes**

Run the same connected Android test command. Expected: all backup/import-help tests pass, including the new change-only preview coverage.

- [ ] **Step 5: Run unit tests and build verification**

Run:

```bash
./gradlew test :app:assembleDebug :app:assembleDebugAndroidTest --no-configuration-cache
```

Expected: unit tests pass, both Debug APK tasks succeed, and no compilation/resource errors occur.

- [ ] **Step 6: Run diff and resource checks**

Run:

```bash
git diff --check
```

Expected: no whitespace errors. Do not alter unrelated user changes.

- [ ] **Step 7: Perform real-app visual verification**

Install the freshly assembled arm64 Debug APK on the existing emulator and open the local backup page. Exercise: export subset confirmation, cancel, import with changes, import with no changes, and back/cancel. Capture a screenshot of the export confirmation and import change-only preview. Check normal and narrow width, light and dark appearance, and 1.3× text scale, then restore the emulator's original settings.

- [ ] **Step 8: Commit**

Do not commit unless the user explicitly requests a commit.
