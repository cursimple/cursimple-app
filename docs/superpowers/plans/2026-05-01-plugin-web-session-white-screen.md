# Plugin Web Session White Screen Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Fix the built-in plugin Web login white screen after unified authentication and add draggable horizontal/vertical WebView scrolling.

**Architecture:** Keep the workflow engine unchanged and harden the Web session boundary. `PluginWebSessionScreen` owns WebView settings, navigation diagnostics, scroll affordances, and robust packet capture; the bundled Yangtze EAMS manifest owns the allowed host list.

**Tech Stack:** Android Kotlin, Jetpack Compose, Android WebView, Gradle Android plugin, JUnit 4.

---

## Chunk 1: Web Session Hardening

### File Structure

- Modify: `feature-plugin/src/main/java/com/kebiao/viewer/feature/plugin/PluginWebSessionScreen.kt`
  - Add WebView scroll/viewport settings.
  - Track current URL, blocked URL, and page error text in Compose state.
  - Replace silent host blocking with visible diagnostics.
  - Make JavaScript packet capture resilient to storage/DOM access failures.
  - Add small internal helper functions for JavaScript payload decoding and URL host extraction.
- Create: `feature-plugin/src/test/java/com/kebiao/viewer/feature/plugin/PluginWebSessionScreenTest.kt`
  - Cover allowed-host matching for exact hosts and subdomains.
  - Cover malformed JavaScript payload decoding.
- Modify: `app/src/main/assets/plugin-dev/yangtzeu-eams-v2/manifest.json`
  - Add observed/likely unified-authentication and ATrust proxy hosts while preserving exact host/subdomain enforcement.

### Task 1: Add focused helper tests

- [ ] **Step 1: Create the test file**

Create `feature-plugin/src/test/java/com/kebiao/viewer/feature/plugin/PluginWebSessionScreenTest.kt`:

```kotlin
package com.kebiao.viewer.feature.plugin

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PluginWebSessionScreenTest {
    @Test
    fun `allowed host accepts exact host and subdomain`() {
        val allowed = listOf("example.edu.cn")

        assertTrue(isAllowedHost("https://example.edu.cn/login", allowed))
        assertTrue(isAllowedHost("https://cas.example.edu.cn/login", allowed))
        assertFalse(isAllowedHost("https://evil-example.edu.cn/login", allowed))
    }

    @Test
    fun `javascript payload decoding tolerates blank or malformed values`() {
        assertEquals("", decodeJavascriptPayload(null).optString("html"))
        assertEquals("", decodeJavascriptPayload("not-json").optString("html"))
    }
}
```

- [ ] **Step 2: Run the tests and verify the new helper visibility failures**

Run:

```powershell
.\gradlew.bat :feature-plugin:testDebugUnitTest
```

Expected: FAIL because helper functions are currently private and malformed JSON is not tolerated.

### Task 2: Harden WebView navigation and capture

- [ ] **Step 1: Update imports and Compose state**

Modify `PluginWebSessionScreen.kt` to import:

```kotlin
import android.view.View
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.ui.text.style.TextOverflow
```

Add state:

```kotlin
val blockedUrl = remember { mutableStateOf<String?>(null) }
val pageError = remember { mutableStateOf<String?>(null) }
```

- [ ] **Step 2: Add visible diagnostics above the WebView**

Show current URL, blocked URL, and load error using small `bodySmall` text. Keep it compact so it does not consume the login page.

- [ ] **Step 3: Enable draggable scrolling and viewport behavior**

Inside WebView factory:

```kotlin
settings.javaScriptEnabled = true
settings.domStorageEnabled = true
settings.useWideViewPort = true
settings.loadWithOverviewMode = true
settings.builtInZoomControls = true
settings.displayZoomControls = false
settings.mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
isVerticalScrollBarEnabled = true
isHorizontalScrollBarEnabled = true
scrollBarStyle = View.SCROLLBARS_INSIDE_OVERLAY
overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
```

- [ ] **Step 4: Replace silent navigation blocking**

When `shouldOverrideUrlLoading` sees a non-allowed host, set `blockedUrl.value = target` and return `true`. When a URL is allowed, clear `blockedUrl` and `pageError`, then continue loading.

- [ ] **Step 5: Capture page load failures**

Override modern `onReceivedError(view, request, error)` and only report main-frame errors:

```kotlin
if (request?.isForMainFrame == true) {
    pageError.value = "${error?.errorCode ?: 0}: ${error?.description.orEmpty()}"
    currentUrl.value = request.url?.toString().orEmpty()
}
```

- [ ] **Step 6: Make packet JavaScript safe**

Wrap storage, selector, and HTML access in JavaScript helpers:

```javascript
const readStorage = (storage) => {
  const snapshot = {};
  try {
    for (let i = 0; i < storage.length; i++) {
      const key = storage.key(i);
      if (key) snapshot[key] = storage.getItem(key) || "";
    }
  } catch (error) {}
  return snapshot;
};
```

The returned object must always be stringified.

- [ ] **Step 7: Make Kotlin payload decoding safe**

Change `decodeJavascriptPayload` from `private` to `internal` and return an empty `JSONObject` on parse failure.

- [ ] **Step 8: Run focused tests**

Run:

```powershell
.\gradlew.bat :feature-plugin:testDebugUnitTest
```

Expected: PASS.

### Task 3: Expand bundled plugin allowed hosts

- [ ] **Step 1: Update Yangtze EAMS manifest**

Modify `app/src/main/assets/plugin-dev/yangtzeu-eams-v2/manifest.json` allowed hosts to include:

```json
"cas-yangtzeu-edu-cn.atrust.yangtzeu.edu.cn",
"authserver.yangtzeu.edu.cn",
"cas.yangtzeu.edu.cn",
"ids.yangtzeu.edu.cn",
"atrust.yangtzeu.edu.cn",
"vpn.yangtzeu.edu.cn",
"jwc3-yangtzeu-edu-cn-s.atrust.yangtzeu.edu.cn",
"jwc3.yangtzeu.edu.cn"
```

- [ ] **Step 2: Preserve existing workflow completion condition**

Do not change `completionUrlContains`; it should still target `jwc3-yangtzeu-edu-cn-s.atrust.yangtzeu.edu.cn`.

### Task 4: Compile and verify

- [ ] **Step 1: Run feature tests**

Run:

```powershell
.\gradlew.bat :feature-plugin:testDebugUnitTest
```

Expected: PASS.

- [ ] **Step 2: Compile the debug app**

Run:

```powershell
.\gradlew.bat :app:assembleDebug
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Inspect git diff**

Run:

```powershell
git diff -- feature-plugin/src/main/java/com/kebiao/viewer/feature/plugin/PluginWebSessionScreen.kt app/src/main/assets/plugin-dev/yangtzeu-eams-v2/manifest.json feature-plugin/src/test/java/com/kebiao/viewer/feature/plugin/PluginWebSessionScreenTest.kt
```

Expected: Diff only contains Web session hardening, manifest host additions, and focused tests.
