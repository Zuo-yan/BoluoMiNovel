package com.novelai.assistant.ui.discover

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.FilterList
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material3.Badge
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.novelai.assistant.data.fetcher.SfacgFetcher
import com.novelai.assistant.data.sf.SfCategory
import com.novelai.assistant.ui.components.AdaptiveCenteredContainer
import com.novelai.assistant.ui.components.AdaptiveGlassSheet
import com.novelai.assistant.ui.components.GlassCapsuleButton
import com.novelai.assistant.ui.components.GlassCircleButton
import com.novelai.assistant.ui.components.GlassFilterPill
import com.novelai.assistant.ui.components.GlassSearchCapsule
import com.novelai.assistant.ui.components.GlassSectionCard
import com.novelai.assistant.ui.components.LocalAdaptiveMetrics
import com.novelai.assistant.ui.theme.NovelColors

@Composable
fun SfBrowseScreen(
    onBack: () -> Unit,
    onOpenBookDetail: (String) -> Unit,
    viewModel: SfBrowseViewModel = hiltViewModel()
) {
    val searchResults by viewModel.searchResults.collectAsStateWithLifecycle()
    val isSearching by viewModel.isSearching.collectAsStateWithLifecycle()
    val rankList by viewModel.rankList.collectAsStateWithLifecycle()
    val currentSort by viewModel.currentSort.collectAsStateWithLifecycle()
    val officialTags by viewModel.officialTags.collectAsStateWithLifecycle()
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    val browseFilter by viewModel.browseFilter.collectAsStateWithLifecycle()
    val hasMoreBrowse by viewModel.hasMoreBrowse.collectAsStateWithLifecycle()
    val isBrowseLoadingMore by viewModel.isBrowseLoadingMore.collectAsStateWithLifecycle()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val safety by viewModel.safety.collectAsStateWithLifecycle()

    var searchInput by remember { mutableStateOf("") }
    val selectedTagIds = browseFilter.tagIds
    var showTagFilterSheet by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.fetchOfficialTags()
        if (rankList.isEmpty() && !isSearching) {
            viewModel.loadRank("all")
        }
    }

    val adaptive = LocalAdaptiveMetrics.current
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val primaryText = if (isDark) NovelColors.GlassOnDarkText else NovelColors.GlassOnLightText
    val secondaryText = MaterialTheme.colorScheme.onSurfaceVariant
    val accentText = if (isDark) NovelColors.GlassAccentBlueDark else MaterialTheme.colorScheme.primary
    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val displayList = if (isSearching) searchResults else rankList

    fun submitSearch() {
        val trimmed = searchInput.trim()
        val extracted = SfacgFetcher.extractBookId(trimmed)
        if (extracted != null) {
            onOpenBookDetail("sf:$extracted")
        } else if (trimmed.isNotBlank()) {
            viewModel.search(trimmed)
        }
    }

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
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp, bottom = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        GlassCircleButton(
                            icon = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "返回",
                            onClick = onBack
                        )
                        GlassSearchCapsule(
                            query = searchInput,
                            placeholder = "搜索书名 / 作者 / 题材 / 书号",
                            onQueryChange = {
                                searchInput = it
                                if (it.isEmpty() && isSearching) {
                                    viewModel.loadRank()
                                }
                            },
                            onSearch = { if (!state.busy) submitSearch() },
                            modifier = Modifier
                                .weight(1f)
                                .widthIn(max = 640.dp),
                            trailingContent = {
                                if (searchInput.isNotBlank()) {
                                    IconButton(
                                        onClick = { if (!state.busy) submitSearch() },
                                        modifier = Modifier.size(30.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.Search,
                                            contentDescription = "执行搜索",
                                            tint = accentText,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                        )
                        Box {
                            GlassCircleButton(
                                icon = Icons.Rounded.FilterList,
                                contentDescription = "标签筛选",
                                onClick = { showTagFilterSheet = true },
                                tint = if (selectedTagIds.isNotEmpty()) accentText else primaryText
                            )
                            if (selectedTagIds.isNotEmpty()) {
                                Badge(
                                    modifier = Modifier.align(Alignment.TopEnd)
                                ) {
                                    Text("${selectedTagIds.size}")
                                }
                            }
                        }
                        GlassCircleButton(
                            icon = Icons.Rounded.Refresh,
                            contentDescription = "刷新",
                            onClick = {
                                if (!state.busy) {
                                    if (isSearching) {
                                        viewModel.search(searchInput.trim())
                                    } else {
                                        viewModel.loadRank(currentSort)
                                    }
                                    viewModel.fetchOfficialTags()
                                }
                            },
                            tint = if (state.busy) secondaryText.copy(alpha = 0.45f) else primaryText
                        )
                    }

                    SfSafetyNotice(
                        protection = safety,
                        onAcknowledgeCooldown = { viewModel.acknowledgeCooldown() },
                        onForceBypass = { viewModel.forceBypassSafety() }
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        (listOf(SfCategory(0, "全部")) + categories).forEach { cat ->
                            GlassFilterPill(
                                label = cat.name,
                                selected = browseFilter.categoryId == cat.id && !isSearching,
                                onClick = {
                                    searchInput = ""
                                    viewModel.selectCategory(cat.id)
                                }
                            )
                        }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val sorts = if (browseFilter.active) {
                            listOf("month" to "人气最高", "latest" to "最新更新")
                        } else {
                            listOf(
                                "all" to "畅销总榜",
                                "month" to "人气最高",
                                "week" to "飙升周榜",
                                "latest" to "最新更新"
                            )
                        }
                        sorts.forEach { (sortKey, label) ->
                            GlassFilterPill(
                                label = label,
                                selected = currentSort == sortKey && !isSearching,
                                onClick = {
                                    searchInput = ""
                                    viewModel.loadRank(sortKey)
                                }
                            )
                        }
                    }

                    if (selectedTagIds.isNotEmpty()) {
                        val idToName = remember(officialTags) { officialTags.associate { it.id to it.name } }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "已选标签：",
                                style = MaterialTheme.typography.labelMedium,
                                color = accentText
                            )
                            selectedTagIds.forEach { tagId ->
                                GlassFilterPill(
                                    label = idToName[tagId] ?: "标签$tagId",
                                    selected = true,
                                    leadingIcon = Icons.Rounded.Close,
                                    onClick = {
                                        val updated = selectedTagIds - tagId
                                        searchInput = ""
                                        viewModel.loadMultiTagNovels(updated.toList())
                                    }
                                )
                            }
                            TextButton(
                                onClick = {
                                    searchInput = ""
                                    viewModel.loadMultiTagNovels(emptyList())
                                },
                                contentPadding = PaddingValues(horizontal = 6.dp)
                            ) {
                                Text("清空标签", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }

                    if (state.busy) {
                        LinearProgressIndicator(Modifier.fillMaxWidth())
                    }
                    state.error?.let { error ->
                        Text(
                            text = error,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }

            if (displayList.isNotEmpty()) {
                BoxWithConstraints(modifier = Modifier.weight(1f).fillMaxWidth()) {
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
                        items(displayList, key = { it.id }) { item ->
                            SfSearchResultCard(
                                item = item,
                                onClick = { onOpenBookDetail("sf:${item.id}") }
                            )
                        }

                        if (hasMoreBrowse) {
                            item(span = { GridItemSpan(maxLineSpan) }) {
                                LaunchedEffect(displayList.size) {
                                    if (!isBrowseLoadingMore) {
                                        viewModel.loadMoreBrowse()
                                    }
                                }
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 12.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (isBrowseLoadingMore) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(18.dp),
                                                strokeWidth = 2.dp
                                            )
                                            Text(
                                                text = "正在加载下一页...",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = secondaryText
                                            )
                                        }
                                    } else {
                                        GlassCapsuleButton(
                                            text = "点击加载更多",
                                            onClick = { viewModel.loadMoreBrowse() },
                                            filled = false
                                        )
                                    }
                                }
                            }
                        } else {
                            item(span = { GridItemSpan(maxLineSpan) }) {
                                Text(
                                    text = "已翻到底部啦 ~",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = secondaryText,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 16.dp)
                                )
                            }
                        }
                    }
                }
            } else if (!state.busy) {
                Box(
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
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.SearchOff,
                                    contentDescription = null,
                                    tint = secondaryText,
                                    modifier = Modifier.size(46.dp)
                                )
                                Text(
                                    text = if (isSearching) {
                                        "未找到匹配书籍"
                                    } else {
                                        "没有同时符合当前分类与全部标签的作品，可减少标签或切换分类。"
                                    },
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = secondaryText,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showTagFilterSheet) {
        var tempSelected by remember(selectedTagIds) { mutableStateOf(selectedTagIds) }
        val tagAccent = if (isDark) Color(0xFFFF7B81) else Color(0xFFE53935)

        AdaptiveGlassSheet(
            onDismissRequest = { showTagFilterSheet = false },
            maxWidth = 600.dp
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    text = "想看的作品标签",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = tagAccent
                )
                Text(
                    text = "作品需同时包含所有已选标签，分类条件会保留。",
                    style = MaterialTheme.typography.bodySmall,
                    color = secondaryText
                )

                if (officialTags.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(104.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 420.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(officialTags, key = { it.id }) { tag ->
                            val isSelected = tempSelected.contains(tag.id)
                            val pillBg = when {
                                isSelected -> tagAccent.copy(alpha = if (isDark) 0.24f else 0.12f)
                                isDark -> Color.White.copy(alpha = 0.08f)
                                else -> Color.Black.copy(alpha = 0.04f)
                            }
                            val pillBorder = if (isSelected) {
                                tagAccent
                            } else if (isDark) {
                                Color.White.copy(alpha = 0.16f)
                            } else {
                                Color.Black.copy(alpha = 0.10f)
                            }
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(20.dp))
                                    .background(pillBg)
                                    .border(
                                        width = 1.dp,
                                        color = pillBorder,
                                        shape = RoundedCornerShape(20.dp)
                                    )
                                    .clickable {
                                        tempSelected = if (isSelected) {
                                            tempSelected - tag.id
                                        } else {
                                            tempSelected + tag.id
                                        }
                                    }
                                    .padding(vertical = 10.dp, horizontal = 6.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = tag.name,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontSize = 13.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSelected) tagAccent else primaryText,
                                    textAlign = TextAlign.Center,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    GlassCapsuleButton(
                        text = "重置",
                        onClick = { tempSelected = emptySet() },
                        filled = false,
                        modifier = Modifier.weight(1f)
                    )
                    GlassCapsuleButton(
                        text = "确定" + if (tempSelected.isNotEmpty()) " (${tempSelected.size})" else "",
                        onClick = {
                            showTagFilterSheet = false
                            searchInput = ""
                            viewModel.loadMultiTagNovels(tempSelected.toList())
                        },
                        filled = true,
                        modifier = Modifier.weight(1.4f)
                    )
                }
            }
        }
    }
}
