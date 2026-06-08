# 2026-06-08 Bug And Risk Findings

> This document is the running findings log for a focused bug and risk analysis of the project. Each confirmed finding is appended here before being folded into the final remediation plan.

## Findings

### 1. Global cleartext HTTP is enabled for the whole app

**Conclusion:** `app/src/main/res/xml/network_security_config.xml` sets `<base-config cleartextTrafficPermitted="true" />`, which enables cleartext HTTP for every network path in the app instead of limiting it to explicitly trusted development or school intranet domains.

**Evidence:** `app/src/main/res/xml/network_security_config.xml:3`; the app manifest applies this config globally via `android:networkSecurityConfig="@xml/network_security_config"` in `app/src/main/AndroidManifest.xml:31`. Sensitive clients rely on user-provided URLs without an HTTPS-only gate, for example AI import builds a Bearer-token request after `normalizeAiEndpoint(...)` in `app/src/main/java/com/x500x/cursimple/app/ai/AiScheduleImportClient.kt:37-43`, WebDAV sends credentials in `app/src/main/java/com/x500x/cursimple/app/webdav/WebDavClient.kt:70`, and plugin WebView explicitly sets `WebSettings.MIXED_CONTENT_ALWAYS_ALLOW` in `feature-plugin/src/main/java/com/x500x/cursimple/feature/plugin/PluginWebSessionScreen.kt:1176`.

**Impact:** Any network feature, including plugin market/session flows, AI import, WebDAV, update checks, or opened plugin URLs, can accidentally use plaintext transport. This increases credential/session leakage and tampering risk, especially because the app handles school login/plugin workflows and downloadable update/plugin artifacts.

**Suggested verification:** Add a manifest/resource test that fails if release builds use a global cleartext `base-config`, and add network-client tests that assert production URLs are HTTPS unless a user-visible, scoped allowlist entry is explicitly selected.

### 2. Clean checkouts can fail during Gradle configuration because signing values are required eagerly

**Conclusion:** `app/build.gradle.kts` resolves all four signing secrets at script evaluation time, before a specific build type or task is known. Because `keystore.properties` is not tracked and only `keystore.example.properties` is committed, a clean checkout without environment variables can fail even for non-release tasks such as listing tasks, running tests, or building library modules.

**Evidence:** `app/build.gradle.kts:20-31` defines and immediately calls `requireSigningValue(...)`; debug and release both force `signingConfigs.getByName("classViewer")` at `app/build.gradle.kts:69` and `app/build.gradle.kts:74`; `git ls-files` includes `keystore.example.properties` but not `keystore.properties`.

**Impact:** New contributors and CI jobs cannot reliably run tests or debug builds without private signing material. This also couples developer/debug workflows to release signing, which makes ordinary validation brittle.

**Suggested verification:** Test a clean checkout with no `keystore.properties` and no `CLASS_VIEWER_*` environment variables by running a non-release task such as `./gradlew :core-kernel:test` or `./gradlew tasks`; add a Gradle TestKit or documented CI smoke check if practical.

### 3. `BeijingTime` ignores its timezone contract and uses the device default timezone

**Conclusion:** The shared time helper is named `BeijingTime`, but its `zone` property returns `ZoneId.systemDefault()`, and its `today(zone)`, `todayIn(zone)`, `nowTimeIn(zone)`, `nowDateTimeIn(zone)`, and `dayOfWeek(zone)` functions ignore the supplied `ZoneId`. Callers that expect China school schedule dates can calculate the wrong day/week when the device timezone is not China time, and tests cannot reliably inject a non-default zone through these APIs.

**Evidence:** `core-kernel/src/main/java/com/x500x/cursimple/core/kernel/time/BeijingTime.kt:11-50`; schedule and widget paths call this helper for current day/time, for example `feature-schedule/src/main/java/com/x500x/cursimple/feature/schedule/ScheduleScreen.kt:1796`, `feature-widget/src/main/java/com/x500x/cursimple/feature/widget/ScheduleWidgetDataSource.kt:62-85`, `feature-widget/src/main/java/com/x500x/cursimple/feature/widget/NextCourseDataSource.kt:60-63`, and `feature-widget/src/main/java/com/x500x/cursimple/feature/widget/ReminderDataSource.kt:60-64`.

**Impact:** A user whose device timezone is outside China, or an Android system that temporarily reports another timezone, can see the wrong current day/week, next course, widget content, and reminder window around midnight. The helper also makes timezone-sensitive tests misleading because the explicit zone parameter has no effect.

**Suggested verification:** Add unit tests that pass `ZoneId.of("Asia/Shanghai")` while the instant is still the previous/next day in another timezone; verify `todayIn`, widget data sources, and reminder sync windows all use the intended app zone consistently.

### 4. Plugin checksum verification accepts empty or partial checksum manifests

**Conclusion:** Plugin installation requires `checksums.json`, but `PluginChecksumVerifier.verify(...)` returns `true` when the checksum `files` map is empty and does not require every package file to be covered. A package can therefore pass the installer's "checksum verified" gate without proving integrity for the manifest, entry script, UI schema, timing profile, or any additional payload file.

**Evidence:** `core-plugin/src/main/java/com/x500x/cursimple/core/plugin/security/PluginVerification.kt:24-32` uses `checksums.files.all { ... }`, which is vacuously true for an empty map; `core-plugin/src/main/java/com/x500x/cursimple/core/plugin/install/PluginInstaller.kt:97-100` accepts the package when `preview.checksumVerified` is true; `core-plugin/src/main/java/com/x500x/cursimple/core/plugin/packageformat/PluginPackageLayout.kt:17-19` only requires that `checksums.json` exists and decodes.

**Impact:** Plugin package integrity can be bypassed by shipping an empty or intentionally incomplete checksum file. This weakens the trust boundary for remote/local plugin installation and makes later runtime permissions such as web session access or schedule writing easier to abuse.

**Suggested verification:** Add unit tests for empty `files`, missing coverage for `manifest.json`/entry files, unknown checksum paths, invalid digest lengths, and a positive case where every non-checksum file is covered by SHA-256.

### 5. Plugin manifest IDs are used as filesystem paths without sanitization

**Conclusion:** Plugin installation builds the target directory from raw `manifest.id`, `versionCode`, and source name. Unlike component installation, the plugin file store does not sanitize the ID or assert the canonical target stays under the plugin root. A malicious plugin manifest can include path separators or `..` segments in `id` and cause writes, directory creation, or the pre-install `deleteRecursively()` to operate outside `filesDir/plugins-v3`.

**Evidence:** `core-plugin/src/main/java/com/x500x/cursimple/core/plugin/storage/PluginFileStore.kt:24-38` creates `File(rootDir, "${manifest.id}-${manifest.versionCode}-$sourceTag")`, deletes it if it exists, and writes package files below it; `core-plugin/src/main/java/com/x500x/cursimple/core/plugin/manifest/PluginManifest.kt:8-26` defines `id` as an unconstrained `String`; by contrast, `core-plugin/src/main/java/com/x500x/cursimple/core/plugin/component/PluginComponentInstaller.kt:87-101` sanitizes component IDs and checks canonical paths.

**Impact:** A crafted local or remote plugin package can escape the plugin storage root and overwrite or delete other app-private files. This can corrupt settings, plugin state, caches, or other files under the app sandbox even before the plugin runtime starts.

**Suggested verification:** Add installer tests for manifest IDs containing `/`, `\`, `..`, absolute path markers, and very long names; assert installation rejects them and that canonical write targets always remain inside the plugin root.

### 6. Normalized plugin entry paths are not persisted, so valid packages can fail at runtime

**Conclusion:** `PluginPackageReader` normalizes `manifest.entry` before checking that the entry file exists, but the normalized value is not written back into the manifest or installed record. A package whose manifest uses a Windows-style entry path such as `scripts\main.js` can pass installation because the ZIP entry is normalized to `scripts/main.js`, then fail later because `PluginFileStore.loadEntryScript(...)` reads the raw `scripts\main.js` path from the installed record.

**Evidence:** `core-plugin/src/main/java/com/x500x/cursimple/core/plugin/packageformat/PluginPackageReader.kt:35-38` computes `entryPath = normalizeEntryPath(manifest.entry)` only for validation; `core-plugin/src/main/java/com/x500x/cursimple/core/plugin/install/PluginInstaller.kt:116-126` persists `entry = entry` from the raw manifest; `core-plugin/src/main/java/com/x500x/cursimple/core/plugin/storage/PluginFileStore.kt:47-49` later reads `record.entry` directly.

**Impact:** Plugin packages that are accepted by the installer can fail only when the user starts sync, producing a confusing runtime error instead of an install-time rejection or automatic normalization.

**Suggested verification:** Add a package-format test with `entry: "scripts\\main.js"` and a ZIP file at `scripts/main.js`; assert either the installer rejects it consistently or the installed record stores `scripts/main.js` and runtime loading succeeds.

### 7. Reminder text shows the source weekday instead of the actual date for temporary make-up classes

**Conclusion:** Temporary make-up rules can schedule a course on a target date whose weekday differs from the course's original `time.dayOfWeek`. `ReminderPlanner` correctly builds the trigger time from the target `courseDate`, but the title and message weekday are still derived from `course.time.dayOfWeek`, so reminders can display inconsistent text such as `5月6日 周一` for a Wednesday make-up class.

**Evidence:** `core-reminder/src/main/java/com/x500x/cursimple/core/reminder/ReminderPlanner.kt:117-144` includes override target dates and builds plans with the target `courseDate`; `core-reminder/src/main/java/com/x500x/cursimple/core/reminder/ReminderPlanner.kt:162-177` formats the weekday using `weekdayName(course.time.dayOfWeek)` instead of `courseDate.dayOfWeek.value`.

**Impact:** Users can receive correct-time alarms with wrong weekday labels during temporary make-up schedules, increasing the chance of misreading a reminder or mistrusting the app around schedule changes.

**Suggested verification:** Add a `ReminderPlannerTest` case where a Monday course is made up on Wednesday and assert the generated title/message use Wednesday while still selecting the Monday source course.

### 8. First-course reminder rules are saved but not included in alarm synchronization

**Conclusion:** The UI can save `FirstCourseOfPeriod` rules and immediately request alarm synchronization, but `ReminderCoordinator.syncAlarmsForWindow(...)` filters enabled rules down to `ReminderScopeType.LabelRule` only. As a result, first-course and flexible first-course rules remain visible in settings but do not create App-managed or system-clock alarms during the normal sync path.

**Evidence:** `feature-schedule/src/main/java/com/x500x/cursimple/feature/schedule/ScheduleViewModel.kt:744` calls `upsertFirstCourseReminder(...)`, and `feature-schedule/src/main/java/com/x500x/cursimple/feature/schedule/ScheduleViewModel.kt:945` calls `upsertFlexibleFirstCourseReminder(...)`; both paths call `syncTodaySystemClockAlarms(...)`, which reaches `ReminderCoordinator.syncAlarmsForWindow(...)`. That sync method filters rules with `it.scopeType == ReminderScopeType.LabelRule` at `core-reminder/src/main/java/com/x500x/cursimple/core/reminder/ReminderCoordinator.kt:699-704`.

**Impact:** Users can enable first-course reminders and see success messages, but no alarm is registered for those rules. This is a silent functional failure in one of the main reminder features.

**Suggested verification:** Add `SystemAlarmRegistryTest` coverage where a `FirstCourseOfPeriod` rule exists in the repository, `syncAlarmsForWindow(...)` is called, and the fake dispatcher receives exactly the selected first-course plan.

### 9. Exam reminder detail and mute flows still look for the old `Exam` rule type

**Conclusion:** The exam reminder creation path now stores per-label exam reminders as `ReminderScopeType.LabelRule` records whose display names start with `考试提醒：`, but the course-detail mute flow still searches for `ReminderScopeType.Exam`. This makes per-exam mute/restore actions fail even after global exam reminders are enabled.

**Evidence:** `feature-schedule/src/main/java/com/x500x/cursimple/feature/schedule/ScheduleViewModel.kt:550-599` creates/updates exam reminders through `reminderCoordinator.upsertLabelRule(...)`; `feature-schedule/src/main/java/com/x500x/cursimple/feature/schedule/ScheduleViewModel.kt:666-678` checks only `it.scopeType == ReminderScopeType.Exam` before muting; `feature-schedule/src/main/java/com/x500x/cursimple/feature/schedule/ScheduleScreen.kt:4281-4289` also treats `LabelRule` separately from `Exam` when checking course reminder state.

**Impact:** After enabling exam reminders, an exam detail screen can still behave as if exam reminders are not enabled, and users cannot mute or restore a single exam reminder through the old `Exam` rule path.

**Suggested verification:** Add feature/view-model tests or focused coordinator tests that enable exam label reminders, open an exam course, mute only that exam, and assert that the targeted exam stops producing a reminder while other exams sharing valid labels still do.

### 10. Component packages can install extra files that were not covered by the manifest hash

**Conclusion:** Component package validation hashes only the files named by `manifest.files` when that list is non-empty, but installation writes every package file except `manifest.json`. This allows a component package to include one valid hashed payload plus extra unverified files that still land in the component directory.

**Evidence:** `core-plugin/src/main/java/com/x500x/cursimple/core/plugin/component/PluginComponentInstaller.kt:70-78` computes the SHA-256 over `payloadFiles`; `core-plugin/src/main/java/com/x500x/cursimple/core/plugin/component/PluginComponentInstaller.kt:97-103` writes all `layout.files.filterKeys { it != MANIFEST_FILE }` to disk.

**Impact:** Component integrity guarantees are incomplete. A marketplace or local component package can carry unverified native/library/assets alongside the verified list, which is especially risky for engine/runtime components.

**Suggested verification:** Add `PluginComponentInstallerTest` cases where `manifest.files` lists one valid file and the ZIP contains an additional file; installation should fail unless the extra file is explicitly included in the hashed payload set.

### 11. Update checks can treat a fast mirror 404 as "no release" even when GitHub has a release

**Conclusion:** Release metadata probing races multiple candidates and accepts both `2xx` and `404` responses when `successfulOnly` is false. A non-GitHub mirror/proxy that quickly returns 404 can become the selected response if it is faster than a valid `200` response, causing the app to report no release.

**Evidence:** `app/src/main/java/com/x500x/cursimple/app/update/AppUpdateChecker.kt:35-46` returns `NoRelease` for the selected response when its status is 404; `app/src/main/java/com/x500x/cursimple/app/update/AppUpdateChecker.kt:219-250` includes `2xx` or `404` responses and picks the lowest latency response.

**Impact:** Users may miss available updates because a mirror endpoint fails differently from GitHub source. This is a reliability bug in the update path, not just a transient network failure.

**Suggested verification:** Add a fake mirror pool test where a mirror returns 404 quickly and the GitHub source returns 200 later; the checker should prefer the 200 release response.

### 12. Update asset selection falls back to an incompatible APK when ABI does not match

**Conclusion:** If `update.json` contains assets but none match the device ABI and none are marked `universal`, the update checker returns the first asset anyway. That can download an APK for the wrong ABI instead of reporting that no compatible APK is available.

**Evidence:** `app/src/main/java/com/x500x/cursimple/app/update/AppUpdateChecker.kt:135-137` selects supported ABI first, then `universal`, then `parsed.firstOrNull()`.

**Impact:** Users on an unsupported ABI can download an APK that fails to install or installs a build that cannot run correctly. The UI will present it as a normal update even though compatibility was not established.

**Suggested verification:** Add a manifest-selection test where `Build.SUPPORTED_ABIS` has no overlap with the manifest assets and no universal asset exists; expected result should be a clear failure rather than a selected first asset.

### 13. Large images and ZIP entries are read into memory before size limits are enforced

**Conclusion:** Several import/install paths decode or read full user-controlled content before applying bounds. Image import decodes the whole bitmap, plugin and component package readers call `zip.readBytes()` for each entry before checking cumulative uncompressed size, and local plugin import reads the selected URI fully into memory.

**Evidence:** `app/src/main/java/com/x500x/cursimple/app/ai/AiScheduleImportClient.kt:118-121` and `app/src/main/java/com/x500x/cursimple/app/ImportExportScreen.kt:969-971` decode full images; `feature-plugin/src/main/java/com/x500x/cursimple/feature/plugin/PluginMarketScreen.kt:1346` reads a local package with `readBytes()`; `core-plugin/src/main/java/com/x500x/cursimple/core/plugin/packageformat/PluginPackageReader.kt:22` and `core-plugin/src/main/java/com/x500x/cursimple/core/plugin/component/PluginComponentInstaller.kt:118` read full ZIP entries before checking total size.

**Impact:** Very large photos, ZIP bombs, or oversized local packages can cause high memory use, UI stalls, or process OOM before the app reaches its validation logic.

**Suggested verification:** Add stream-limited readers for images/packages and tests using oversized streams or high-compression ZIP entries to assert the app aborts before allocating the full payload.

### 14. Plugin WebView network capture has no connection timeouts and reads full matched responses

**Conclusion:** The plugin WebView network-capture interceptor opens its own `HttpURLConnection` without setting connect/read timeouts and reads the entire response body before truncating to `safeMaxBodyBytes()`. A slow or large matched response can block WebView resource loading threads or allocate much more memory than the configured capture limit.

**Evidence:** `feature-plugin/src/main/java/com/x500x/cursimple/feature/plugin/PluginWebSessionScreen.kt:1050-1058` intercepts WebView requests; `feature-plugin/src/main/java/com/x500x/cursimple/feature/plugin/PluginWebSessionScreen.kt:1455-1474` opens the connection and calls `stream?.use { it.readBytes() }` before applying `takeBytes(spec.safeMaxBodyBytes())`.

**Impact:** A plugin capture rule or hostile/slow school endpoint can hang page loading and consume memory despite a max body setting. This can make sync sessions appear frozen.

**Suggested verification:** Add tests around the capture fetch helper with a slow local endpoint and an oversized response; assert bounded timeout behavior and bounded reads.

### 15. Component market list keys are not unique for multi-ABI or multi-version entries

**Conclusion:** The component market model supports multiple entries with the same `id` but different `version`, `abi`, or `downloadUrl`, while the Compose `LazyColumn` uses only `id` as the key. Duplicate IDs can crash rendering with duplicate key errors.

**Evidence:** `core-plugin/src/main/java/com/x500x/cursimple/core/plugin/market/MarketIndex.kt:39` defines component fields beyond `id`; `feature-plugin/src/main/java/com/x500x/cursimple/feature/plugin/ComponentMarketScreen.kt:96` renders `items(uiState.knownComponents, key = { it.id })`.

**Impact:** A normal market layout that publishes separate component builds per ABI or version can make the component market screen crash or lose item state.

**Suggested verification:** Add a component market UI/state test with two entries sharing `id` but differing in `abi` or `version`; rendering should use a composite stable key such as `id:version:abi:downloadUrl`.

### 16. Schedule widget reminder badges miss label-based reminders from timing-profile slot labels

**Conclusion:** The schedule widget decides whether a course has a label-based reminder by comparing the rule's label action to `course.slotLabelOverride` only. The core reminder evaluator and main schedule UI both resolve labels through `course.reminderSlotLabel(timingProfile)`, which falls back to the timing profile slot label when no per-course override exists.

**Evidence:** `feature-widget/src/main/java/com/x500x/cursimple/feature/widget/ScheduleWidgetDataSource.kt:198-211` passes `timingProfile` into `toRow(...)` but the `LabelRule` branch compares `it.slotLabel == course.slotLabelOverride`; the canonical helper is defined at `core-kernel/src/main/java/com/x500x/cursimple/core/kernel/model/ScheduleTimingModels.kt:40-42`, used by `core-reminder/src/main/java/com/x500x/cursimple/core/reminder/LabelReminderRuleEvaluator.kt:103` and `feature-schedule/src/main/java/com/x500x/cursimple/feature/schedule/ScheduleScreen.kt:4286-4290`.

**Impact:** Courses that have reminders through standard slot labels can appear unmarked in the widget, while the main schedule screen and actual reminder planning consider them covered.

**Suggested verification:** Add a widget data-source test with a timing profile slot label, a course without `slotLabelOverride`, and a matching `LabelRule`; assert `ScheduleWidgetCourseRow.hasReminder` is true.

### 17. Reminder DataStore decoding is not resilient to corrupted or incompatible JSON

**Conclusion:** `DataStoreReminderRepository` decodes reminder rules, custom occupancies, and system alarm records directly with `json.decodeFromString(...)` in both flows and write helpers. Unlike several other repositories, it does not isolate bad persisted JSON with `runCatching`, so one corrupted key can break reading and also block save/delete methods that decode the old value before writing a replacement.

**Evidence:** `core-data/src/main/java/com/x500x/cursimple/core/data/reminder/DataStoreReminderRepository.kt:31-45` decodes all reminder flows directly; `core-data/src/main/java/com/x500x/cursimple/core/data/reminder/DataStoreReminderRepository.kt:173-189` uses the same direct decode helpers before writes.

**Impact:** A malformed backup restore, schema-incompatible old value, or DataStore corruption can make the reminder screen, reminder widgets, and alarm synchronization fail instead of degrading to an empty/remediable state.

**Suggested verification:** Add repository tests that seed invalid JSON into `reminder_rules`, `custom_occupancies`, and `system_alarm_records`; flows should emit empty lists or quarantined data, and `saveReminderRule` / `saveSystemAlarmRecord` should be able to repair the store.

### 18. Backup restore can release persisted URI permissions that are still referenced after restore

**Conclusion:** User and widget preference restore paths read the old persisted image/ringtone URI, restore the snapshot, then unconditionally release the old URI permission. If the restored snapshot still references the same URI, the app keeps the URI string but loses access to it.

**Evidence:** `core-data/src/main/java/com/x500x/cursimple/core/data/widget/DataStoreWidgetPreferencesRepository.kt:182-188` unconditionally releases `previousImageUri`; `core-data/src/main/java/com/x500x/cursimple/core/data/DataStoreUserPreferencesRepository.kt:515-527` does the same for schedule background and alarm ringtone URIs. Normal setters compare old and new values before release, for example `core-data/src/main/java/com/x500x/cursimple/core/data/widget/DataStoreWidgetPreferencesRepository.kt:140-147` and `core-data/src/main/java/com/x500x/cursimple/core/data/DataStoreUserPreferencesRepository.kt:225-334`.

**Impact:** Restoring a same-device backup can leave schedule backgrounds, widget backgrounds, or custom alarm ringtones broken after process restart because the saved URI no longer has a persisted read grant.

**Suggested verification:** Add tests with an injectable permission releaser or fake content resolver: restoring a snapshot with the same URI should not release it; restoring a different URI should release only the old, no-longer-referenced URI.

### 19. Deleting a non-active term can mirror the wrong term start date into user preferences

**Conclusion:** `TermProfileViewModel.delete(...)` deletes the requested term, then guesses the next active term from the pre-delete state by taking the first term whose id differs from the deleted id. The repository only changes `activeTermId` when the deleted term was active, so deleting a non-active term can still mirror another term's start date into `UserPreferences.termStartDate`.

**Evidence:** `app/src/main/java/com/x500x/cursimple/app/TermProfileViewModel.kt:73-79` picks `state.value.terms.firstOrNull { it.id != id }` after deletion; `core-data/src/main/java/com/x500x/cursimple/core/data/term/DataStoreTermProfileRepository.kt:79-86` changes active id only when the deleted id was active. `feature-widget/src/main/java/com/x500x/cursimple/feature/widget/NextCourseDataSource.kt:68` uses `userPrefs.termStartDate`, while `feature-widget/src/main/java/com/x500x/cursimple/feature/widget/ScheduleWidgetDataSource.kt:70-178` prefers the actual active term start date.

**Impact:** After deleting a non-active term, the legacy mirrored term start date can point at the wrong term. Next-course widget filtering and settings that depend on `UserPreferences.termStartDate` can disagree with the daily schedule widget and active term repository.

**Suggested verification:** Add view-model/repository tests with three terms where active is the second term; deleting the third term should leave the mirrored user preference start date unchanged and both widgets should resolve the same week.

### 20. Android Auto Backup can include plaintext WebDAV and AI credentials

**Conclusion:** The app enables Android system backup but does not declare backup extraction rules in the manifest. `user_preferences` DataStore contains WebDAV password and AI API key values, so these secrets can be included in system cloud/device backup unless explicitly excluded or moved.

**Evidence:** `app/src/main/AndroidManifest.xml:28` sets `android:allowBackup="true"` and there is no `android:fullBackupContent` or `android:dataExtractionRules` in the opened manifest; `core-data/src/main/java/com/x500x/cursimple/core/data/DataStoreUserPreferencesRepository.kt:100-102` reads `webDavPassword` and `aiImportApiKey`, and `core-data/src/main/java/com/x500x/cursimple/core/data/DataStoreUserPreferencesRepository.kt:428-449` writes them.

**Impact:** Sensitive API keys and WebDAV credentials can leave the app sandbox through OS backup/restore channels without a dedicated user-controlled encrypted export flow.

**Suggested verification:** Add a manifest test that asserts backup rules exist and exclude `datastore/user_preferences.preferences_pb` or any credential-specific storage file; add a data-layer migration test if credentials are moved to a separate excluded store.
