# Local ZIP Backup UI Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a standalone local ZIP backup menu above WebDAV with selective export, system file pickers, import preview, and confirmed restore.

**Architecture:** Reuse `AppBackupPayload` and `LocalBackupArchive`; keep WebDAV's JSON flow unchanged. `ImportExportScreen` owns picker/state/dialog presentation and invokes the existing suspend backup callbacks. Import is decoded and validated before confirmation; restore occurs only after the user confirms.

**Tech Stack:** Kotlin, Jetpack Compose Material 3, Activity Result Contracts, kotlinx.serialization, Java ZIP streams, Android resources.

**Spec:** Existing local backup requirements from the conversation and `AGENTS.md`.

## Global Constraints

- The standalone `备份数据` row must appear above WebDAV in the `备份与恢复` card.
- Use `CreateDocument("application/zip")` and `OpenDocument` without storage permissions.
- Preserve WebDAV JSON upload/list/restore behavior.
- Chinese is the default UI language; add English and Traditional Chinese resources.
- Validate the complete ZIP before calling restore; malformed input must not alter current data.

## Review Focus

- A canceled picker leaves the screen state unchanged.
- An invalid or oversized archive reports failure and never calls restore.
- A partial export includes only selected stores and memo images only with memos.
- A restore failure returns to an actionable idle state.
- The new row remains above WebDAV and does not disable or alter WebDAV operations.

---

### Task 1: Backup selection and archive behavior tests

**Files:**
- Test: `app/src/test/java/com/x500x/cursimple/app/LocalBackupArchiveTest.kt`
- Create: `app/src/main/java/com/x500x/cursimple/app/LocalBackupSelection.kt`

**Interfaces:**
- Produces `LocalBackupSelection.all(storeNames: List<String>)` and `selectedStoreNames` for deterministic store selection.

- [ ] Add tests for all-store selection, toggling a store, and rejecting an empty selection.
- [ ] Run the focused test and observe failure for the missing selection helper.
- [ ] Implement the minimal immutable selection helper.
- [ ] Run the focused test and verify it passes.

### Task 2: Local backup screen integration

**Files:**
- Modify: `app/src/main/java/com/x500x/cursimple/app/ImportExportScreen.kt`
- Modify: `app/src/main/res/values/strings.xml`
- Modify: `app/src/main/res/values-en/strings.xml`
- Modify: `app/src/main/res/values-zh-rTW/strings.xml`

**Interfaces:**
- Consumes `LocalBackupArchive.encode/decode`, `onCreateAppBackup`, and `onRestoreAppBackup`.
- Produces the standalone `备份数据` row, ZIP picker flow, selection sheet, import preview/confirmation, and success/failure feedback.

- [ ] Add localized strings for the row, export/import actions, selection, preview, confirmation, loading, success, failure, and exclusion note.
- [ ] Add state and `CreateDocument`/`OpenDocument` launchers; read/write URI bytes on `Dispatchers.IO`.
- [ ] Add the standalone row before all WebDAV rows, opening a local backup sheet.
- [ ] Add selective store export and ZIP creation with a timestamped `.zip` filename.
- [ ] Add ZIP import decoding, preview, and explicit restore confirmation.
- [ ] Keep WebDAV code paths unchanged and make local failures retryable.
- [ ] Run compilation and focused tests.

### Task 3: Verification

- [ ] Run `./gradlew :app:testDebugUnitTest --tests com.x500x.cursimple.app.LocalBackupArchiveTest`.
- [ ] Run the full app debug unit-test suite.
- [ ] Run `./gradlew :app:assembleDebug`.
- [ ] Inspect the diff and report any unverified device-only UI scenarios.
