# Private Access WebDAV AI Import Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add developer-gated private-file access, WebDAV schedule backup/restore, and AI image schedule import.

**Architecture:** Extend existing DataStore user preferences, settings UI, and import/export UI. Use a custom `DocumentsProvider` for developer private-file access, OkHttp for WebDAV and AI HTTP calls, and existing `ScheduleSharePayload` import confirmation flow for backup/restore.

**Tech Stack:** Android Kotlin, Jetpack Compose, DataStore Preferences, OkHttp, Kotlin Serialization, Android DocumentsProvider, Activity Result APIs, JUnit 4.

---

## Chunk 1: Preferences and Settings

### Task 1: Extend UserPreferences

**Files:**
- Modify: `core-data/src/main/java/com/x500x/cursimple/core/data/UserPreferencesRepository.kt`
- Modify: `core-data/src/main/java/com/x500x/cursimple/core/data/DataStoreUserPreferencesRepository.kt`
- Modify: `core-data/src/test/java/com/x500x/cursimple/core/data/UserPreferencesTest.kt`

- [ ] Add `DEFAULT_WEBDAV_URL`.
- [ ] Add preference fields for private provider, WebDAV, and AI import.
- [ ] Add repository setters.
- [ ] Persist and reset the new fields in DataStore.
- [ ] Add default tests.

### Task 2: Thread preferences through ViewModel and Settings UI

**Files:**
- Modify: `app/src/main/java/com/x500x/cursimple/app/AppPreferencesViewModel.kt`
- Modify: `app/src/main/java/com/x500x/cursimple/app/SettingsScreen.kt`
- Modify: `app/src/main/java/com/x500x/cursimple/app/MainActivity.kt`

- [ ] Add ViewModel setters for all new preferences.
- [ ] Add WebDAV and AI settings destinations to the settings page.
- [ ] Add private provider switch in developer debug section.
- [ ] Add settings rows for WebDAV and AI import on the settings root page.
- [ ] Pass values and callbacks from `MainActivity`.

## Chunk 2: Private Documents Provider

### Task 3: Implement developer-gated provider

**Files:**
- Create: `app/src/main/java/com/x500x/cursimple/app/provider/PrivateFilesDocumentsProvider.kt`
- Modify: `app/src/main/AndroidManifest.xml`

- [ ] Add a `DocumentsProvider` declaration with read/write grant permissions.
- [ ] Implement roots for `filesDir` and `cacheDir`.
- [ ] Deny roots and operations when `privateFilesProviderEnabled` is false.
- [ ] Resolve document ids using canonical paths under allowed roots.
- [ ] Implement listing, open, create, delete, rename, and metadata.

## Chunk 3: WebDAV Backup and Restore

### Task 4: Add WebDAV models/client

**Files:**
- Create: `app/src/main/java/com/x500x/cursimple/app/webdav/WebDavClient.kt`
- Create: `app/src/main/java/com/x500x/cursimple/app/webdav/WebDavBackupModels.kt`
- Modify: `app/build.gradle.kts`

- [ ] Add app dependency on `libs.okhttp`.
- [ ] Implement Basic Auth requests.
- [ ] Implement `PROPFIND`, `MKCOL`, `PUT`, and `GET`.
- [ ] Parse WebDAV multistatus XML with platform XML APIs.

### Task 5: Connect WebDAV to import/export screen

**Files:**
- Modify: `app/src/main/java/com/x500x/cursimple/app/ImportExportScreen.kt`
- Modify: `app/src/main/java/com/x500x/cursimple/app/MainActivity.kt`

- [ ] Pass WebDAV config and callbacks into `ImportExportScreen`.
- [ ] Add test connection action from settings.
- [ ] Add upload current schedule backup action.
- [ ] Add list and restore remote backup action.
- [ ] Reuse existing import confirmation dialog for restore.

## Chunk 4: AI Image Import

### Task 6: Add AI import service

**Files:**
- Create: `app/src/main/java/com/x500x/cursimple/app/ai/AiScheduleImportClient.kt`
- Create: `app/src/main/java/com/x500x/cursimple/app/ai/AiScheduleImportModels.kt`
- Modify: `app/build.gradle.kts`

- [ ] Add request builder for OpenAI-compatible image input.
- [ ] Compress image to readable JPEG/PNG and encode data URL.
- [ ] Extract JSON from model response.
- [ ] Decode to `TermSchedule` and validate.
- [ ] Return typed errors for UI messages.

### Task 7: Add AI import actions to UI

**Files:**
- Modify: `app/src/main/java/com/x500x/cursimple/app/ImportExportScreen.kt`
- Modify: `app/src/main/java/com/x500x/cursimple/app/MainActivity.kt`

- [ ] Add Photo Picker image import action.
- [ ] Add camera capture action with a temporary app cache URI.
- [ ] If AI settings are missing, open AI settings dialog.
- [ ] Show progress and route successful AI output into existing confirmation dialog.

## Chunk 5: Verification

### Task 8: Run checks

**Files:**
- No source changes expected unless failures are found.

- [ ] Run `./gradlew.bat :core-data:testDebugUnitTest`.
- [ ] Run `./gradlew.bat :app:assembleDebug`.
- [ ] Fix compile/test issues.
- [ ] Report changed files and verification results without committing.

