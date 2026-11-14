package com.novelai.assistant.ui.bookshelf

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.items as listItems
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.UploadFile
import androidx.compose.material.icons.rounded.ViewList
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.novelai.assistant.data.db.BookEntity
import com.novelai.assistant.data.importer.BookImportManager
import com.novelai.assistant.ui.components.AdaptiveCenteredContainer
import com.novelai.assistant.ui.components.EmptyState
import com.novelai.assistant.ui.components.GlassCapsuleButton
import com.novelai.assistant.ui.components.GlassCircleButton
import com.novelai.assistant.ui.components.GlassFilterPill
import com.novelai.assistant.ui.components.GlassSceneType
import com.novelai.assistant.ui.components.GlassSearchCapsule
import com.novelai.assistant.ui.components.GlassWidgetSize
import com.novelai.assistant.ui.components.LargeTitleHeader
import com.novelai.assistant.ui.components.LiquidGlassBox
import com.novelai.assistant.ui.components.LocalAdaptiveMetrics
import com.novelai.assistant.ui.components.adaptiveContentPadding
import com.novelai.assistant.ui.components.bottomBarContentPadding
import com.novelai.assistant.ui.theme.NovelColors
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun BookshelfScreen(
    onOpenBook: (String) -> Unit,
    onOpenTree: (String) -> Unit,
    viewModel: BookshelfViewModel = hiltViewModel()
) {
    val books by viewModel.books.collectAsStateWithLifecycle()
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    val currentCategory by viewModel.currentCategory.collectAsStateWithLifecycle()
    val viewMode by viewModel.viewMode.collectAsStateWithLifecycle()
    val importing by viewModel.importing.collectAsStateWithLifecycle()
    val message by viewModel.importMessage.collectAsStateWithLifecycle()

    val adaptive = LocalAdaptiveMetrics.current
    val snackbarHostState = remember { SnackbarHostState() }
    val haptics = LocalHapticFeedback.current

    var searchVisible by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var showAddCategory by remember { mutableStateOf(false) }
    var categoryToDelete by remember { mutableStateOf<String?>(null) }
    var newCategoryName by remember { mutableStateOf("") }
    var actionBook by remember { mutableStateOf<BookEntity?>(null) }
    var moveBook by remember { mutableStateOf<BookEntity?>(null) }
    var confirmDelete by remember { mutableStateOf<BookEntity?>(null) }
    var pendingCoverBookId by remember { mutableStateOf<String?>(null) }

    val displayedBooks = remember(books, searchQuery) {
        val q = searchQuery.trim()
        if (q.isEmpty()) books
        else books.filter {
            it.title.contains(q, ignoreCase = true) ||
                it.author.contains(q, ignoreCase = true) ||
                it.category.contains(q, ignoreCase = true)
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> viewModel.importBook(uri) }

    val coverPickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        val bid = pendingCoverBookId
        if (bid != null && uri != null) {
            viewModel.setBookCover(bid, uri)
        }
        pendingCoverBookId = null
    }

    LaunchedEffect(message) {
        message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.consumeMessage()
        }
    }

    Scaffold(
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = {
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier.padding(bottom = bottomBarContentPadding(8.dp))
            )
        }
    ) { _ ->
        AdaptiveCenteredContainer(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding(),
            maxWidth = adaptive.maxContentWidth
        ) {
            Column(Modifier.fillMaxSize()) {
                LargeTitleHeader(
                    title = "书架",
                    subtitle = when {
                        searchQuery.isNotBlank() -> "找到 ${displayedBooks.size} 本"
                        books.isEmpty() -> null
                        else -> "共 ${books.size} 本"
                    },
                    horizontalPadding = adaptive.horizontalPadding,
                    actions = {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            GlassCircleButton(
                                icon = if (searchVisible) Icons.Rounded.Close else Icons.Rounded.Search,
                                contentDescription = if (searchVisible) "关闭搜索" else "搜索书架",
                                onClick = {
                                    if (searchVisible) {
                                        searchVisible = false
                                        searchQuery = ""
                                    } else {
                                        searchVisible = true
                                    }
                                }
                            )
                            if (importing) {
                                LiquidGlassBox(
                                    modifier = Modifier.size(44.dp),
                                    cornerRadius = 22.dp,
                                    shape = CircleShape,
                                    widgetSize = GlassWidgetSize.SMALL,
                                    sceneType = GlassSceneType.IMMERSIVE,
                                    interactive = false,
                                    shadowElevation = 8.dp,
                                    contentAlignment = Alignment.Center
                                ) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(18.dp),
                                        strokeWidth = 2.dp
                                    )
                                }
                            } else {
                                GlassCircleButton(
                                    icon = Icons.Rounded.UploadFile,
                                    contentDescription = "导入书籍",
                                    onClick = {
                                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        importLauncher.launch(arrayOf("*/*"))
                                    }
                                )
                            }
                            GlassCircleButton(
                                icon = if (viewMode == ShelfViewMode.GRID) Icons.Rounded.ViewList else Icons.Rounded.GridView,
                                contentDescription = "切换视图",
                                onClick = {
                                    viewModel.setViewMode(
                                        if (viewMode == ShelfViewMode.GRID) ShelfViewMode.LIST else ShelfViewMode.GRID
                                    )
                                }
                            )
                        }
                    }
                )

                AnimatedVisibility(
                    visible = searchVisible,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = adaptive.horizontalPadding, vertical = 4.dp)
                    ) {
                        GlassSearchCapsule(
                            query = searchQuery,
                            placeholder = "搜索书名、作者或书单…",
                            onQueryChange = { searchQuery = it },
                            modifier = Modifier
                                .fillMaxWidth()
                                .widthIn(max = 560.dp)
                        )
                    }
                }

                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(horizontal = adaptive.horizontalPadding, vertical = 6.dp)
                ) {
                    listItems(categories) { cat ->
                        val isSelected = cat == currentCategory
                        val isCustom = cat != "全部"
                        GlassFilterPill(
                            label = cat,
                            selected = isSelected,
                            leadingIcon = if (isCustom && isSelected) Icons.Rounded.Close else null,
                            onClick = {
                                if (isSelected && isCustom) {
                                    categoryToDelete = cat
                                } else {
                                    viewModel.selectCategory(cat)
                                }
                            }
                        )
                    }
                    listItems(listOf("__add__")) {
                        GlassFilterPill(
                            label = "书单",
                            selected = false,
                            leadingIcon = Icons.Rounded.Add,
                            onClick = { showAddCategory = true }
                        )
                    }
                }

                Spacer(Modifier.height(4.dp))

                if (displayedBooks.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .padding(horizontal = adaptive.horizontalPadding)
                            .padding(bottom = bottomBarContentPadding(12.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            modifier = Modifier.widthIn(max = 420.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            if (searchQuery.isNotBlank() && books.isNotEmpty()) {
                                EmptyState(
                                    icon = Icons.Rounded.Search,
                                    title = "未找到「$searchQuery」",
                                    hint = "试试更换关键词，或清空搜索条件"
                                )
                                GlassCapsuleButton(
                                    text = "清空搜索",
                                    filled = false,
                                    onClick = { searchQuery = "" }
                                )
                            } else {
                                EmptyState(
                                    icon = Icons.Rounded.UploadFile,
                                    title = if (currentCategory == "全部") "书架空空如也" else "「$currentCategory」书单为空",
                                    hint = if (currentCategory == "全部") {
                                        "点击右上角导入 TXT / Markdown / EPUB\n或在「菠萝包」页打开与下载书籍"
                                    } else {
                                        "长按书籍可将其移动到此书单"
                                    }
                                )
                                if (currentCategory == "全部") {
                                    GlassCapsuleButton(
                                        text = "导入本地书籍",
                                        icon = Icons.Rounded.UploadFile,
                                        filled = true,
                                        onClick = { importLauncher.launch(arrayOf("*/*")) }
                                    )
                                } else {
                                    TextButton(
                                        onClick = { categoryToDelete = currentCategory }
                                    ) {
                                        Icon(
                                            Icons.Rounded.DeleteOutline,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp),
                                            tint = MaterialTheme.colorScheme.error
                                        )
                                        Spacer(Modifier.width(4.dp))
                                        Text("删除此书单", color = MaterialTheme.colorScheme.error)
                                    }
                                }
                            }
                        }
                    }
                } else if (viewMode == ShelfViewMode.GRID) {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = adaptive.bookshelfMinCardWidth),
                        contentPadding = adaptiveContentPadding(top = 8.dp, bottomExtra = 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(if (adaptive.isTablet) 18.dp else 14.dp),
                        verticalArrangement = Arrangement.spacedBy(if (adaptive.isTablet) 22.dp else 18.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        gridItems(displayedBooks, key = { it.id }) { book ->
                            ShelfCard(
                                book = book,
                                onClick = { onOpenBook(book.id) },
                                onLongPress = {
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                    actionBook = book
                                }
                            )
                        }
                    }
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(adaptive.listGridColumns),
                        contentPadding = adaptiveContentPadding(top = 8.dp, bottomExtra = 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        gridItems(displayedBooks, key = { it.id }) { book ->
                            ShelfRow(
                                book = book,
                                onClick = { onOpenBook(book.id) },
                                onLongPress = {
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                    actionBook = book
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    // 长按操作菜单
    actionBook?.let { book ->
        BookActionSheet(
            book = book,
            onDismiss = { actionBook = null },
            onContinueReading = {
                actionBook = null
                onOpenBook(book.id)
            },
            onOpenTree = {
                actionBook = null
                onOpenTree(book.id)
            },
            onMoveToCategory = {
                moveBook = book
                actionBook = null
            },
            onPickCover = {
                val bid = book.id
                actionBook = null
                pendingCoverBookId = bid
                coverPickerLauncher.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                )
            },
            onClearCover = {
                viewModel.clearBookCover(book.id)
                actionBook = null
            },
            onDelete = {
                actionBook = null
                confirmDelete = book
            }
        )
    }

    // 移动到书单
    moveBook?.let { book ->
        MoveToCategorySheet(
            bookTitle = book.title,
            categories = categories,
            currentCategory = book.category,
            onDismiss = { moveBook = null },
            onSelectCategory = { cat ->
                viewModel.moveBookToCategory(book, cat)
                moveBook = null
            }
        )
    }

    // 新建书单
    if (showAddCategory) {
        AddCategoryDialog(
            name = newCategoryName,
            onNameChange = { newCategoryName = it },
            onDismiss = { showAddCategory = false },
            onConfirm = {
                viewModel.addCategory(newCategoryName)
                newCategoryName = ""
                showAddCategory = false
            }
        )
    }

    // 删除书单确认
    categoryToDelete?.let { cat ->
        DeleteCategoryDialog(
            category = cat,
            onDismiss = { categoryToDelete = null },
            onConfirm = {
                categoryToDelete = null
                viewModel.deleteCategory(cat)
            }
        )
    }

    // 确认删除
    confirmDelete?.let { book ->
        DeleteBookDialog(
            bookTitle = book.title,
            onDismiss = { confirmDelete = null },
            onConfirm = {
                viewModel.deleteBook(book)
                confirmDelete = null
            }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ShelfCard(book: BookEntity, onClick: () -> Unit, onLongPress: () -> Unit) {
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val titleColor = if (isDark) NovelColors.GlassOnDarkText else NovelColors.GlassOnLightText
    val subtitleColor = if (isDark) {
        NovelColors.GlassOnDarkText.copy(alpha = 0.68f)
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .combinedClickable(onClick = onClick, onLongClick = onLongPress)
            .padding(bottom = 4.dp)
    ) {
        BookCover(
            book = book,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(0.72f),
            corner = RoundedCornerShape(18.dp),
            elevation = 10.dp,
            showBadges = true
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = book.title,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
            color = titleColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 2.dp)
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = "${book.readingProgressPercent.toInt()}% · ${formatTime(book.lastReadAt)}",
            style = MaterialTheme.typography.bodySmall,
            color = subtitleColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 2.dp)
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ShelfRow(book: BookEntity, onClick: () -> Unit, onLongPress: () -> Unit) {
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val primaryTextColor = if (isDark) NovelColors.GlassOnDarkText else NovelColors.GlassOnLightText
    val secondaryTextColor = if (isDark) {
        NovelColors.GlassOnDarkText.copy(alpha = 0.72f)
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    val accentColor = if (isDark) NovelColors.GlassAccentBlueDark else MaterialTheme.colorScheme.primary

    LiquidGlassBox(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .combinedClickable(onClick = onClick, onLongClick = onLongPress),
        cornerRadius = 20.dp,
        widgetSize = GlassWidgetSize.BIG,
        sceneType = GlassSceneType.GENERAL,
        interactive = false,
        skipRenderLayer = true,
        shadowElevation = 6.dp,
        borderWidth = 0.7.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            BookCover(
                book = book,
                modifier = Modifier
                    .width(56.dp)
                    .aspectRatio(0.72f),
                corner = RoundedCornerShape(12.dp),
                elevation = 4.dp,
                showBadges = false
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = book.title,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = primaryTextColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    text = "${BookImportManager.sourceTypeLabel(book.sourceType)} · ${book.category} · ${formatTime(book.lastReadAt)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = secondaryTextColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(7.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    LinearProgressIndicator(
                        progress = { (book.readingProgressPercent / 100f).coerceIn(0f, 1f) },
                        modifier = Modifier
                            .weight(1f)
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp)),
                        color = accentColor,
                        trackColor = if (isDark) Color.White.copy(alpha = 0.14f) else Color.Black.copy(alpha = 0.08f)
                    )
                    Text(
                        text = "${book.readingProgressPercent.toInt()}%",
                        style = MaterialTheme.typography.labelMedium,
                        color = secondaryTextColor
                    )
                }
            }
        }
    }
}

private val BOOK_TIME_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("MM-dd HH:mm")

private fun formatTime(ts: Long): String =
    if (ts <= 0) "未读"
    else Instant.ofEpochMilli(ts).atZone(ZoneId.systemDefault()).format(BOOK_TIME_FORMATTER)
