package com.novelai.assistant.ui.bookshelf

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CallMerge
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DriveFileMove
import androidx.compose.material.icons.rounded.HideImage
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.novelai.assistant.data.db.BookEntity
import com.novelai.assistant.data.db.BookSourceType

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun BookActionSheet(
    book: BookEntity,
    onDismiss: () -> Unit,
    onContinueReading: () -> Unit,
    onOpenTree: () -> Unit,
    onMoveToCategory: () -> Unit,
    onPickCover: () -> Unit,
    onClearCover: () -> Unit,
    onDelete: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
            Text(book.title, style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(10.dp))
            SimpleAction(Icons.Rounded.Menu, "继续阅读", onClick = onContinueReading)
            SimpleAction(Icons.Rounded.CallMerge, "剧情分支树", onClick = onOpenTree)
            SimpleAction(Icons.Rounded.DriveFileMove, "移动到书单", onClick = onMoveToCategory)
            if (book.sourceType != BookSourceType.BOLUOBAO) {
                SimpleAction(
                    icon = Icons.Rounded.Image,
                    label = if (book.coverUri.isNullOrBlank()) "设置封面" else "更换封面",
                    onClick = onPickCover
                )
                if (!book.coverUri.isNullOrBlank()) {
                    SimpleAction(
                        icon = Icons.Rounded.HideImage,
                        label = "恢复默认封面",
                        onClick = onClearCover
                    )
                }
            }
            SimpleAction(
                Icons.Rounded.Delete,
                "删除本书",
                tint = MaterialTheme.colorScheme.error,
                onClick = onDelete
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MoveToCategorySheet(
    bookTitle: String,
    categories: List<String>,
    onDismiss: () -> Unit,
    onSelectCategory: (String) -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
            Text("移动《$bookTitle》到书单", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(10.dp))
            categories.filter { it != "全部" }.forEach { cat ->
                SimpleAction(Icons.Rounded.DriveFileMove, cat) {
                    onSelectCategory(cat)
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

@Composable
internal fun AddCategoryDialog(
    name: String,
    onNameChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("新建书单") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = onNameChange,
                placeholder = { Text("如：奇幻 / 已读完 / AI 推演中") },
                singleLine = true
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text("创建") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

@Composable
internal fun DeleteCategoryDialog(
    category: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("删除书单「$category」？") },
        text = { Text("该书单将被删除，该书单下的书籍将保留在书架中（移至默认分类）。") },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("删除", color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}

@Composable
internal fun DeleteBookDialog(
    bookTitle: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("删除《$bookTitle》？") },
        text = { Text("章节、AI 续写与分支记录将一并删除，且不可恢复。") },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("删除", color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
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
