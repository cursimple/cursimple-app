package com.x500x.cursimple.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.EventNote
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.x500x.cursimple.R
import com.x500x.cursimple.feature.plugin.ui.AppToolbarIconButton

internal const val SIDEBAR_EXTENSION_LIMIT = 3

/** Drawer keeps a few components; the open one stays visible even when it sorts past the limit. */
internal fun sidebarExtensionPreview(
    entries: List<Pair<String, String>>,
    current: String?,
    limit: Int = SIDEBAR_EXTENSION_LIMIT,
): List<Pair<String, String>> {
    if (entries.size <= limit) return entries
    val head = entries.take(limit)
    val open = entries.drop(limit).firstOrNull { it.first == current } ?: return head
    return head.take(limit - 1) + open
}

/** Every sidebar component, for when the drawer only has room for a few. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AllExtensionsScreen(
    entries: List<Pair<String, String>>,
    current: String?,
    onOpen: (String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.all_extensions_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                },
                navigationIcon = {
                    AppToolbarIconButton(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.settings_back), onClick = onBack)
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        if (entries.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text(
                    stringResource(R.string.all_extensions_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            return@Scaffold
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            extensionEntryItems(entries, current, onOpen)
        }
    }
}

/** Component rows shared by the all-components page and the bottom bar's components hub. */
internal fun LazyListScope.extensionEntryItems(
    entries: List<Pair<String, String>>,
    current: String?,
    onOpen: (String) -> Unit,
) {
    items(entries, key = { it.first }) { (key, title) ->
        val selected = key == current
        val colors = MaterialTheme.colorScheme
        Surface(
            onClick = { onOpen(key) },
            shape = RoundedCornerShape(16.dp),
            color = if (selected) colors.primaryContainer else colors.surfaceVariant,
            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
        ) {
            Row(Modifier.padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.AutoMirrored.Rounded.EventNote, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(12.dp))
                Text(
                    title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                    contentDescription = null,
                    tint = colors.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}
