package com.x500x.cursimple.feature.plugin

import com.x500x.cursimple.feature.plugin.ui.AppOutlinedButton
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.x500x.cursimple.core.plugin.install.InstalledPluginRecord
import com.x500x.cursimple.core.plugin.market.github.GitHubRepoSummary
import com.x500x.cursimple.core.plugin.web.WebSessionPacket
import com.x500x.cursimple.core.plugin.web.WebSessionRequest

/**
 * 「从教务系统导课」的引导页。
 *
 * 插件市场是按仓库列的，新用户并不知道自己学校对应哪个仓库；这里把入口收成
 * 一件事：搜学校名 → 装上匹配的插件 → 就地登录导课，装完的插件默认就是打开的。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SchoolImportRoute(
    pluginMarketViewModel: PluginMarketViewModel,
    pluginRegistryRepo: String,
    syncingPluginId: String?,
    syncStatusMessage: String?,
    pendingWebSession: WebSessionRequest?,
    onSyncPlugin: (String) -> Unit,
    onCompleteWebSession: (WebSessionPacket) -> Unit,
    onCancelWebSession: () -> Unit,
    onBack: () -> Unit,
    onBrowseAllPlugins: () -> Unit,
    onAddCourseManually: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val uiState by pluginMarketViewModel.uiState.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf("") }

    LaunchedEffect(pluginRegistryRepo) {
        if (pluginRegistryRepo.isNotBlank()) {
            pluginMarketViewModel.refreshIfStale(pluginRegistryRepo, MARKET_CACHE_TTL_MILLIS)
        }
        // 已装插件单独现查最新版，有新版在列表里直接标出来
        pluginMarketViewModel.refreshInstalledPluginVersions()
    }

    // 网页登录浮层开着时，返回键该退出登录流程回到搜索页，
    // 而不是一路退回课表把整个导课流程丢掉。
    BackHandler(enabled = pendingWebSession != null) { onCancelWebSession() }

    val matched = remember(uiState.marketRepos, query) {
        filterMarketRepos(uiState.marketRepos, query)
    }
    // 插件清单在本地缓存 24 小时，新收录的学校在缓存过期前一直搜不到，
    // 用户还以为是自己学校没人做。搜不到时先自动拉一次最新清单，每次进页面只补拉一次。
    var refreshedForMiss by remember { mutableStateOf(false) }
    LaunchedEffect(query, uiState.marketRepos, uiState.isLoading) {
        if (query.isBlank() || matched.isNotEmpty() || uiState.isLoading || refreshedForMiss) return@LaunchedEffect
        if (pluginRegistryRepo.isBlank()) return@LaunchedEffect
        refreshedForMiss = true
        pluginMarketViewModel.loadRegistry(pluginRegistryRepo)
    }
    // 装自哪个仓库记在安装记录里，据此判断这一条是不是已经装好了
    val installedByRepo = remember(uiState.installedPlugins) {
        uiState.installedPlugins
            .filter { !it.sourceRepo.isNullOrBlank() }
            .associateBy { it.sourceRepo.orEmpty() }
    }

    Box(modifier = modifier.fillMaxSize()) {
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.school_import_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(R.string.school_import_back),
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = { pluginMarketViewModel.loadRegistry(pluginRegistryRepo) },
                        enabled = !uiState.isLoading,
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Refresh,
                            contentDescription = stringResource(R.string.school_import_refresh),
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.background)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            SchoolImportSteps()

            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text(stringResource(R.string.school_import_search_label)) },
                placeholder = { Text(stringResource(R.string.school_import_search_hint)) },
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
            )

            // 只留有信息量的状态：加载中、失败要让用户看见，
            // 「已加载 N 个插件」说的是清单总数，紧挨着下面的「搜到 M 个」像在自相矛盾，
            // 而且下面那行已经把总数说清楚了。
            uiState.status
                ?.takeUnless { it is PluginMarketStatus.MarketLoaded }
                ?.let { status ->
                    Text(
                        text = context.pluginMarketStatusText(status),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

            if (uiState.marketRepos.isNotEmpty()) {
                Text(
                    text = if (query.isBlank()) {
                        pluralStringResource(R.plurals.school_import_catalog_count, uiState.marketRepos.size, uiState.marketRepos.size)
                    } else {
                        // 一句话把「清单里有几个」和「搜中几个」都交代了，不再分两行各说各的
                        pluralStringResource(
                            R.plurals.school_import_match_count,
                            uiState.marketRepos.size,
                            uiState.marketRepos.size,
                            matched.size,
                        )
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            when {
                uiState.marketRepos.isEmpty() && uiState.isLoading -> SchoolImportBusy()

                uiState.marketRepos.isEmpty() -> SchoolImportEmpty(
                    text = stringResource(R.string.school_import_market_empty),
                    actionText = stringResource(R.string.school_import_retry),
                    onAction = { pluginMarketViewModel.loadRegistry(pluginRegistryRepo) },
                )

                else -> LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    // 学校真没插件时，「浏览全部」帮不上忙，得告诉用户还能怎么把课表弄进来
                    if (matched.isEmpty()) {
                        item(key = "no-match") {
                            SchoolImportEmpty(
                                text = stringResource(R.string.school_import_no_match, query.trim()),
                                hint = stringResource(R.string.school_import_no_match_hint),
                                actionText = stringResource(R.string.school_import_add_manually),
                                onAction = onAddCourseManually,
                                secondaryActionText = stringResource(R.string.school_import_refresh_list),
                                onSecondaryAction = { pluginMarketViewModel.loadRegistry(pluginRegistryRepo) },
                            )
                        }
                        // 没搜中不等于没得装：清单里的插件照样列出来，
                        // 别让人对着空屏以为一个插件都没有
                        item(key = "catalog-header") {
                            Text(
                                text = pluralStringResource(
                                    R.plurals.school_import_catalog_header,
                                    uiState.marketRepos.size,
                                    uiState.marketRepos.size,
                                ),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 8.dp),
                            )
                        }
                    }
                    items(matched.ifEmpty { uiState.marketRepos }, key = { it.fullName }) { repo ->
                        val installed = installedByRepo[repo.fullName]
                        SchoolPluginRow(
                            repo = repo,
                            installed = installed,
                            upgrade = installed?.let { availableUpgrade(it, uiState) },
                            busy = uiState.isLoading,
                            checking = installed != null && uiState.checkingUpdateKey == installed.installKey,
                            syncingPluginId = syncingPluginId,
                            onInstall = { pluginMarketViewModel.installFromGitHub(repo) },
                            // 导课前先查新版，有新版就先升级
                            onSync = { record -> pluginMarketViewModel.syncWithUpdateCheck(record) },
                            onUpgrade = { record, latest -> pluginMarketViewModel.upgradeThenSync(record, latest) },
                        )
                    }
                    item(key = "browse-all") {
                        AppOutlinedButton(
                            onClick = onBrowseAllPlugins,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(stringResource(R.string.school_import_browse_all))
                        }
                    }
                }
            }

            syncStatusMessage?.takeIf { it.isNotBlank() }?.let { message ->
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
            }
        }
    }

    PluginUpgradeGate(uiState = uiState, viewModel = pluginMarketViewModel, onSyncPlugin = onSyncPlugin)

    uiState.installPreview?.let { preview ->
        InstallPreviewDialog(
            preview = preview,
            origin = uiState.installPreviewOrigin,
            isLoading = uiState.isLoading,
            onDismiss = pluginMarketViewModel::dismissInstallPreview,
            onConfirm = pluginMarketViewModel::confirmInstall,
        )
    }

    // 登录教务系统的网页会话就在本页弹出，装完插件不必再绕去插件页。
    // 放在铺满的 Box 里，尺寸才和插件页那边一致。
    pendingWebSession?.let { request ->
        WebSessionOverlay(
            request = request,
            onFinish = onCompleteWebSession,
            onCancel = onCancelWebSession,
        )
    }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SchoolImportSteps() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
    ) {
        FlowRow(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            itemVerticalAlignment = Alignment.CenterVertically,
        ) {
            listOf(
                stringResource(R.string.school_import_step_search),
                stringResource(R.string.school_import_step_install),
                stringResource(R.string.school_import_step_login),
            ).forEachIndexed { index, step ->
                if (index > 0) {
                    Text(
                        text = "›",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    text = "${index + 1}. $step",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun SchoolPluginRow(
    repo: GitHubRepoSummary,
    installed: InstalledPluginRecord?,
    /** 市场上已知有新版时是那个仓库（带着最新 release），否则为 null。 */
    upgrade: GitHubRepoSummary?,
    busy: Boolean,
    checking: Boolean,
    syncingPluginId: String?,
    onInstall: () -> Unit,
    onSync: (InstalledPluginRecord) -> Unit,
    onUpgrade: (InstalledPluginRecord, GitHubRepoSummary) -> Unit,
) {
    val syncing = installed != null &&
        (syncingPluginId == installed.installKey || syncingPluginId == installed.pluginId)
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                imageVector = Icons.Rounded.School,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(22.dp),
            )
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    // 标题给学校全称：这一页的人是在找自己学校，不是在找仓库
                    text = repo.schoolDisplayTitle(),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    maxLines = 2,
                )
                // 副标题给仓库简介：标题已经是学校名了，这里该说的是这个插件本身
                if (repo.description.isNotBlank()) {
                    Text(
                        text = repo.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                    )
                }
                if (installed != null) {
                    val newVersion = upgrade?.latestRelease?.tagName
                    Text(
                        text = if (newVersion != null) {
                            stringResource(
                                R.string.plugin_upgrade_available_line,
                                displayVersion(newVersion),
                                displayVersion(installed.version),
                            )
                        } else {
                            stringResource(R.string.school_import_installed_enabled)
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = if (newVersion != null) {
                            MaterialTheme.colorScheme.tertiary
                        } else {
                            MaterialTheme.colorScheme.primary
                        },
                    )
                }
            }
            when {
                syncing || checking -> CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)

                // 已知有新版：按钮直接换成升级，升级装好后自动接着导课
                installed != null && upgrade != null -> Button(
                    onClick = { onUpgrade(installed, upgrade) },
                    enabled = !busy,
                ) {
                    Text(stringResource(R.string.plugin_upgrade_action), maxLines = 1)
                }

                installed != null -> Button(
                    onClick = { onSync(installed) },
                    enabled = !busy,
                ) {
                    Text(stringResource(R.string.school_import_action_sync), maxLines = 1)
                }

                else -> AppOutlinedButton(onClick = onInstall, enabled = !busy) {
                    Icon(
                        imageVector = Icons.Rounded.CloudDownload,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.size(6.dp))
                    Text(stringResource(R.string.school_import_action_install), maxLines = 1)
                }
            }
        }
    }
}

@Composable
private fun SchoolImportBusy() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 40.dp),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator()
    }
}

@Composable
private fun SchoolImportEmpty(
    text: String,
    actionText: String,
    onAction: () -> Unit,
    hint: String? = null,
    secondaryActionText: String? = null,
    onSecondaryAction: (() -> Unit)? = null,
    tertiaryActionText: String? = null,
    onTertiaryAction: (() -> Unit)? = null,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
        hint?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
        Button(onClick = onAction) { Text(actionText) }
        if (secondaryActionText != null && onSecondaryAction != null) {
            AppOutlinedButton(onClick = onSecondaryAction) { Text(secondaryActionText) }
        }
        if (tertiaryActionText != null && onTertiaryAction != null) {
            AppOutlinedButton(onClick = onTertiaryAction) { Text(tertiaryActionText) }
        }
    }
}
