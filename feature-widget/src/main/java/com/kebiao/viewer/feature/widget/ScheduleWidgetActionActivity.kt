package com.kebiao.viewer.feature.widget

import android.app.Activity
import android.os.Bundle
import androidx.glance.action.Action
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.actionStartActivity
import androidx.glance.appwidget.AppWidgetId
import com.kebiao.viewer.core.data.widget.DataStoreWidgetPreferencesRepository
import kotlinx.coroutines.runBlocking

class ScheduleWidgetActionActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        runBlocking {
            handleWidgetAction()
        }
        finish()
    }

    private suspend fun handleWidgetAction() {
        val app = applicationContext
        val repository = DataStoreWidgetPreferencesRepository(app)
        val action = intent.getStringExtra(EXTRA_ACTION)
        val glanceId = intent.data
            ?.getQueryParameter(QUERY_APP_WIDGET_ID)
            ?.toIntOrNull()
            ?.let(::AppWidgetId)
        when (action) {
            ACTION_PREV -> repository.shiftWidgetDayOffset(-1)
            ACTION_NEXT -> repository.shiftWidgetDayOffset(1)
            ACTION_RESET -> repository.setWidgetDayOffset(0)
            else -> return
        }
        ScheduleWidgetUpdater.refreshSchedule(app, glanceId)
    }

    companion object {
        const val ACTION_PREV = "prev"
        const val ACTION_NEXT = "next"
        const val ACTION_RESET = "reset"

        private const val EXTRA_ACTION = "schedule_widget_action"
        private const val QUERY_APP_WIDGET_ID = "appWidgetId"
        private val ActionKey = ActionParameters.Key<String>(EXTRA_ACTION)

        fun action(action: String): Action =
            actionStartActivity<ScheduleWidgetActionActivity>(
                actionParametersOf(ActionKey.to(action)),
            )
    }
}
