package com.novelai.assistant.ui.discover

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SfCatalogScreen(
    bookId: String,
    onBack: () -> Unit,
    onRead: (Int) -> Unit,
    viewModel: SfBookDetailViewModel = hiltViewModel()
) {
    LaunchedEffect(bookId) { viewModel.selectLocal(bookId) }
    val book by viewModel.book.collectAsStateWithLifecycle()
    val chapters by viewModel.chapters.collectAsStateWithLifecycle()
    val updates by viewModel.updates.collectAsStateWithLifecycle()
    val busy by viewModel.directoryBusy.collectAsStateWithLifecycle()
    val error by viewModel.directoryError.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("章节目录") },
                navigationIcon = {
                    IconButton(onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, "返回")
                    }
                },
                actions = {
                    if (bookId.startsWith("sf:")) {
                        IconButton({ viewModel.refreshDirectory(bookId) }, enabled = !busy) {
                            Icon(Icons.Rounded.Refresh, "刷新目录")
                        }
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding)) {
            item {
                Text(
                    book?.title.orEmpty(),
                    Modifier.padding(20.dp),
                    style = MaterialTheme.typography.titleLarge
                )
            }
            updates[bookId]?.let { update ->
                item {
                    Column(
                        Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            update.label(),
                            style = MaterialTheme.typography.bodySmall,
                            color = if (update.error != null) MaterialTheme.colorScheme.error
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (update.added.isNotEmpty()) {
                            TextButton({ viewModel.downloadUpdates(bookId) }) {
                                Text("下载新增章节")
                            }
                        }
                    }
                }
            }
            error?.let {
                item {
                    Text(it, Modifier.padding(20.dp), color = MaterialTheme.colorScheme.error)
                }
            }
            if (busy) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
            items(chapters, key = { it.id }) { ch ->
                ListItem(
                    headlineContent = { Text("${ch.chapterIndex + 1}. ${ch.title}") },
                    supportingContent = {
                        Text(
                            when {
                                ch.downloaded -> "已下载"
                                ch.content.isNotBlank() -> "已缓存"
                                ch.isPaid -> "VIP · 读取时检查已购权限"
                                else -> "免费"
                            }
                        )
                    },
                    modifier = Modifier.clickable { onRead(ch.chapterIndex) }
                )
            }
        }
    }
}
