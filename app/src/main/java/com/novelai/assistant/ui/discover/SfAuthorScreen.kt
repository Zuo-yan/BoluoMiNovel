package com.novelai.assistant.ui.discover

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SfAuthorScreen(
    bookId: String,
    authorName: String,
    onBack: () -> Unit,
    onOpenBook: (String) -> Unit,
    viewModel: SfViewModel = hiltViewModel()
) {
    LaunchedEffect(bookId) { viewModel.loadAuthor(bookId) }
    val portfolio by viewModel.authorPortfolio.collectAsStateWithLifecycle()
    val books by viewModel.authorNovels.collectAsStateWithLifecycle()
    val loading by viewModel.isAuthorLoading.collectAsStateWithLifecycle()
    val error by viewModel.authorError.collectAsStateWithLifecycle()
    val displayName = portfolio?.name?.ifBlank { authorName } ?: authorName

    Scaffold(topBar = {
        TopAppBar(
            title = {
                Text(
                    displayName.ifBlank { "作者作品集" },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            },
            navigationIcon = {
                IconButton(onBack) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, "返回")
                }
            }
        )
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (loading && books.isEmpty()) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
            }
            when {
                error != null -> Box(
                    Modifier.fillMaxSize().padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            error.orEmpty(),
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        TextButton({ viewModel.loadAuthor(bookId) }) { Text("重试") }
                    }
                }
                !loading && books.isEmpty() -> Box(
                    Modifier.fillMaxSize().padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text("作品集暂无公开作品", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "官方页面没有列出这位作者的作品",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item {
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                            shape = RoundedCornerShape(18.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                SfAvatar(portfolio?.avatar, displayName, Modifier.size(52.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        displayName,
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        "作品集 · ${books.size} 部",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                    if (loading) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
                    item {
                        Text(
                            "全部作品",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(top = 6.dp, bottom = 2.dp)
                        )
                    }
                    items(books, key = { it.id }) { book ->
                        SfSearchResultCard(
                            item = book,
                            onClick = { onOpenBook("sf:${book.id}") }
                        )
                    }
                    item { Box(Modifier.heightIn(min = 24.dp)) }
                }
            }
        }
    }
}
