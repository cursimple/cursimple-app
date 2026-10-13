package com.x500x.cursimple.app

import android.content.Intent
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.x500x.cursimple.feature.widget.WidgetDeepLinks
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assume.assumeTrue
import org.junit.Test

/** Launchers animate a closing task; the popup must already be invisible when that happens. */
class WidgetPopupCloseTest {
    @Test fun closingHidesTheWindowAndDimBeforeTheTaskCloses() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("memoMiniQa") == "true")
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val intent = Intent(context, MemoTodoQuickEditActivity::class.java)
            .putExtra(WidgetDeepLinks.EXTRA_CREATE_MEMO, true)
        ActivityScenario.launch<MemoTodoQuickEditActivity>(intent).use { scenario ->
            scenario.onActivity { activity ->
                activity.finish()
                assertFalse("窗口还没隐藏就开始关闭", activity.isFinishing)
                assertEquals(0f, activity.window.attributes.alpha)
                assertEquals(0f, activity.window.attributes.dimAmount)
            }
            val deadline = System.currentTimeMillis() + 3_000
            while (scenario.state != Lifecycle.State.DESTROYED && System.currentTimeMillis() < deadline) Thread.sleep(50)
            assertEquals(Lifecycle.State.DESTROYED, scenario.state)
        }
    }
}
