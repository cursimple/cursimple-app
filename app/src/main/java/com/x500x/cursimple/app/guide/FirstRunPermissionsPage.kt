package com.x500x.cursimple.app.guide

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.x500x.cursimple.R
import com.x500x.cursimple.app.PermissionsSection
import com.x500x.cursimple.core.data.ClassNoticePreferences

@Composable
internal fun FirstRunPermissionsPage(
    classNotice: ClassNoticePreferences,
    alarmKeepAliveEnabled: Boolean,
    onAlarmKeepAliveEnabledChange: (Boolean) -> Unit,
    onContinue: () -> Unit,
) {
    FirstRunPageDialog(onDismiss = onContinue) {
        Box(Modifier.fillMaxSize().safeDrawingPadding(), contentAlignment = Alignment.TopCenter) {
            Column(Modifier.widthIn(max = 560.dp).fillMaxSize().testTag("first-run-permissions")) {
                Text(
                    stringResource(R.string.settings_dest_permissions),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                )
                Column(
                    modifier = Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    PermissionsSection(
                        classNotice = classNotice,
                        alarmKeepAliveEnabled = alarmKeepAliveEnabled,
                        onAlarmKeepAliveEnabledChange = onAlarmKeepAliveEnabledChange,
                        showDiagnostics = false,
                    )
                }
                Button(
                    onClick = onContinue,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).heightIn(min = 48.dp),
                ) {
                    Text(stringResource(R.string.guide_continue_to_demo))
                }
            }
        }
    }
}
