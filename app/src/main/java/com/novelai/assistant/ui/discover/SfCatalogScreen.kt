package com.novelai.assistant.ui.discover

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import com.novelai.assistant.ui.components.GlassSceneType
import com.novelai.assistant.ui.components.GlassSectionCard
import com.novelai.assistant.ui.components.GlassWidgetSize
import com.novelai.assistant.ui.components.LiquidGlassBox
import com.novelai.assistant.ui.components.LocalAdaptiveMetrics
import com.novelai.assistant.ui.theme.NovelColors

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

    val adaptive = LocalAdaptiveMetrics.current
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val primaryText = if (isDark) NovelColors.GlassOnDarkText else NovelColors.GlassOnLightText
    val secondaryText = MaterialTheme.colorScheme.onSurfaceVariant
    val accentText = if (isDark) NovelColors.GlassAccentBlueDark else MaterialTheme.colorScheme.primary
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
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = "章节目录",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = primaryText
                        )
                        if (!book?.title.isNullOrBlank()) {
                            Text(
                                text = book?.title.orEmpty(),
                                style = MaterialTheme.typography.bodySmall,
                                color = secondaryText,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    if (bookId.startsWith("sf:")) {
                        GlassCircleButton(
                            icon = Icons.Rounded.Refresh,
                            contentDescription = "刷新目录",
                            onClick = { if (!busy) viewModel.refreshDirectory(bookId) },
                            tint = if (busy) secondaryText.copy(alpha = 0.45f) else primaryText
                        )
                    }
                }
            }

            BoxWithConstraints(modifier = Modifier.weight(1f).fillMaxWidth()) {
                val extraSide = ((maxWidth - adaptive.maxContentWidth) / 2).coerceAtLeast(0.dp)
                val horizontalPad = adaptive.horizontalPadding + extraSide

                LazyVerticalGrid(
                    columns = GridCells.Fixed(adaptive.listGridColumns),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = horizontalPad,
                        end = horizontalPad,
                        top = 4.dp,
                        bottom = navBottom + 24.dp
                    ),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (busy) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            LinearProgressIndicator(Modifier.fillMaxWidth())
                        }
                    }

                    updates[bookId]?.let { update ->
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            GlassSectionCard(contentPadding = PaddingValues(14.dp)) {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text(
                                        text = update.label(),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (update.error != null) MaterialTheme.colorScheme.error else secondaryText
                                    )
                                    if (update.added.isNotEmpty()) {
                                        GlassCapsuleButton(
                                            text = "下载新增章节",
                                            onClick = { viewModel.downloadUpdates(bookId) },
                                            filled = true
                                        )
                                    }
                                }
                            }
                        }
                    }

                    error?.let { err ->
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            GlassSectionCard(contentPadding = PaddingValues(14.dp)) {
                                Text(err, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }

                    items(chapters, key = { it.id }) { ch ->
                        val statusText = when {
                            ch.downloaded -> "已下载"
                            ch.content.isNotBlank() -> "已缓存"
                            ch.isPaid -> "VIP · 读取时检查已购"
                            else -> "免费"
                        }
                        val statusBg = when {
                            ch.downloaded || ch.content.isNotBlank() -> accentText.copy(alpha = if (isDark) 0.22f else 0.12f)
                            ch.isPaid -> NovelColors.Amber.copy(alpha = if (isDark) 0.22f else 0.16f)
                            else -> if (isDark) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.05f)
                        }
                        val statusFg = when {
                            ch.downloaded || ch.content.isNotBlank() -> accentText
                            ch.isPaid -> if (isDark) NovelColors.Amber else Color(0xFF9C5B00)
                            else -> secondaryText
                        }

                        LiquidGlassBox(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .clickable { onRead(ch.chapterIndex) },
                            cornerRadius = 16.dp,
                            widgetSize = GlassWidgetSize.SMALL,
                            sceneType = GlassSceneType.GENERAL,
                            interactive = false,
                            skipRenderLayer = true,
                            shadowElevation = 3.dp,
                            borderWidth = 0.6.dp
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 12.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = "${ch.chapterIndex + 1}. ${ch.title}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium,
                                    color = primaryText,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(statusBg)
                                        .padding(horizontal = 7.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = statusText,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = statusFg,
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
