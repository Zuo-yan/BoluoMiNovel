package com.novelai.assistant.ui.discover

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SfBrowseScreen(
    onBack: () -> Unit,
    onOpenBookDetail: (String) -> Unit,
    viewModel: SfViewModel = hiltViewModel()
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

    var searchInput by remember { mutableStateOf("") }
    val selectedTagIds = browseFilter.tagIds
    var showTagFilterSheet by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.fetchOfficialTags()
        if (rankList.isEmpty() && !isSearching) {
            viewModel.loadRank("all")
        }
    }

    val displayList = if (isSearching) searchResults else rankList

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("分类", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    // 右上角漏斗筛选图标（对齐官方 App Image #12）
                    Box {
                        IconButton(onClick = { showTagFilterSheet = true }) {
                            Icon(
                                Icons.Rounded.FilterList,
                                contentDescription = "标签筛选",
                                tint = if (selectedTagIds.isNotEmpty()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                            )
                        }
                        if (selectedTagIds.isNotEmpty()) {
                            Badge(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(top = 4.dp, end = 4.dp)
                            ) {
                                Text("${selectedTagIds.size}")
                            }
                        }
                    }
                    IconButton(
                        onClick = {
                            if (isSearching) {
                                viewModel.search(searchInput.trim())
                            } else {
                                viewModel.loadRank(currentSort)
                            }
                            viewModel.fetchOfficialTags()
                        },
                        enabled = !state.busy
                    ) {
                        Icon(Icons.Rounded.Refresh, contentDescription = "刷新")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // 动态安全熔断提示（提供无视风险继续使用按钮）
            Box(Modifier.padding(horizontal = 16.dp, vertical = 2.dp)) {
                SfSafetyNotice(viewModel)
            }

            // 搜索输入框
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = searchInput,
                    onValueChange = { searchInput = it },
                    label = { Text("搜索书名 / 作者 / 题材") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    trailingIcon = {
                        if (searchInput.isNotEmpty()) {
                            IconButton({ searchInput = ""; viewModel.loadRank() }) {
                                Icon(Icons.Rounded.Close, "清空")
                            }
                        }
                    },
                    modifier = Modifier.weight(1f)
                )
                Button(
                    onClick = {
                        val trimmed = searchInput.trim()
                        val extracted = com.novelai.assistant.data.fetcher.SfacgFetcher.extractBookId(trimmed)
                        if (extracted != null) {
                            onOpenBookDetail("sf:$extracted")
                        } else if (trimmed.isNotBlank()) {
                            viewModel.search(trimmed)
                        }
                    },
                    enabled = searchInput.isNotBlank() && !state.busy,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("搜索")
                }
            }

            // 一级大类筛选（对齐官方 App Image #12：全部 / 魔幻 / 玄幻 / 古风 / 科幻 / 校园 / 都市 / 游戏 / 悬疑）
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                (listOf(com.novelai.assistant.data.sf.SfCategory(0, "全部")) + categories).forEach { cat ->
                    FilterChip(
                        selected = browseFilter.categoryId == cat.id && !isSearching,
                        onClick = {
                            searchInput = ""
                            viewModel.selectCategory(cat.id)
                        },
                        label = { Text(cat.name, fontWeight = if (browseFilter.categoryId == cat.id) FontWeight.Bold else FontWeight.Normal) },
                        shape = RoundedCornerShape(10.dp)
                    )
                }
            }

            // 排序栏（对齐官方 App Image #12：最新更新 / 人气最高 / 收藏最高 / 月票最多 / 畅销总榜）
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val sorts = if (browseFilter.active) listOf("month" to "人气最高", "latest" to "最新更新") else listOf(
                    "all" to "畅销总榜",
                    "month" to "人气最高",
                    "week" to "飙升周榜",
                    "latest" to "最新更新"
                )
                sorts.forEach { (sortKey, label) ->
                    FilterChip(
                        selected = currentSort == sortKey && !isSearching,
                        onClick = {
                            searchInput = ""
                            viewModel.loadRank(sortKey)
                        },
                        label = { Text(label) },
                        shape = RoundedCornerShape(10.dp)
                    )
                }
            }

            // 已选多标签展示条（带一键移除与清除）
            if (selectedTagIds.isNotEmpty()) {
                val idToName = remember(officialTags) { officialTags.associate { it.id to it.name } }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text("已选标签：", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    selectedTagIds.forEach { tagId ->
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.clickable {
                                val updated = selectedTagIds - tagId
                                searchInput = ""
                                viewModel.loadMultiTagNovels(updated.toList())
                            }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(idToName[tagId] ?: "标签$tagId", style = MaterialTheme.typography.labelSmall)
                                Icon(Icons.Rounded.Close, null, modifier = Modifier.size(12.dp))
                            }
                        }
                    }
                    TextButton(
                        onClick = {
                            searchInput = ""
                            viewModel.loadMultiTagNovels(emptyList())
                        },
                        contentPadding = PaddingValues(horizontal = 4.dp)
                    ) {
                        Text("清空标签", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }

            if (state.busy) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
            }
            state.error?.let { error ->
                Text(error, color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 16.dp))
            }

            // 作品列表：全屏独立滑动，彻底避免手势冲突与抽搐
            if (displayList.isNotEmpty()) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    contentPadding = PaddingValues(top = 8.dp, bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(displayList, key = { it.id }) { item ->
                        SfSearchResultCard(
                            item = item,
                            onClick = { onOpenBookDetail("sf:${item.id}") }
                        )
                    }

                    // 分页无限滚动加载更多
                    if (hasMoreBrowse) {
                        item {
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
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                                        Text("正在加载下一页...", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                } else {
                                    TextButton(onClick = { viewModel.loadMoreBrowse() }) {
                                        Text("点击加载更多", style = MaterialTheme.typography.bodySmall)
                                    }
                                }
                            }
                        }
                    } else {
                        item {
                            Text(
                                text = "已翻到底部啦 ~",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.outline,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp)
                            )
                        }
                    }
                }
            } else if (!state.busy) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            Icons.Rounded.SearchOff,
                            null,
                            tint = MaterialTheme.colorScheme.outline,
                            modifier = Modifier.size(48.dp)
                        )
                        Text(
                            if (isSearching) "未找到匹配书籍" else "没有同时符合当前分类与全部标签的作品，可减少标签或切换分类。",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }

    // 官方 1:1 复刻：“想看的作品标签” 多选弹窗（对齐 Image #11）
    if (showTagFilterSheet) {
        var tempSelected by remember { mutableStateOf(selectedTagIds) }
        ModalBottomSheet(
            onDismissRequest = { showTagFilterSheet = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "想看的作品标签",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFE53935) // 官方标志红
                )
                Text("作品需同时包含所有已选标签，分类条件会保留。",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

                if (officialTags.isEmpty()) {
                    Box(Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                } else {
                    // 三列整齐网格（Image #11 布局）
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(3),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 420.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(officialTags, key = { it.id }) { tag ->
                            val isSelected = tempSelected.contains(tag.id)
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(20.dp))
                                    .background(
                                        if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                                        else MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.4f)
                                    )
                                    .border(
                                        width = 1.dp,
                                        color = if (isSelected) Color(0xFFE53935) else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                                        shape = RoundedCornerShape(20.dp)
                                    )
                                    .clickable {
                                        tempSelected = if (isSelected) tempSelected - tag.id else tempSelected + tag.id
                                    }
                                    .padding(vertical = 10.dp, horizontal = 4.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = tag.name,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontSize = 13.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSelected) Color(0xFFE53935) else MaterialTheme.colorScheme.onSurface,
                                    textAlign = TextAlign.Center,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }

                // 底部双按钮：重置 与 确定（Image #11 布局）
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = { tempSelected = emptySet() },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("重置")
                    }
                    Button(
                        onClick = {
                            showTagFilterSheet = false
                            searchInput = ""
                            viewModel.loadMultiTagNovels(tempSelected.toList())
                        },
                        modifier = Modifier.weight(1.4f),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE53935))
                    ) {
                        Text("确定" + if (tempSelected.isNotEmpty()) " (${tempSelected.size})" else "")
                    }
                }
            }
        }
    }
}

