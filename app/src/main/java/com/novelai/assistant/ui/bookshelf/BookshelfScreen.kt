package com.novelai.assistant.ui.bookshelf

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.items as listItems
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CallMerge
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material3.TextButton
import androidx.compose.material.icons.rounded.DriveFileMove
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.HideImage
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.UploadFile
import androidx.compose.material.icons.rounded.ViewList
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.novelai.assistant.data.db.BookEntity
import com.novelai.assistant.data.importer.BookImportManager
import com.novelai.assistant.ui.components.EmptyState
import com.novelai.assistant.ui.components.LargeTitleHeader
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
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

    val snackbarHostState = remember { SnackbarHostState() }
    val haptics = LocalHapticFeedback.current

    var showAddCategory by remember { mutableStateOf(false) }
    var categoryToDelete by remember { mutableStateOf<String?>(null) }
    var newCategoryName by remember { mutableStateOf("") }
    var actionBook by remember { mutableStateOf<BookEntity?>(null) }
    var moveBook by remember { mutableStateOf<BookEntity?>(null) }
    var confirmDelete by remember { mutableStateOf<BookEntity?>(null) }
    var pendingCoverBookId by remember { mutableStateOf<String?>(null) }

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
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            LargeTitleHeader(
                title = "书架",
                subtitle = if (books.isEmpty()) null else "共 ${books.size} 本",
                actions = {
                    IconButton(onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        importLauncher.launch(arrayOf("*/*"))
                    }) {
                        if (importing) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        else Icon(Icons.Rounded.UploadFile, contentDescription = "导入书籍")
                    }
                    IconButton(onClick = {
                        viewModel.setViewMode(
                            if (viewMode == ShelfViewMode.GRID) ShelfViewMode.LIST else ShelfViewMode.GRID
                        )
                    }) {
                        Icon(
                            if (viewMode == ShelfViewMode.GRID) Icons.Rounded.ViewList else Icons.Rounded.GridView,
                            contentDescription = "切换视图"
                        )
                    }
                }
            )

            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 4.dp)
            ) {
                listItems(categories) { cat ->
                    val isSelected = cat == currentCategory
                    val isCustom = cat != "全部"
                    FilterChip(
                        selected = isSelected,
                        onClick = {
                            if (isSelected && isCustom) {
                                categoryToDelete = cat
                            } else {
                                viewModel.selectCategory(cat)
                            }
                        },
                        label = { Text(cat) },
                        trailingIcon = if (isCustom && isSelected) {
                            {
                                Icon(
                                    Icons.Rounded.Close,
                                    contentDescription = "删除书单",
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        } else null,
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f),
                            selectedLabelColor = MaterialTheme.colorScheme.primary
                        )
                    )
                }
                listItems(listOf("__add__")) {
                    FilterChip(
                        selected = false,
                        onClick = { showAddCategory = true },
                        label = { Text("+ 书单") }
                    )
                }
            }

            Spacer(Modifier.height(6.dp))

            if (books.isEmpty()) {
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        EmptyState(
                            icon = Icons.Rounded.UploadFile,
                            title = if (currentCategory == "全部") "书架空空如也" else "「$currentCategory」书单为空",
                            hint = if (currentCategory == "全部") "点击右上角导入 TXT / Markdown / EPUB\n或在「菠萝包」页打开与下载书籍"
                            else "长按书籍可将其移动到此书单"
                        )
                        if (currentCategory != "全部") {
                            Spacer(Modifier.height(12.dp))
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
            } else if (viewMode == ShelfViewMode.GRID) {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 24.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalArrangement = Arrangement.spacedBy(18.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    gridItems(books, key = { it.id }) { book ->
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
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    listItems(books, key = { it.id }) { book ->
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

    // 长按操作菜单
    actionBook?.let { book ->
        ModalBottomSheet(onDismissRequest = { actionBook = null }) {
            Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
                Text(book.title, style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(10.dp))
                SimpleAction(Icons.Rounded.Menu, "继续阅读") {
                    actionBook = null; onOpenBook(book.id)
                }
                SimpleAction(Icons.Rounded.CallMerge, "剧情分支树") {
                    actionBook = null; onOpenTree(book.id)
                }
                SimpleAction(Icons.Rounded.DriveFileMove, "移动到书单") {
                    moveBook = book; actionBook = null
                }
                if (book.sourceType != com.novelai.assistant.data.db.BookSourceType.BOLUOBAO) {
                    SimpleAction(
                        icon = Icons.Rounded.Image,
                        label = if (book.coverUri.isNullOrBlank()) "设置封面" else "更换封面"
                    ) {
                        val bid = book.id
                        actionBook = null
                        pendingCoverBookId = bid
                        coverPickerLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    }
                    if (!book.coverUri.isNullOrBlank()) {
                        SimpleAction(
                            icon = Icons.Rounded.HideImage,
                            label = "恢复默认封面"
                        ) {
                            viewModel.clearBookCover(book.id)
                            actionBook = null
                        }
                    }
                }
                SimpleAction(Icons.Rounded.Delete, "删除本书", tint = MaterialTheme.colorScheme.error) {
                    actionBook = null; confirmDelete = book
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }

    // 移动到书单
    moveBook?.let { book ->
        ModalBottomSheet(onDismissRequest = { moveBook = null }) {
            Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
                Text("移动《${book.title}》到书单", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(10.dp))
                categories.filter { it != "全部" }.forEach { cat ->
                    SimpleAction(Icons.Rounded.DriveFileMove, cat) {
                        viewModel.moveBookToCategory(book, cat)
                        moveBook = null
                    }
                }
                Text(
                    "提示：可在书架顶部「+ 书单」创建新书单",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(24.dp))
            }
        }
    }

    // 新建书单
    if (showAddCategory) {
        AlertDialog(
            onDismissRequest = { showAddCategory = false },
            title = { Text("新建书单") },
            text = {
                OutlinedTextField(
                    value = newCategoryName,
                    onValueChange = { newCategoryName = it },
                    placeholder = { Text("如：奇幻 / 已读完 / AI 推演中") },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.addCategory(newCategoryName)
                    newCategoryName = ""
                    showAddCategory = false
                }) { Text("创建") }
            },
            dismissButton = {
                TextButton(onClick = { showAddCategory = false }) { Text("取消") }
            }
        )
    }

    // 删除书单确认
    categoryToDelete?.let { cat ->
        AlertDialog(
            onDismissRequest = { categoryToDelete = null },
            title = { Text("删除书单「$cat」？") },
            text = { Text("该书单将被删除，该书单下的书籍将保留在书架中（移至默认分类）。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        categoryToDelete = null
                        viewModel.deleteCategory(cat)
                    }
                ) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { categoryToDelete = null }) {
                    Text("取消")
                }
            }
        )
    }

    // 确认删除
    confirmDelete?.let { book ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text("删除《${book.title}》？") },
            text = { Text("章节、AI 续写与分支记录将一并删除，且不可恢复。") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteBook(book)
                    confirmDelete = null
                }) { Text("删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text("取消") } }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ShelfCard(book: BookEntity, onClick: () -> Unit, onLongPress: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongPress)
    ) {
        BookCover(
            book = book,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(0.72f)
                .clip(RoundedCornerShape(18.dp)),
            corner = RoundedCornerShape(18.dp)
        )
        Spacer(Modifier.height(8.dp))
        Text(
            book.title,
            style = MaterialTheme.typography.titleMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            "${book.readingProgressPercent.toInt()}% · ${formatTime(book.lastReadAt)}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ShelfRow(book: BookEntity, onClick: () -> Unit, onLongPress: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .background(MaterialTheme.colorScheme.surface)
            .combinedClickable(onClick = onClick, onLongClick = onLongPress)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        BookCover(
            book = book,
            modifier = Modifier
                .width(52.dp)
                .aspectRatio(0.72f)
                .clip(MaterialTheme.shapes.small),
            corner = MaterialTheme.shapes.small
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                book.title,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                "${BookImportManager.sourceTypeLabel(book.sourceType)} · ${book.category} · ${book.readingProgressPercent.toInt()}%",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SimpleAction(
    icon: ImageVector,
    label: String,
    tint: Color = MaterialTheme.colorScheme.onSurface,
    onClick: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .combinedClickable(onClick = onClick, onLongClick = onClick)
            .padding(vertical = 12.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = tint)
        Spacer(Modifier.width(14.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge, color = tint)
    }
}

private fun formatTime(ts: Long): String =
    if (ts <= 0) "未读"
    else SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(ts))
