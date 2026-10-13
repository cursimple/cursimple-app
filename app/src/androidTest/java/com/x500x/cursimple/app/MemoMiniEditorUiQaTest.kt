package com.x500x.cursimple.app

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Surface
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.x500x.cursimple.app.theme.ClassScheduleTheme
import com.x500x.cursimple.R
import com.x500x.cursimple.core.data.ThemeMode
import com.x500x.cursimple.core.data.memo.MemoTodoEditDraft
import com.x500x.cursimple.core.data.memo.MemoTodoEditResult
import com.x500x.cursimple.core.kernel.model.MemoNote
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test

/** Opt-in native mini-editor QA; save callbacks stay in memory and touch no user notes. */
class MemoMiniEditorUiQaTest {
    @get:Rule val compose = createComposeRule()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private fun text(id: Int) = instrumentation.targetContext.getString(id)
    private val note = MemoNote("mini", title = "Homework", body = "- [ ] First\n- [ ] Second\n1. Bring notes")

    @Test fun historyLivesInTheHeaderAndAllToolsRemainReachableOnNarrowScreens() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("memoMiniQa") == "true")
        compose.setContent {
            ClassScheduleTheme(themeMode = ThemeMode.Light) {
                CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, 1.3f)) {
                    Surface(Modifier.width(288.dp)) {
                        MemoTodoQuickEditor(note, null, false, emptyList(), emptyMap(), onClose = {},
                            onSave = { MemoTodoEditResult.Saved })
                    }
                }
            }
        }
        compose.onNodeWithTag("memo-quick-edit").performClick()
        compose.onNodeWithTag("memo-quick-body-1").performScrollTo().performTextReplacement("Changed")
        compose.onNodeWithTag("memo-tool-undo").assertIsDisplayed().performClick()
        compose.onNodeWithTag("memo-quick-body-1").performScrollTo().assertTextContains("Second", substring = true)
        compose.onAllNodes(hasTestTag("memo-tool-undo") and hasAnyAncestor(hasTestTag("memo-edit-tools")))
            .assertCountEquals(0)
        compose.onNodeWithTag("memo-tool-image").assertIsDisplayed()
        compose.onNodeWithTag("memo-tools-more-end").assertIsDisplayed()
        compose.onNodeWithTag("memo-tool-clear").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("memo-edit-tools").performTouchInput { swipeLeft() }
        compose.onNodeWithTag("memo-tools-more-end").assertDoesNotExist()
        compose.onNodeWithTag("memo-tools-more-start").assertIsDisplayed()
        compose.onNodeWithTag("memo-tool-divider").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("memo-tool-link").performScrollTo().performClick()
        compose.onNode(hasText(text(com.x500x.cursimple.feature.schedule.R.string.schedule_action_cancel)) and hasAnyAncestor(isDialog()))
            .performClick()
        compose.onNodeWithTag("memo-quick-save").assertIsDisplayed()
    }

    @Test fun switchingPanelsKeepsWindowBoundsAndDraftEvenWhenInterrupted() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("memoMiniQa") == "true")
        compose.setContent {
            ClassScheduleTheme(themeMode = ThemeMode.Dark) {
                CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, 1.3f)) {
                    Surface(Modifier.width(288.dp)) {
                        MemoTodoQuickEditor(note, null, false, emptyList(), emptyMap(), onClose = {},
                            onSave = { MemoTodoEditResult.Saved })
                    }
                }
            }
        }
        compose.onNodeWithTag("memo-quick-edit").performClick()
        compose.onNodeWithTag("memo-quick-body-1").performScrollTo().performTextReplacement("Kept draft")
        val bounds = compose.onNodeWithTag("memo-quick-editor").fetchSemanticsNode().boundsInRoot
        compose.mainClock.autoAdvance = false
        try {
            repeat(3) {
                compose.onNodeWithTag("memo-quick-more").performClick()
                compose.mainClock.advanceTimeBy(80)
                assertEquals(bounds, compose.onNodeWithTag("memo-quick-editor").fetchSemanticsNode().boundsInRoot)
                compose.onNodeWithTag("memo-quick-back").performClick()
                compose.mainClock.advanceTimeBy(80)
                assertEquals(bounds, compose.onNodeWithTag("memo-quick-editor").fetchSemanticsNode().boundsInRoot)
            }
            compose.mainClock.advanceTimeBy(320)
        } finally { compose.mainClock.autoAdvance = true }
        compose.onNodeWithTag("memo-quick-body-1").performScrollTo().assertTextContains("Kept draft", substring = true)
        compose.onNodeWithTag("memo-quick-save").assertIsDisplayed()
    }

    @Test fun settingsRemainReachableAfterFocusChangesAndKeepRichEdits() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("memoMiniQa") == "true")
        var saved: MemoTodoEditDraft? = null
        var closed = false
        compose.setContent {
            ClassScheduleTheme(themeMode = ThemeMode.Light) {
                CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, 1.3f)) {
                    Surface(Modifier.width(288.dp)) {
                        MemoTodoQuickEditor(note, null, false, emptyList(), emptyMap(), onClose = { closed = true },
                            onSave = { saved = it; MemoTodoEditResult.Saved })
                    }
                }
            }
        }
        compose.onNodeWithTag("memo-quick-edit").performClick()
        compose.onNodeWithTag("memo-quick-body-check").performScrollTo().performClick()
        compose.onNodeWithTag("memo-complete-confirmation").assertIsDisplayed()
        compose.onNode(hasText(text(com.x500x.cursimple.feature.schedule.R.string.schedule_action_cancel)) and hasClickAction() and hasAnyAncestor(isDialog())).performClick()
        compose.onNodeWithTag("memo-quick-body-check").assertIsOff().performClick()
        compose.onNode(hasText(text(com.x500x.cursimple.feature.schedule.R.string.memo_complete_confirm_action)) and hasClickAction() and hasAnyAncestor(isDialog())).performClick()
        compose.onNodeWithTag("memo-quick-body-check").assertIsOn()
        compose.onNodeWithTag("memo-quick-body-1").performScrollTo().performTextReplacement("Second item")
        repeat(2) {
            compose.onNodeWithTag("memo-quick-body-1").performScrollTo().performClick()
            compose.onNodeWithTag("memo-quick-more").performClick()
            compose.onNodeWithText(text(R.string.memo_quick_priority)).assertIsDisplayed()
            compose.onNodeWithTag("memo-quick-back").performClick()
            compose.onNodeWithTag("memo-quick-body-1").performScrollTo().assertTextContains("Second item", substring = true)
        }
        compose.onNodeWithTag("memo-quick-save").performClick()
        compose.onNode(hasText(text(R.string.memo_quick_cancel)) and hasAnyAncestor(isDialog())).performClick()
        compose.runOnIdle { assertNull(saved); assertFalse(closed) }
        compose.onNodeWithTag("memo-quick-save").performClick()
        compose.onNode(hasText(text(R.string.memo_quick_save)) and hasAnyAncestor(isDialog())).performClick()
        compose.waitUntil(5000) { closed }
        compose.runOnIdle {
            assertEquals("Homework", saved?.text)
            assertEquals("- [x] First\n- [ ] Second item\n1. Bring notes", saved?.body)
        }
    }

    private fun showEditor(isNew: Boolean = false) = compose.setContent {
        ClassScheduleTheme(themeMode = ThemeMode.Light) {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, 1.3f)) {
                Surface(Modifier.width(288.dp)) {
                    MemoTodoQuickEditor(note, null, isNew, emptyList(), emptyMap(), onClose = {},
                        onSave = { MemoTodoEditResult.Saved })
                }
            }
        }
    }

    @Test fun anExistingNoteOpensForReadingUntilEditIsTapped() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("memoMiniQa") == "true")
        showEditor()
        compose.onNodeWithText("Homework").assertIsDisplayed()
        compose.onNodeWithText("Bring notes", substring = true).assertExists()
        compose.onNodeWithTag("memo-quick-text").assertDoesNotExist()
        compose.onNodeWithTag("memo-quick-toolbar").assertDoesNotExist()
        compose.onNodeWithTag("memo-quick-save").assertDoesNotExist()
        val bounds = compose.onNodeWithTag("memo-quick-editor").fetchSemanticsNode().boundsInRoot
        compose.onNodeWithTag("memo-quick-edit").assertIsDisplayed().performClick()
        compose.onNodeWithTag("memo-quick-text").assertIsDisplayed()
        compose.onNodeWithTag("memo-quick-toolbar").assertExists()
        compose.onNodeWithTag("memo-quick-edit").assertDoesNotExist()
        assertEquals("阅读和编辑不应改变窗口大小", bounds, compose.onNodeWithTag("memo-quick-editor").fetchSemanticsNode().boundsInRoot)
    }

    @Test fun aNewNoteOpensStraightIntoEditing() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("memoMiniQa") == "true")
        showEditor(isNew = true)
        compose.onNodeWithTag("memo-quick-text").assertIsDisplayed()
        compose.onNodeWithTag("memo-quick-edit").assertDoesNotExist()
    }

    @Test fun editorToolsExplainThemselvesOnLongPress() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("memoMiniQa") == "true")
        showEditor(isNew = true)
        val bold = text(com.x500x.cursimple.feature.schedule.R.string.memo_tool_bold)
        compose.onNodeWithText(bold).assertDoesNotExist()
        compose.onNodeWithTag("memo-tool-bold").performScrollTo().performTouchInput { longClick() }
        compose.waitUntil(3000) { compose.onAllNodesWithText(bold).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText(bold).assertIsDisplayed()
        val undo = text(com.x500x.cursimple.feature.schedule.R.string.memo_tool_undo)
        compose.onNodeWithTag("memo-tool-undo").performTouchInput { longClick() }
        compose.waitUntil(3000) { compose.onAllNodesWithText(undo).fetchSemanticsNodes().isNotEmpty() }
    }
}
