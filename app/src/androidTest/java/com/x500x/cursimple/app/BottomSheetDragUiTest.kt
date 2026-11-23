package com.x500x.cursimple.app

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.dp
import com.x500x.cursimple.app.theme.ClassScheduleTheme
import com.x500x.cursimple.core.data.ThemeMode
import com.x500x.cursimple.feature.plugin.ui.AppModalBottomSheet
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalMaterial3Api::class)
class BottomSheetDragUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun fullHeightSheetKeepsItsTopAnchorAndSupportsDragToDismiss() {
        var visible by mutableStateOf(true)
        compose.setContent {
            ClassScheduleTheme(ThemeMode.Light) {
                if (visible) {
                    AppModalBottomSheet(
                        onDismissRequest = { visible = false },
                        modifier = Modifier.testTag("sheet"),
                        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
                        dragHandle = {
                            Box(Modifier.fillMaxWidth().height(48.dp).testTag("handle"))
                        },
                    ) {
                        Box(Modifier.fillMaxWidth().fillMaxHeight().testTag("body").verticalScroll(rememberScrollState())) {
                            Text("Content", Modifier.height(1600.dp).testTag("content"))
                        }
                    }
                }
            }
        }
        val handle = compose.onNodeWithTag("handle", useUnmergedTree = true).assertIsDisplayed()
        val top = handle.fetchSemanticsNode().positionOnScreen.y

        handle.performTouchInput { down(center) }
        try {
            repeat(4) {
                handle.performTouchInput { moveBy(Offset(0f, -8.dp.toPx()), delayMillis = 32) }
                assertEquals(top, handle.fetchSemanticsNode().positionOnScreen.y, 2f)
            }
        } finally {
            handle.performTouchInput { up() }
        }

        val body = compose.onNodeWithTag("body")
        val initialContentTop = compose.onNodeWithTag("content").fetchSemanticsNode().positionOnScreen.y
        body.performTouchInput { swipeUp() }
        val scrolledContentTop = compose.onNodeWithTag("content").fetchSemanticsNode().positionOnScreen.y
        check(scrolledContentTop < initialContentTop)
        assertEquals(top, handle.fetchSemanticsNode().positionOnScreen.y, 2f)

        handle.performTouchInput { swipeDown(startY = center.y, endY = center.y + 250.dp.toPx()) }
        compose.onNodeWithTag("sheet").assertDoesNotExist()
    }
}
