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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.novelai.assistant.ui.components.AdaptiveCenteredContainer
import com.novelai.assistant.ui.components.GlassCapsuleButton
import com.novelai.assistant.ui.components.GlassCircleButton
import com.novelai.assistant.ui.components.GlassSectionCard
import com.novelai.assistant.ui.components.LocalAdaptiveMetrics
import com.novelai.assistant.ui.components.SfAvatar
import com.novelai.assistant.ui.theme.NovelColors

@Composable
fun SfAuthorScreen(
    bookId: String,
    authorName: String,
    onBack: () -> Unit,
    onOpenBook: (String) -> Unit,
    viewModel: SfBookDetailViewModel = hiltViewModel()
) {
    LaunchedEffect(bookId) { viewModel.loadAuthor(bookId) }
    val portfolio by viewModel.authorPortfolio.collectAsStateWithLifecycle()
    val books by viewModel.authorNovels.collectAsStateWithLifecycle()
    val loading by viewModel.isAuthorLoading.collectAsStateWithLifecycle()
    val error by viewModel.authorError.collectAsStateWithLifecycle()
    val displayName = portfolio?.name?.ifBlank { authorName } ?: authorName

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
                        .padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    GlassCircleButton(
                        icon = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = "返回",
                        onClick = onBack
                    )
                    Text(
                        text = displayName.ifBlank { "作者作品集" },
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = primaryText,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            if (loading && books.isEmpty()) {
                AdaptiveCenteredContainer(
                    maxWidth = adaptive.maxContentWidth,
                    horizontalPadding = adaptive.horizontalPadding
                ) {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                }
            }

            when {
                error != null -> Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    AdaptiveCenteredContainer(maxWidth = adaptive.maxDialogSheetWidth) {
                        GlassSectionCard(contentPadding = PaddingValues(20.dp)) {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Text(
                                    text = error.orEmpty(),
                                    color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                GlassCapsuleButton(
                                    text = "重试",
                                    onClick = { viewModel.loadAuthor(bookId) },
                                    filled = false
                                )
                            }
                        }
                    }
                }
                !loading && books.isEmpty() -> Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    AdaptiveCenteredContainer(maxWidth = adaptive.maxDialogSheetWidth) {
                        GlassSectionCard(contentPadding = PaddingValues(24.dp)) {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = "作品集暂无公开作品",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = primaryText
                                )
                                Text(
                                    text = "官方页面没有列出这位作者的作品",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = secondaryText
                                )
                            }
                        }
                    }
                }
                else -> BoxWithConstraints(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    val extraSide = ((maxWidth - adaptive.maxContentWidth) / 2).coerceAtLeast(0.dp)
                    val horizontalPad = adaptive.horizontalPadding + extraSide

                    LazyVerticalGrid(
                        columns = GridCells.Fixed(adaptive.listGridColumns),
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                            start = horizontalPad,
                            end = horizontalPad,
                            top = 6.dp,
                            bottom = navBottom + 24.dp
                        ),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            GlassSectionCard(contentPadding = PaddingValues(16.dp)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                                ) {
                                    SfAvatar(portfolio?.avatar, displayName, Modifier.size(54.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(
                                            text = displayName,
                                            style = MaterialTheme.typography.titleLarge,
                                            fontWeight = FontWeight.SemiBold,
                                            color = primaryText
                                        )
                                        Text(
                                            text = "作品集 · ${books.size} 部",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = secondaryText
                                        )
                                    }
                                }
                            }
                        }
                        if (loading) {
                            item(span = { GridItemSpan(maxLineSpan) }) {
                                LinearProgressIndicator(Modifier.fillMaxWidth())
                            }
                        }
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            Text(
                                text = "全部作品",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = primaryText,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                        items(books, key = { it.id }) { book ->
                            SfSearchResultCard(
                                item = book,
                                onClick = { onOpenBook("sf:${book.id}") }
                            )
                        }
                    }
                }
            }
        }
    }
}
