package com.novelai.assistant.ui.discover

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.novelai.assistant.ui.components.AdaptiveCenteredContainer
import com.novelai.assistant.ui.components.GlassCapsuleButton
import com.novelai.assistant.ui.components.GlassCircleButton
import com.novelai.assistant.ui.components.GlassSectionCard
import com.novelai.assistant.ui.components.LargeTitleHeader
import com.novelai.assistant.ui.components.LocalAdaptiveMetrics
import com.novelai.assistant.ui.theme.NovelColors

@Composable
fun SfExploreScreen(
    onBack: () -> Unit,
    onOpenBookDetail: (String) -> Unit,
    viewModel: SfExploreViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val related = viewModel.sourceBookId != null
    val adaptive = LocalAdaptiveMetrics.current
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val primaryText = if (isDark) NovelColors.GlassOnDarkText else NovelColors.GlassOnLightText
    val secondaryText = MaterialTheme.colorScheme.onSurfaceVariant
    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    Scaffold(
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { _ ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
        ) {
            AdaptiveCenteredContainer(
                maxWidth = adaptive.maxContentWidth,
                horizontalPadding = adaptive.horizontalPadding
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    GlassCircleButton(
                        icon = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = "返回",
                        onClick = onBack
                    )
                    LargeTitleHeader(
                        title = if (related) "相关书单" else "新书推荐",
                        subtitle = if (related) {
                            "收录这部作品的读者书单"
                        } else {
                            "来自菠萝包平台的新书推荐"
                        },
                        horizontalPadding = 0.dp,
                        modifier = Modifier.weight(1f)
                    ) {
                        GlassCircleButton(
                            icon = Icons.Rounded.Refresh,
                            contentDescription = "刷新",
                            onClick = { if (!state.loading) viewModel.load(refresh = true) },
                            tint = if (state.loading) secondaryText.copy(alpha = 0.45f) else primaryText
                        )
                    }
                }
            }

            BoxWithConstraints(modifier = Modifier.weight(1f).fillMaxWidth()) {
                val extraSide = ((maxWidth - adaptive.maxContentWidth) / 2).coerceAtLeast(0.dp)
                val horizontalPad = adaptive.horizontalPadding + extraSide
                val columns = adaptive.listGridColumns

                LazyVerticalGrid(
                    columns = GridCells.Fixed(columns),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = horizontalPad,
                        end = horizontalPad,
                        top = 8.dp,
                        bottom = navBottom + 24.dp
                    ),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(state.books, key = { "book:${it.id}" }) { book ->
                        SfSearchResultCard(
                            item = book,
                            onClick = { onOpenBookDetail("sf:${book.id}") }
                        )
                    }

                    items(
                        items = state.lists,
                        key = { "list:${it.id}" },
                        span = { GridItemSpan(maxLineSpan) }
                    ) { list ->
                        var expanded by rememberSaveable(list.id) { mutableStateOf(false) }
                        GlassSectionCard(contentPadding = PaddingValues(16.dp)) {
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text(
                                    text = list.title,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = primaryText
                                )
                                Text(
                                    text = listOfNotNull(
                                        list.author.takeIf { it.isNotBlank() },
                                        "共 ${list.totalBooks} 部作品"
                                    ).joinToString(" · "),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = secondaryText
                                )
                                if (list.summary.isNotBlank()) {
                                    Text(
                                        text = list.summary,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = secondaryText
                                    )
                                }
                                GlassCapsuleButton(
                                    text = if (expanded) "收起预览" else "预览 ${list.books.size} 部作品",
                                    onClick = { expanded = !expanded },
                                    filled = false,
                                    enabled = list.books.isNotEmpty()
                                )
                                if (expanded) {
                                    val chunked = list.books.chunked(columns)
                                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                        chunked.forEach { rowItems ->
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                                            ) {
                                                rowItems.forEach { book ->
                                                    SfSearchResultCard(
                                                        item = book,
                                                        onClick = { onOpenBookDetail("sf:${book.id}") },
                                                        modifier = Modifier.weight(1f)
                                                    )
                                                }
                                                repeat(columns - rowItems.size) {
                                                    Box(Modifier.weight(1f))
                                                }
                                            }
                                        }
                                    }
                                    if (list.totalBooks > list.books.size) {
                                        Text(
                                            text = "接口仅提供这 ${list.books.size} 部预览作品，未展示完整书单。",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = secondaryText
                                        )
                                    }
                                }
                            }
                        }
                    }

                    if (state.loading) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            LinearProgressIndicator(Modifier.fillMaxWidth())
                        }
                    }

                    state.error?.let { error ->
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            GlassSectionCard(contentPadding = PaddingValues(16.dp)) {
                                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Text(
                                        text = error,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                    GlassCapsuleButton(
                                        text = "重试",
                                        onClick = { viewModel.load(refresh = state.page == 0) },
                                        filled = false
                                    )
                                }
                            }
                        }
                    }

                    if (!state.loading && state.error == null) {
                        if (state.books.isEmpty() && state.lists.isEmpty()) {
                            item(span = { GridItemSpan(maxLineSpan) }) {
                                GlassSectionCard(contentPadding = PaddingValues(20.dp)) {
                                    Text(
                                        text = if (related) "这部作品暂无相关书单。" else "平台暂未提供新书推荐。",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = secondaryText
                                    )
                                }
                            }
                        } else if (state.hasMore) {
                            item(span = { GridItemSpan(maxLineSpan) }) {
                                Box(
                                    modifier = Modifier.fillMaxWidth(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    GlassCapsuleButton(
                                        text = "加载更多",
                                        onClick = { viewModel.load() },
                                        filled = false
                                    )
                                }
                            }
                        } else {
                            item(span = { GridItemSpan(maxLineSpan) }) {
                                Text(
                                    text = "已展示平台返回的全部结果",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = secondaryText,
                                    modifier = Modifier.padding(vertical = 8.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
