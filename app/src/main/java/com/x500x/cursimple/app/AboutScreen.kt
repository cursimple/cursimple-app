package com.x500x.cursimple.app

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.CloudSync
import androidx.compose.material.icons.rounded.EventBusy
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Storefront
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material.icons.rounded.VolumeOff
import androidx.compose.material.icons.rounded.Widgets
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.x500x.cursimple.BuildConfig
import com.x500x.cursimple.R

private const val GITHUB_URL = "https://github.com/cursimple/cursimple-app"
private const val ISSUES_URL = "$GITHUB_URL/issues"
private const val RELEASES_URL = "$GITHUB_URL/releases"
private const val PLUGIN_REGISTRY_URL = "https://github.com/cursimple/cursimple-plugins"
private const val DEV_MODE_TAP_TARGET = 5
private const val DEV_MODE_TAP_RESET_MS = 3000L

@Composable
fun AboutScreen(
    developerModeEnabled: Boolean,
    onSetDeveloperMode: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val versionName = remember(context) { resolveVersionName(context) }

    var tapCount by rememberSaveable { mutableIntStateOf(0) }
    var lastTapMs by rememberSaveable { mutableLongStateOf(0L) }

    LaunchedEffect(developerModeEnabled) {
        if (developerModeEnabled) {
            tapCount = 0
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            HeroCard(
                versionName = versionName,
                developerModeEnabled = developerModeEnabled,
                onVersionTap = {
                    if (developerModeEnabled) {
                        return@HeroCard
                    }
                    val now = System.currentTimeMillis()
                    if (now - lastTapMs > DEV_MODE_TAP_RESET_MS) {
                        tapCount = 0
                    }
                    lastTapMs = now
                    tapCount += 1
                    if (tapCount >= DEV_MODE_TAP_TARGET) {
                        onSetDeveloperMode(true)
                        tapCount = 0
                    }
                },
            )

            FeatureCard()

            LinkCard(
                onOpenRepo = { openUrl(context, GITHUB_URL) },
                onOpenIssues = { openUrl(context, ISSUES_URL) },
                onOpenReleases = { openUrl(context, RELEASES_URL) },
                onOpenPlugins = { openUrl(context, PLUGIN_REGISTRY_URL) },
            )

            RuntimeCard(versionName = versionName)

            TechStackCard()

            Text(
                text = stringResource(R.string.about_license_line),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
            )
        }
    }
}

@Composable
private fun AboutCard(content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            content()
        }
    }
}

@Composable
private fun CardTitle(icon: ImageVector, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp),
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun HeroCard(
    versionName: String,
    developerModeEnabled: Boolean,
    onVersionTap: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // 自适应图标本身是 XML，取其前景位图铺在同色底上
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0xFFFFFFFF)),
                    contentAlignment = Alignment.Center,
                ) {
                    Image(
                        painter = painterResource(R.mipmap.ic_launcher_foreground),
                        contentDescription = null,
                        modifier = Modifier.size(56.dp),
                    )
                }
                Spacer(modifier = Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.main_app_name),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                    Text(
                        text = stringResource(R.string.about_app_alt_name),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            }

            Text(
                text = stringResource(R.string.about_tagline),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )

            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .clickable(onClick = onVersionTap),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                HeroChip(text = "v$versionName")
                HeroChip(text = stringResource(releaseChannelLabel()))
                if (developerModeEnabled) {
                    HeroChip(text = stringResource(R.string.about_developer_badge))
                }
            }

            if (developerModeEnabled) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Rounded.VerifiedUser,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.about_dev_mode_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            }
        }
    }
}

@Composable
private fun HeroChip(text: String) {
    Surface(
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun FeatureCard() {
    val features = listOf(
        Triple(
            Icons.Rounded.Extension,
            stringResource(R.string.about_feature_plugin_title),
            stringResource(R.string.about_feature_plugin_desc),
        ),
        Triple(
            Icons.Rounded.NotificationsActive,
            stringResource(R.string.about_feature_reminder_title),
            stringResource(R.string.about_feature_reminder_desc),
        ),
        Triple(
            Icons.Rounded.Widgets,
            stringResource(R.string.about_feature_widget_title),
            stringResource(R.string.about_feature_widget_desc),
        ),
        Triple(
            Icons.Rounded.VolumeOff,
            stringResource(R.string.about_feature_silence_title),
            stringResource(R.string.about_feature_silence_desc),
        ),
        Triple(
            Icons.Rounded.CloudSync,
            stringResource(R.string.about_feature_sync_title),
            stringResource(R.string.about_feature_sync_desc),
        ),
        Triple(
            Icons.Rounded.EventBusy,
            stringResource(R.string.about_feature_calendar_title),
            stringResource(R.string.about_feature_calendar_desc),
        ),
    )
    AboutCard {
        CardTitle(Icons.Rounded.Layers, stringResource(R.string.about_features_title))
        features.forEach { (icon, title, desc) ->
            Row(modifier = Modifier.fillMaxWidth()) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = desc,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun LinkCard(
    onOpenRepo: () -> Unit,
    onOpenIssues: () -> Unit,
    onOpenReleases: () -> Unit,
    onOpenPlugins: () -> Unit,
) {
    AboutCard {
        CardTitle(Icons.Rounded.Code, stringResource(R.string.about_links_title))
        Text(
            text = stringResource(R.string.about_project_desc),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        LinkRow(
            icon = Icons.Rounded.Code,
            title = stringResource(R.string.about_link_repo),
            subtitle = GITHUB_URL.removePrefix("https://"),
            onClick = onOpenRepo,
        )
        LinkRow(
            icon = Icons.Rounded.BugReport,
            title = stringResource(R.string.about_link_issues),
            subtitle = stringResource(R.string.about_link_issues_desc),
            onClick = onOpenIssues,
        )
        LinkRow(
            icon = Icons.Rounded.History,
            title = stringResource(R.string.about_link_releases),
            subtitle = stringResource(R.string.about_link_releases_desc),
            onClick = onOpenReleases,
        )
        LinkRow(
            icon = Icons.Rounded.Storefront,
            title = stringResource(R.string.about_link_plugins),
            subtitle = stringResource(R.string.about_link_plugins_desc),
            onClick = onOpenPlugins,
        )
    }
}

@Composable
private fun RuntimeCard(versionName: String) {
    val items = listOf(
        stringResource(R.string.about_version_label) to "v$versionName",
        stringResource(R.string.about_runtime_version_code) to BuildConfig.VERSION_CODE.toString(),
        stringResource(R.string.about_runtime_channel) to stringResource(releaseChannelLabel()),
        stringResource(R.string.about_runtime_android) to "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
        stringResource(R.string.about_runtime_abi) to (Build.SUPPORTED_ABIS?.firstOrNull().orEmpty().ifBlank { "-" }),
        stringResource(R.string.about_runtime_sdk) to "minSdk 24 · targetSdk 36",
    )
    AboutCard {
        CardTitle(Icons.Rounded.VerifiedUser, stringResource(R.string.about_runtime_title))
        items.forEach { (label, value) ->
            KeyValueRow(label = label, value = value)
        }
    }
}

@Composable
private fun TechStackCard() {
    val items = listOf(
        "Kotlin" to "2.2.21",
        "Jetpack Compose" to "BOM 2026.04.01",
        "Material 3" to "Compose M3",
        "AndroidX Glance" to stringResource(R.string.about_tech_glance),
        "WorkManager" to stringResource(R.string.about_tech_workmanager),
        "DataStore" to stringResource(R.string.about_tech_datastore),
        "Kotlinx Coroutines" to "1.10.2",
        "Kotlinx Serialization" to "1.11.0",
        "Android WebView" to stringResource(R.string.about_tech_webview),
        "OkHttp" to stringResource(R.string.about_tech_okhttp),
    )
    AboutCard {
        CardTitle(Icons.Rounded.Layers, stringResource(R.string.about_tech_title))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        items.forEach { (name, version) ->
            KeyValueRow(label = name, value = version)
        }
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = "ABI v7a / v8a / x86 / x86_64 / universal",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun KeyValueRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun LinkRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick),
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(14.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

/** 版本名带连字符后缀的是测试通道构建。 */
private fun releaseChannelLabel(): Int = if (BuildConfig.VERSION_NAME.contains('-')) {
    R.string.about_channel_beta
} else {
    R.string.about_channel_stable
}

private fun resolveVersionName(context: Context): String {
    return runCatching {
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        info.versionName.orEmpty().ifBlank { "0.0.0" }
    }.getOrDefault("0.0.0")
}

private fun openUrl(context: Context, url: String) {
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    runCatching { context.startActivity(intent) }
        .onFailure {
            if (it is ActivityNotFoundException) {
                Toast.makeText(context, context.getString(R.string.about_no_link_handler), Toast.LENGTH_SHORT).show()
            }
        }
}
