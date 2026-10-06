package com.x500x.cursimple.app

import com.x500x.cursimple.feature.plugin.ui.AppConfirmationDialog
import com.x500x.cursimple.feature.plugin.ui.AppInfoBlock
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.x500x.cursimple.app.github.GitHubAccount
import com.x500x.cursimple.R
import com.x500x.cursimple.app.github.GitHubAccountStore
import com.x500x.cursimple.core.plugin.market.github.DefaultMarketSources
import com.x500x.cursimple.core.plugin.market.github.GitHubApiClient
import com.x500x.cursimple.core.plugin.market.github.GitHubRegistryRepository
import com.x500x.cursimple.core.plugin.market.github.GitHubRepoAddress
import com.x500x.cursimple.core.plugin.market.github.MarketSourceCheck
import com.x500x.cursimple.core.plugin.market.github.MarketSourceKind
import com.x500x.cursimple.feature.plugin.ui.AppOutlinedButton
import kotlinx.coroutines.delay
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MarketSourceServices(
    val registry: GitHubRegistryRepository,
    val account: GitHubAccountStore,
    /** Empty OAuth Client ID enables token login only. */
    val oauthClientId: String,
    val api: GitHubApiClient = GitHubApiClient(),
)

private typealias SourceStatus = MarketSourceCheck?

/**
 * Manage separate registry sources and GitHub account access. Each source reports its own
 * connectivity and authentication state.
 */
@Composable
internal fun MarketSourceSettings(
    pluginSources: List<String>,
    componentSources: List<String>,
    onPluginSourcesChange: (List<String>) -> Unit,
    onComponentSourcesChange: (List<String>) -> Unit,
    services: MarketSourceServices,
) {
    val account by services.account.account.collectAsState()
    val checkKey by services.account.revision.collectAsState()
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SourceListCard(
            kind = MarketSourceKind.Plugin,
            sources = pluginSources,
            onChange = onPluginSourcesChange,
            services = services,
            checkKey = checkKey,
        )
        SourceListCard(
            kind = MarketSourceKind.Component,
            sources = componentSources,
            onChange = onComponentSourcesChange,
            services = services,
            checkKey = checkKey,
        )
        GitHubAccountCard(account = account, services = services)
    }
}

@Composable
private fun SourceListCard(
    kind: MarketSourceKind,
    sources: List<String>,
    onChange: (List<String>) -> Unit,
    services: MarketSourceServices,
    checkKey: Long,
) {
    val scope = rememberCoroutineScope()
    val statuses = remember(kind) { mutableStateMapOf<String, SourceStatus>() }
    val checks = remember(kind) { mutableMapOf<String, Job>() }
    fun check(slug: String) {
        checks.remove(slug)?.cancel()
        statuses[slug] = null
        checks[slug] = scope.launch {
            statuses[slug] = services.registry.checkSource(slug, kind, preferAccount = !DefaultMarketSources.isDefault(slug))
        }
    }
    var lastCheckKey by remember { mutableStateOf<Long?>(null) }
    LaunchedEffect(sources, checkKey) {
        // Account changes invalidate every check; source changes invalidate only added entries.
        val accountChanged = lastCheckKey != checkKey
        lastCheckKey = checkKey
        checks.keys.toList().filter { it !in sources || accountChanged }.forEach { checks.remove(it)?.cancel() }
        statuses.keys.toList().filter { it !in sources }.forEach { statuses.remove(it) }
        sources.forEach { if (accountChanged || it !in statuses) check(it) }
    }
    DisposableEffect(Unit) { onDispose { checks.values.forEach(Job::cancel) } }
    var adding by rememberSaveable { mutableStateOf(false) }
    var pendingRemoval by rememberSaveable { mutableStateOf<String?>(null) }
    val default = DefaultMarketSources.of(kind)
    SettingsCardSurface {
        Text(
            stringResource(if (kind == MarketSourceKind.Plugin) R.string.market_sources_plugin_title else R.string.market_sources_component_title),
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            stringResource(R.string.market_sources_subtitle),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (sources.isEmpty()) {
            Text(
                stringResource(R.string.market_sources_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
        }
        sources.forEach { slug ->
            SourceRow(
                slug = slug,
                isDefault = slug.equals(default, ignoreCase = true),
                kind = kind,
                status = statuses[slug],
                checked = slug in statuses,
                onRecheck = { check(slug) },
                onRemove = { pendingRemoval = slug },
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AppOutlinedButton(onClick = { adding = true }) {
                Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text(stringResource(R.string.market_sources_add))
            }
            if (sources.none { it.equals(default, ignoreCase = true) }) {
                AppOutlinedButton(onClick = { onChange(listOf(default) + sources) }) {
                    Text(stringResource(R.string.market_sources_restore_default))
                }
            }
        }
    }
    if (adding) {
        AddSourceDialog(
            kind = kind,
            existing = sources,
            services = services,
            onDismiss = { adding = false },
            onAdd = { slug, status ->
                statuses[slug] = status
                onChange(sources + slug)
                adding = false
            },
        )
    }
    pendingRemoval?.let { target ->
        AppConfirmationDialog(
            title = stringResource(R.string.market_sources_remove_confirm_title),
            message = stringResource(R.string.market_sources_remove_confirm_body),
            confirmLabel = stringResource(R.string.market_sources_remove_confirm),
            cancelLabel = stringResource(R.string.settings_cancel),
            onConfirm = {
                onChange(sources.filterNot { it.equals(target, ignoreCase = true) })
                pendingRemoval = null
            },
            onDismiss = { pendingRemoval = null },
            details = {
                AppInfoBlock(
                    label = if (DefaultMarketSources.isDefault(target)) stringResource(R.string.market_source_public) else null,
                    value = target,
                )
            },
        )
    }
}

@Composable
private fun SourceRow(
    slug: String,
    isDefault: Boolean,
    kind: MarketSourceKind,
    status: SourceStatus,
    checked: Boolean,
    onRecheck: () -> Unit,
    onRemove: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Row(
            modifier = Modifier.padding(start = 12.dp, top = 8.dp, bottom = 8.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                if (isDefault) Icons.Rounded.Public else Icons.Rounded.Folder,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(22.dp),
            )
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    if (isDefault) stringResource(R.string.market_source_public) else slug,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (isDefault) {
                    Text(slug, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (checked) SourceStatusLine(status, kind)
            }
            if (checked && status == null) {
                CircularProgressIndicator(modifier = Modifier.padding(12.dp).size(18.dp), strokeWidth = 2.dp)
            } else {
                OutlinedIconButton(onClick = onRecheck, modifier = Modifier.size(40.dp)) {
                    Icon(Icons.Rounded.Refresh, contentDescription = stringResource(R.string.market_sources_recheck))
                }
            }
            Spacer(Modifier.width(4.dp))
            OutlinedIconButton(onClick = onRemove, modifier = Modifier.size(40.dp)) {
                Icon(Icons.Rounded.Delete, contentDescription = stringResource(R.string.market_sources_remove))
            }
        }
    }
}

@Composable
private fun SourceStatusLine(status: SourceStatus, kind: MarketSourceKind) {
    val (text, ok) = sourceStatusText(status, kind)
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        color = when {
            status == null -> MaterialTheme.colorScheme.onSurfaceVariant
            ok -> MaterialTheme.colorScheme.primary
            else -> MaterialTheme.colorScheme.error
        },
    )
}

@Composable
private fun sourceStatusText(status: SourceStatus, kind: MarketSourceKind): Pair<String, Boolean> = when (status) {
    null -> stringResource(R.string.market_source_status_checking) to false
    is MarketSourceCheck.Available -> {
        val count = stringResource(
            if (kind == MarketSourceKind.Plugin) R.string.market_source_status_plugins else R.string.market_source_status_components,
            status.count,
        )
        (if (status.viaAccount) stringResource(R.string.market_source_status_available_account, count) else stringResource(R.string.market_source_status_available, count)) to true
    }
    MarketSourceCheck.NotFoundOrPrivate -> stringResource(R.string.market_source_status_needs_login) to false
    MarketSourceCheck.NotFound -> stringResource(R.string.market_source_status_not_found) to false
    MarketSourceCheck.AccountExpired -> stringResource(R.string.market_source_status_account_expired) to false
    MarketSourceCheck.NothingPublished -> stringResource(R.string.market_source_status_nothing) to false
    is MarketSourceCheck.Unreachable -> stringResource(R.string.market_source_status_unreachable) to false
}

@Composable
private fun AddSourceDialog(
    kind: MarketSourceKind,
    existing: List<String>,
    services: MarketSourceServices,
    onDismiss: () -> Unit,
    onAdd: (String, MarketSourceCheck) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var input by rememberSaveable { mutableStateOf("") }
    var checkedSlug by remember { mutableStateOf<String?>(null) }
    var result by remember { mutableStateOf<MarketSourceCheck?>(null) }
    var checking by remember { mutableStateOf(false) }
    val slug = GitHubRepoAddress.parse(input)
    val duplicate = slug != null && existing.any { it.equals(slug, ignoreCase = true) }
    val checkedThis = checkedSlug != null && checkedSlug == slug
    AlertDialog(
        onDismissRequest = { if (!checking) onDismiss() },
        title = {
            Text(stringResource(if (kind == MarketSourceKind.Plugin) R.string.market_sources_add_plugin_title else R.string.market_sources_add_component_title))
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it; result = null; checkedSlug = null },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    enabled = !checking,
                    label = { Text(stringResource(R.string.market_sources_add_label)) },
                    placeholder = { Text("owner/repo") },
                )
                val hint = when {
                    input.isBlank() -> stringResource(R.string.market_sources_add_hint)
                    slug == null -> stringResource(R.string.market_sources_add_invalid)
                    duplicate -> stringResource(R.string.market_sources_add_duplicate)
                    else -> stringResource(R.string.market_sources_add_parsed, slug)
                }
                Text(
                    hint,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (input.isNotBlank() && (slug == null || duplicate)) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (checking) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.market_source_status_checking), style = MaterialTheme.typography.bodySmall)
                    }
                } else if (checkedThis) {
                    SourceStatusLine(result, kind)
                }
            }
        },
        confirmButton = {
            val failed = checkedThis && result !is MarketSourceCheck.Available
            Button(
                enabled = slug != null && !duplicate && !checking,
                onClick = {
                    val target = slug ?: return@Button
                    if (failed) {
                        // Allow adding a private source before signing in.
                        onAdd(target, result ?: MarketSourceCheck.NotFound)
                        return@Button
                    }
                    checking = true
                    scope.launch {
                        val outcome = services.registry.checkSource(target, kind, preferAccount = true)
                        checking = false
                        checkedSlug = target
                        result = outcome
                        if (outcome is MarketSourceCheck.Available) onAdd(target, outcome)
                    }
                },
            ) {
                Text(stringResource(if (failed) R.string.market_sources_add_anyway else R.string.market_sources_add_check))
            }
        },
        dismissButton = {
            AppOutlinedButton(enabled = !checking, onClick = onDismiss) { Text(stringResource(R.string.settings_cancel)) }
        },
    )
}

@Composable
private fun GitHubAccountCard(account: GitHubAccount?, services: MarketSourceServices) {
    val context = LocalContext.current
    val resources = LocalResources.current
    var pastingToken by rememberSaveable { mutableStateOf(false) }
    var deviceLogin by rememberSaveable { mutableStateOf(false) }
    SettingsCardSurface {
        Text(stringResource(R.string.github_account_title), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
        if (account == null) {
            Text(
                stringResource(R.string.github_account_logged_out),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (services.oauthClientId.isNotBlank()) {
                    Button(onClick = { deviceLogin = true }) { Text(stringResource(R.string.github_account_login_device)) }
                    AppOutlinedButton(onClick = { pastingToken = true }) { Text(stringResource(R.string.github_account_login_token)) }
                } else {
                    Button(onClick = { pastingToken = true }) { Text(stringResource(R.string.github_account_login_token)) }
                }
            }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AccountInitial(account.login)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(account.login, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                    Text(
                        stringResource(R.string.github_account_logged_in),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                AppOutlinedButton(onClick = {
                    services.account.clear()
                    Toast.makeText(context, resources.getString(R.string.github_account_logged_out_toast), Toast.LENGTH_SHORT).show()
                }) { Text(stringResource(R.string.github_account_logout)) }
            }
        }
    }
    if (pastingToken) TokenLoginDialog(services = services, onDismiss = { pastingToken = false })
    if (deviceLogin) DeviceLoginDialog(services = services, onDismiss = { deviceLogin = false })
}

@Composable
private fun AccountInitial(login: String) {
    Box(
        modifier = Modifier.size(36.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            login.firstOrNull()?.uppercase() ?: "?",
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun TokenLoginDialog(services: MarketSourceServices, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val scope = rememberCoroutineScope()
    var token by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text(stringResource(R.string.github_token_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.github_token_steps), style = MaterialTheme.typography.bodySmall)
                AppOutlinedButton(onClick = { context.openUrl(NEW_TOKEN_URL) }) {
                    Text(stringResource(R.string.github_token_open_page))
                }
                OutlinedTextField(
                    value = token,
                    onValueChange = { token = it.trim(); error = null },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text(stringResource(R.string.github_token_label)) },
                    enabled = !busy,
                    visualTransformation = PasswordVisualTransformation(),
                    isError = error != null,
                    supportingText = error?.let { { Text(it) } },
                )
            }
        },
        confirmButton = {
            Button(
                enabled = token.isNotBlank() && !busy,
                onClick = {
                    val candidate = token.trim()
                    busy = true
                    scope.launch {
                        val viewer = runCatching {
                            val user = services.registry.viewer(candidate)
                            withContext(Dispatchers.IO) { services.account.save(candidate, GitHubAccount(user.login, user.avatarUrl)) }
                            user
                        }
                        busy = false
                        viewer.onSuccess {
                            Toast.makeText(context, resources.getString(R.string.github_account_logged_in_toast, it.login), Toast.LENGTH_SHORT).show()
                            onDismiss()
                        }.onFailure { failure ->
                            if (failure is CancellationException) throw failure
                            error = resources.getString(
                                if ((failure as? GitHubApiClient.HttpError)?.code == 401) R.string.github_token_invalid else R.string.github_login_network_error,
                            )
                        }
                    }
                },
            ) { Text(stringResource(if (busy) R.string.github_login_verifying else R.string.github_token_confirm)) }
        },
        dismissButton = { AppOutlinedButton(enabled = !busy, onClick = onDismiss) { Text(stringResource(R.string.settings_cancel)) } },
    )
}

/**
 * Device authorization polls at GitHub's interval after the user confirms the displayed code.
 */
@Composable
private fun DeviceLoginDialog(services: MarketSourceServices, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val resources = LocalResources.current
    var userCode by remember { mutableStateOf<String?>(null) }
    var verifyUrl by remember { mutableStateOf("https://github.com/login/device") }
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        val code = runCatching { services.api.requestDeviceCode(services.oauthClientId, DEVICE_SCOPE) }.getOrElse {
            if (it is CancellationException) throw it
            error = resources.getString(R.string.github_login_network_error)
            return@LaunchedEffect
        }
        userCode = code.userCode
        verifyUrl = code.verificationUri
        var interval = code.interval.coerceAtLeast(5)
        val deadline = System.currentTimeMillis() + code.expiresIn * 1000L
        while (System.currentTimeMillis() < deadline) {
            delay(interval * 1000L)
            val response = runCatching { services.api.pollDeviceToken(services.oauthClientId, code.deviceCode) }
                .onFailure { if (it is CancellationException) throw it }.getOrNull() ?: continue
            val token = response.accessToken
            if (token != null) {
                val viewer = runCatching { services.registry.viewer(token) }
                    .onFailure { if (it is CancellationException) throw it }.getOrNull()
                if (viewer == null) {
                    error = resources.getString(R.string.github_login_network_error)
                    return@LaunchedEffect
                }
                val saved = runCatching {
                    withContext(Dispatchers.IO) { services.account.save(token, GitHubAccount(viewer.login, viewer.avatarUrl)) }
                }.onFailure { if (it is CancellationException) throw it }.isSuccess
                if (!saved) {
                    error = resources.getString(R.string.github_login_network_error)
                    return@LaunchedEffect
                }
                Toast.makeText(context, resources.getString(R.string.github_account_logged_in_toast, viewer.login), Toast.LENGTH_SHORT).show()
                onDismiss()
                return@LaunchedEffect
            }
            when (response.error) {
                "slow_down" -> interval = (response.interval ?: (interval + 5))
                "authorization_pending", null -> Unit
                "access_denied" -> { error = resources.getString(R.string.github_device_denied); return@LaunchedEffect }
                else -> { error = resources.getString(R.string.github_device_expired); return@LaunchedEffect }
            }
        }
        error = resources.getString(R.string.github_device_expired)
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.github_device_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                val code = userCode
                when {
                    error != null -> Text(error!!, color = MaterialTheme.colorScheme.error)
                    code == null -> Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.github_device_requesting))
                    }
                    else -> {
                        Text(stringResource(R.string.github_device_steps), style = MaterialTheme.typography.bodySmall)
                        Text(
                            code,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.github_device_waiting), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        },
        confirmButton = {
            val code = userCode
            if (code != null && error == null) {
                Button(onClick = {
                    context.copyText(code)
                    context.openUrl(verifyUrl)
                }) { Text(stringResource(R.string.github_device_open)) }
            }
        },
        dismissButton = { AppOutlinedButton(onClick = onDismiss) { Text(stringResource(R.string.settings_cancel)) } },
    )
}

@Composable
private fun SettingsCardSurface(content: @Composable () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) { content() }
    }
}

private fun Context.openUrl(url: String) {
    runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}

private fun Context.copyText(text: String) {
    getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText("GitHub", text))
}

/** Fine-grained tokens need Contents: Read-only for selected repositories. */
private const val NEW_TOKEN_URL = "https://github.com/settings/personal-access-tokens/new"

/** OAuth access to private release assets requires the repo scope. */
private const val DEVICE_SCOPE = "repo"
