# Plugin Logging Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add detailed, privacy-safe plugin diagnostics to the existing logcat-based log export flow.

**Architecture:** Introduce a centralized `PluginLogger` in `core-plugin`, then route plugin manager, installer, workflow, Web session, and schedule sync diagnostics through it. Keep the existing `LogExporter` model unchanged so exported logs naturally include the new plugin events.

**Tech Stack:** Android Kotlin, Android `Log`, Jetpack Compose WebView interop, Kotlin serialization, OkHttp, JUnit 4.

---

## Chunk 1: Central Logger

### File Structure

- Create: `core-plugin/src/main/java/com/kebiao/viewer/core/plugin/logging/PluginLogger.kt`
  - Own event logging helpers.
  - Own URL query redaction.
  - Own sha256 helpers.
  - Own count/length/hash field conventions.
- Create: `core-plugin/src/test/java/com/kebiao/viewer/core/plugin/logging/PluginLoggerTest.kt`
  - Verify sensitive query values are redacted.
  - Verify non-sensitive URL values survive.
  - Verify sha256 output is stable.

### Task 1: Write logger tests

- [ ] **Step 1: Add focused tests**

Create `PluginLoggerTest.kt`:

```kotlin
package com.kebiao.viewer.core.plugin.logging

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PluginLoggerTest {
    @Test
    fun `sanitizeUrl redacts sensitive query values`() {
        val sanitized = PluginLogger.sanitizeUrl(
            "https://example.edu/login?ticket=abc&username=20260001&token=secret",
        )

        assertTrue(sanitized.contains("ticket=***"))
        assertTrue(sanitized.contains("username=20260001"))
        assertTrue(sanitized.contains("token=***"))
        assertFalse(sanitized.contains("secret"))
    }

    @Test
    fun `sha256 is stable`() {
        assertEquals(
            "2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824",
            PluginLogger.sha256("hello"),
        )
    }
}
```

- [ ] **Step 2: Run tests and verify failure**

Run:

```powershell
.\gradlew.bat :core-plugin:testDebugUnitTest
```

Expected: FAIL because `PluginLogger` does not exist yet.

### Task 2: Implement `PluginLogger`

- [ ] **Step 1: Create logger object**

Implement:

```kotlin
object PluginLogger {
    fun info(event: String, fields: Map<String, Any?> = emptyMap())
    fun warn(event: String, fields: Map<String, Any?> = emptyMap(), error: Throwable? = null)
    fun error(event: String, fields: Map<String, Any?> = emptyMap(), error: Throwable? = null)
    fun sanitizeUrl(url: String): String
    fun sha256(value: String): String
}
```

- [ ] **Step 2: Add safe field rendering**

Rules:

- Drop null fields.
- Render maps as sorted `key=value`.
- Replace whitespace in values with a single space.
- Cap long values at a conservative length.
- Never throw from logging helpers.

- [ ] **Step 3: Run focused tests**

Run:

```powershell
.\gradlew.bat :core-plugin:testDebugUnitTest
```

Expected: PASS.

---

## Chunk 2: Core Plugin Flow Logging

### File Structure

- Modify: `core-plugin/src/main/java/com/kebiao/viewer/core/plugin/PluginManager.kt`
  - Add manager, market, sync start/resume, load schema/timing, bundled initialization, and removal logs.
- Modify: `core-plugin/src/main/java/com/kebiao/viewer/core/plugin/install/PluginInstaller.kt`
  - Add package preview, install, verification, and bundled install logs.
- Modify: `core-plugin/src/main/java/com/kebiao/viewer/core/plugin/runtime/WorkflowEngine.kt`
  - Add workflow lifecycle, step lifecycle, HTTP summary, WebSession request, and detailed failure logs.

### Task 3: Add `PluginManager` logs

- [ ] **Step 1: Log bundled plugin initialization**

Record start, install, update, skip, and failure with `assetRoot`, `pluginId`, `version`, `versionCode`, `elapsedMs`.

- [ ] **Step 2: Log plugin removal**

Record start, success, and failure with `pluginId`, `storagePathPresent`, `elapsedMs`.

- [ ] **Step 3: Log market operations**

Record fetch/download start, success, and failure with sanitized URL, byte count, elapsedMs, error details.

- [ ] **Step 4: Log sync start and resume outcomes**

Record plugin ID, result type, elapsedMs, course count, message count, recommendation count, web session id, failure message.

### Task 4: Add `PluginInstaller` logs

- [ ] **Step 1: Log preview**

Record source, bytes, pluginId, version, checksumVerified, signatureVerified, elapsedMs.

- [ ] **Step 2: Log install**

Record source, bytes, pluginId, version, versionCode, storage path presence, elapsedMs.

- [ ] **Step 3: Log install failures**

Record source, bytes, elapsedMs, error type, message, and stack trace.

### Task 5: Add `WorkflowEngine` logs

- [ ] **Step 1: Log workflow lifecycle**

Record start/resume with pluginId, workflowId, token prefix only, nextStepIndex, step count.

- [ ] **Step 2: Log every step**

Wrap each step in timing. Record step start, success, and failure with pluginId, workflowId, stepId, stepType, stepIndex, elapsedMs.

- [ ] **Step 3: Log HTTP summaries**

Record method, sanitized URL, statusCode, elapsedMs, requestHeaderCount, responseHeaderCount, responseLength, responseSha256. Do not log body.

- [ ] **Step 4: Log WebSession request summaries**

Record sessionId, title presence, startUrl sanitized, allowedHostCount, captureSelectorCount, storage/html extraction flags.

- [ ] **Step 5: Preserve behavior**

Existing workflow result behavior must remain unchanged.

---

## Chunk 3: UI Boundary Logging

### File Structure

- Modify: `feature-plugin/src/main/java/com/kebiao/viewer/feature/plugin/PluginWebSessionScreen.kt`
  - Add WebView diagnostics logs.
  - Add packet capture summary logs.
- Modify: `feature-schedule/src/main/java/com/kebiao/viewer/feature/schedule/ScheduleViewModel.kt`
  - Add user-triggered sync/result/failure logs.
- Modify: `app/src/main/java/com/kebiao/viewer/app/util/LogExporter.kt`
  - Improve exported log header to mention plugin diagnostics.

### Task 6: Add Web session logs

- [ ] **Step 1: Log navigation and page lifecycle**

Record page start, page finish, blocked URL, page error, HTTP error, popup creation/close, console error surfaced.

- [ ] **Step 2: Log completion**

Record manual completion and automatic completion with sessionId, finalUrl sanitized, elapsed-related state if available.

- [ ] **Step 3: Log packet capture summaries**

Record cookie count, localStorage count, sessionStorage count, capturedFields count, htmlDigest. Do not log values.

### Task 7: Add schedule sync logs

- [ ] **Step 1: Log sync request**

Record pluginId, username presence only, termId presence, baseUrl sanitized, no password.

- [ ] **Step 2: Log result handling**

Record awaiting WebSession, success summary, validation failure, persistence failure, and workflow failure.

### Task 8: Update export header**

- [ ] **Step 1: Adjust header text**

Add a line such as:

```kotlin
writer.appendLine("# includes plugin diagnostics with sensitive values redacted")
```

---

## Chunk 4: Verification

### Task 9: Run focused tests

- [ ] **Step 1: Core plugin tests**

Run:

```powershell
.\gradlew.bat :core-plugin:testDebugUnitTest
```

Expected: PASS.

- [ ] **Step 2: Feature plugin tests**

Run:

```powershell
.\gradlew.bat :feature-plugin:testDebugUnitTest
```

Expected: PASS.

### Task 10: Build app

- [ ] **Step 1: Assemble debug app**

Run:

```powershell
.\gradlew.bat :app:assembleDebug
```

Expected: BUILD SUCCESSFUL.

### Task 11: Inspect privacy-sensitive diff

- [ ] **Step 1: Search for risky raw logging**

Run:

```powershell
rg -n "password|Cookie|requestBody|responseBody|body|string\\(\\)" core-plugin feature-plugin feature-schedule app/src/main/java/com/kebiao/viewer/app/util/LogExporter.kt
```

Expected: No new logging statement writes sensitive values or HTTP bodies.
