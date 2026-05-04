package com.kebiao.viewer.feature.widget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.glance.action.Action
import androidx.glance.appwidget.action.actionSendBroadcast
import com.kebiao.viewer.core.data.widget.DataStoreWidgetPreferencesRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class ScheduleWidgetActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            try {
                handleAction(context.applicationContext, intent.getStringExtra(EXTRA_ACTION))
            } finally {
                pendingResult.finish()
            }
        }
    }

    private suspend fun handleAction(context: Context, action: String?) {
        val repository = DataStoreWidgetPreferencesRepository(context)
        when (action) {
            ACTION_PREV -> repository.shiftWidgetDayOffset(-1)
            ACTION_NEXT -> repository.shiftWidgetDayOffset(1)
            ACTION_RESET -> repository.setWidgetDayOffset(0)
            else -> return
        }
        ScheduleWidgetUpdater.refreshSchedule(context)
    }

    companion object {
        const val ACTION_PREV = "prev"
        const val ACTION_NEXT = "next"
        const val ACTION_RESET = "reset"

        private const val EXTRA_ACTION = "schedule_widget_action"

        fun action(context: Context, action: String): Action {
            val intent = Intent(context, ScheduleWidgetActionReceiver::class.java).apply {
                setPackage(context.packageName)
                putExtra(EXTRA_ACTION, action)
            }
            return actionSendBroadcast(intent)
        }
    }
}
