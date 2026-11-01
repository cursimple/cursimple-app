package com.x500x.cursimple.app

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.x500x.cursimple.core.data.memo.DataStoreMemoRepository
import com.x500x.cursimple.core.kernel.model.MemoNote
import com.x500x.cursimple.feature.widget.WidgetCatalog
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test

/**
 * Opt-in (`-e pinQa memo|next`) manual launcher check: seeds six to-dos and asks the launcher to
 * pin the widget, then waits so the confirmation can be accepted and the widget scrolled by hand.
 */
class LauncherWidgetPinQaTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun pinWidgetForManualScrolling() {
        val target = InstrumentationRegistry.getArguments().getString("pinQa")
        assumeTrue(target != null)
        val context = compose.activity
        runBlocking {
            val repo = DataStoreMemoRepository(context)
            repo.notesFlow.first().forEach { repo.remove(it.id) }
            (1..6).forEach { repo.upsert(MemoNote("pin-qa-$it", title = "桌面滚动待办 $it", body = "第 $it 条")) }
        }
        val entry = WidgetCatalog.pickerEntries(context).first { it.id == target }
        compose.runOnUiThread { WidgetCatalog.requestPin(context, entry) }
        Thread.sleep(20_000)
    }
}
