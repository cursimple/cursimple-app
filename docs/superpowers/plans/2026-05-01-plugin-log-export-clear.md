# Plugin Log Export And Clear Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Persist plugin diagnostics into an app-owned log file, merge it into exported logs, and add a developer action to clear app-owned logs.

**Architecture:** `PluginLogger` keeps writing Android logcat and gains an optional sink interface. The app installs a file-backed sink during `Application.onCreate()`. `LogExporter` appends the plugin diagnostics file to the exported text and exposes a clear operation for cache logs.

**Tech Stack:** Android Kotlin, Android `Log`, Jetpack Compose, JUnit 4, app cache files.

---

## Chunk 1: Plugin Logger Sink

### Task 1: Extend `PluginLogger`

**Files:**
- Modify: `core-plugin/src/main/java/com/kebiao/viewer/core/plugin/logging/PluginLogger.kt`
- Modify: `core-plugin/src/test/java/com/kebiao/viewer/core/plugin/logging/PluginLoggerTest.kt`

- [ ] Add `PluginLogSink` with `write(priority, tag, message, throwableText)`.
- [ ] Add `PluginLogger.setSink(sink: PluginLogSink?)`.
- [ ] Send each rendered plugin event to the optional sink after logcat output.
- [ ] Keep all sink calls wrapped so sink failures cannot affect plugin code.
- [ ] Add tests for sink delivery, throwable text delivery, and clearing the sink.

## Chunk 2: App File Sink

### Task 2: Add file-backed sink

**Files:**
- Create: `app/src/main/java/com/kebiao/viewer/app/util/PluginFileLogSink.kt`
- Modify: `app/src/main/java/com/kebiao/viewer/app/ClassScheduleApplication.kt`

- [ ] Write plugin diagnostics to `cache/plugin-logs/plugin-diagnostics.log`.
- [ ] Append timestamped lines with priority, tag, message, and throwable text when present.
- [ ] Trim old content when the file exceeds a conservative byte limit.
- [ ] Install the sink in `ClassScheduleApplication.onCreate()` before plugin initialization.

## Chunk 3: Export And Clear

### Task 3: Merge plugin diagnostics into exported logs

**Files:**
- Modify: `app/src/main/java/com/kebiao/viewer/app/util/LogExporter.kt`
- Create: `app/src/test/java/com/kebiao/viewer/app/util/LogExporterTest.kt`

- [ ] Add helper functions for log directories and the plugin diagnostics file.
- [ ] Append plugin diagnostics as a dedicated section in exported text.
- [ ] Add `clearLogs(context)` for cache export files and plugin diagnostics.
- [ ] Add pure file tests for appending plugin diagnostics and clearing directories.

### Task 4: Add UI action

**Files:**
- Modify: `app/src/main/java/com/kebiao/viewer/app/AboutScreen.kt`

- [ ] Add a “清空日志” button to the developer card.
- [ ] Wire the button to `LogExporter.clearLogs(context)` on a coroutine.
- [ ] Show success/failure toast text.

## Chunk 4: Verification

- [ ] Run `.\gradlew.bat :core-plugin:testDebugUnitTest`.
- [ ] Run `.\gradlew.bat :app:testDebugUnitTest`.
- [ ] Run `.\gradlew.bat :app:assembleDebug`.
- [ ] Inspect git diff for raw sensitive logging.
