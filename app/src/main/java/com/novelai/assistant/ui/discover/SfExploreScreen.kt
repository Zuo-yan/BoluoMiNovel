package com.novelai.assistant.ui.discover

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SfExploreScreen(
    onBack: () -> Unit, onOpenBookDetail: (String) -> Unit,
    viewModel: SfExploreViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val related = viewModel.sourceBookId != null
    Scaffold(topBar = {
        TopAppBar(title = { Text(if (related) "相关书单" else "新书推荐") },
            navigationIcon = { IconButton(onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "返回") } },
            actions = { IconButton({ viewModel.load(refresh = true) }, enabled = !state.loading) { Icon(Icons.Rounded.Refresh, "刷新") } })
    }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Text(if (related) "收录这部作品的读者书单，可展开预览并点开作品。" else "来自菠萝包平台的新书推荐，点开看看新故事。",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            items(state.books, key = { "book:${it.id}" }) { book ->
                SfSearchResultCard(book, { onOpenBookDetail("sf:${book.id}") })
            }
            items(state.lists, key = { "list:${it.id}" }) { list ->
                var expanded by rememberSaveable(list.id) { mutableStateOf(false) }
                Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLow) {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(list.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text(listOfNotNull(list.author.takeIf { it.isNotBlank() }, "共 ${list.totalBooks} 部作品").joinToString(" · "),
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (list.summary.isNotBlank()) Text(list.summary, style = MaterialTheme.typography.bodyMedium)
                        TextButton({ expanded = !expanded }, enabled = list.books.isNotEmpty()) {
                            Text(if (expanded) "收起预览" else "预览 ${list.books.size} 部作品")
                        }
                        if (expanded) {
                            list.books.forEach { book -> SfSearchResultCard(book, { onOpenBookDetail("sf:${book.id}") }) }
                            if (list.totalBooks > list.books.size) Text("接口仅提供这 ${list.books.size} 部预览作品，未展示完整书单。",
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            if (state.loading) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
            state.error?.let { error -> item {
                Surface(color = MaterialTheme.colorScheme.errorContainer, shape = MaterialTheme.shapes.medium) {
                    Column(Modifier.fillMaxWidth().padding(16.dp)) {
                        Text(error)
                        TextButton({ viewModel.load(refresh = state.page == 0) }) { Text("重试") }
                    }
                }
            } }
            if (!state.loading && state.error == null) {
                if (state.books.isEmpty() && state.lists.isEmpty()) item {
                    Text(if (related) "这部作品暂无相关书单。" else "平台暂未提供新书推荐。", style = MaterialTheme.typography.bodyMedium)
                }
                else if (state.hasMore) item { TextButton({ viewModel.load() }) { Text("加载更多") } }
                else item { Text("已展示平台返回的全部结果", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        }
    }
}
