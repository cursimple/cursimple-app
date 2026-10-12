package com.x500x.cursimple.app

import androidx.compose.foundation.layout.width
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.x500x.cursimple.app.theme.ClassScheduleTheme
import com.x500x.cursimple.core.data.ThemeMode
import com.x500x.cursimple.core.data.memo.memoTodoRevision
import com.x500x.cursimple.core.kernel.model.MemoNote
import kotlinx.coroutines.CompletableDeferred
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** In-memory callbacks verify that desktop confirmation alone never writes a note. */
class MemoCompletionUiQaTest {
    @get:Rule val compose = createComposeRule()
    private val note = MemoNote("confirm", title = "报告", body = "- [ ] 上传报告")

    @Test fun cancellingDesktopConfirmationNeverCallsCompletion() {
        var saves = 0
        var closes = 0
        compose.setContent {
            ClassScheduleTheme(ThemeMode.Dark) {
                CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, 1.3f)) {
                    Surface(Modifier.width(288.dp)) {
                        MemoTodoCompletionWindow(note, 0, memoTodoRevision(note.body),
                            onClose = { closes++ }, onComplete = { saves++; true })
                    }
                }
            }
        }
        compose.onNodeWithTag("memo-complete-confirmation").assertIsDisplayed()
        compose.onNodeWithTag("memo-quick-editor").assertDoesNotExist()
        compose.runOnIdle { assertEquals(0, saves) }
        compose.onNodeWithTag("memo-complete-cancel").assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(0, saves); assertEquals(1, closes) }
    }

    @Test fun confirmingCompletesOnceAndDisablesRepeatedClicksWhileSaving() {
        var saves = 0
        var closes = 0
        val result = CompletableDeferred<Boolean>()
        compose.setContent {
            ClassScheduleTheme(ThemeMode.Light) {
                Surface(Modifier.width(288.dp)) {
                    MemoTodoCompletionWindow(note, 0, memoTodoRevision(note.body),
                        onClose = { closes++ }, onComplete = { saves++; result.await() })
                }
            }
        }
        compose.runOnIdle { assertEquals(0, saves) }
        compose.onNodeWithTag("memo-complete-confirm").performClick()
        compose.onNodeWithTag("memo-complete-confirm").assertIsNotEnabled().performClick()
        compose.onNodeWithTag("memo-complete-cancel").assertIsNotEnabled()
        compose.runOnIdle { assertEquals(1, saves); assertEquals(0, closes); result.complete(true) }
        compose.waitUntil(5000) { closes == 1 }
        compose.runOnIdle { assertEquals(1, saves) }
    }

    @Test fun staleDesktopChecklistCannotBeConfirmed() {
        var saves = 0
        compose.setContent {
            ClassScheduleTheme(ThemeMode.Light) {
                MemoTodoCompletionWindow(note.copy(body = "- [ ] 新任务"), 0, memoTodoRevision(note.body),
                    onClose = {}, onComplete = { saves++; true })
            }
        }
        compose.onNodeWithTag("memo-complete-confirm").assertIsNotEnabled()
        compose.onNodeWithTag("memo-complete-error").assertIsDisplayed()
        compose.runOnIdle { assertEquals(0, saves) }
    }
}
