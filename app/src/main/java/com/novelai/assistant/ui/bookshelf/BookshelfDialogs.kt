package com.novelai.assistant.ui.bookshelf

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CallMerge
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DriveFileMove
import androidx.compose.material.icons.rounded.HideImage
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.novelai.assistant.data.db.BookEntity
import com.novelai.assistant.data.db.BookSourceType
import com.novelai.assistant.data.importer.BookImportManager
import com.novelai.assistant.ui.components.AdaptiveGlassDialog
import com.novelai.assistant.ui.components.AdaptiveGlassSheet
import com.novelai.assistant.ui.components.GlassCapsuleButton
import com.novelai.assistant.ui.components.LocalAdaptiveMetrics
import com.novelai.assistant.ui.theme.NovelColors

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
    val adaptive = LocalAdaptiveMetrics.current
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val primaryText = if (isDark) NovelColors.GlassOnDarkText else NovelColors.GlassOnLightText
    val secondaryText = if (isDark) {
        NovelColors.GlassOnDarkText.copy(alpha = 0.72f)
    } else {
        NovelColors.GlassOnLightText.copy(alpha = 0.65f)
    }
    val accent = if (isDark) NovelColors.GlassAccentBlueDark else MaterialTheme.colorScheme.primary

    AdaptiveGlassSheet(
        onDismissRequest = onDismiss,
        maxWidth = adaptive.maxDialogSheetWidth
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            BookCover(
                book = book,
                modifier = Modifier
                    .width(46.dp)
                    .aspectRatio(0.72f),
                corner = RoundedCornerShape(10.dp),
                elevation = 4.dp,
                showBadges = false
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = book.title,
                    style = MaterialTheme.typography.titleLarge,
                    color = primaryText,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "${BookImportManager.sourceTypeLabel(book.sourceType)} · ${book.category} · ${book.readingProgressPercent.toInt()}%",
                    style = MaterialTheme.typography.bodySmall,
                    color = secondaryText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Spacer(Modifier.height(12.dp))
        HorizontalDivider(
            color = if (isDark) Color.White.copy(alpha = 0.12f) else Color.Black.copy(alpha = 0.08f)
        )
        Spacer(Modifier.height(6.dp))

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 420.dp)
                .verticalScroll(rememberScrollState())
        ) {
            SimpleAction(
                icon = Icons.Rounded.Menu,
                label = "继续阅读",
                tint = accent,
                textColor = primaryText,
                isDark = isDark,
                onClick = onContinueReading
            )
            SimpleAction(
                icon = Icons.Rounded.CallMerge,
                label = "剧情分支树",
                tint = NovelColors.Teal,
                textColor = primaryText,
                isDark = isDark,
                onClick = onOpenTree
            )
            SimpleAction(
                icon = Icons.Rounded.DriveFileMove,
                label = "移动到书单",
                tint = NovelColors.Amber,
                textColor = primaryText,
                isDark = isDark,
                onClick = onMoveToCategory
            )
            if (book.sourceType != BookSourceType.BOLUOBAO) {
                SimpleAction(
                    icon = Icons.Rounded.Image,
                    label = if (book.coverUri.isNullOrBlank()) "设置封面" else "更换封面",
                    tint = NovelColors.Mint,
                    textColor = primaryText,
                    isDark = isDark,
                    onClick = onPickCover
                )
                if (!book.coverUri.isNullOrBlank()) {
                    SimpleAction(
                        icon = Icons.Rounded.HideImage,
                        label = "恢复默认封面",
                        tint = secondaryText,
                        textColor = primaryText,
                        isDark = isDark,
                        onClick = onClearCover
                    )
                }
            }
            SimpleAction(
                icon = Icons.Rounded.Delete,
                label = "删除本书",
                tint = MaterialTheme.colorScheme.error,
                textColor = MaterialTheme.colorScheme.error,
                isDark = isDark,
                onClick = onDelete
            )
        }
    }
}

@Composable
internal fun MoveToCategorySheet(
    bookTitle: String,
    categories: List<String>,
    currentCategory: String? = null,
    onDismiss: () -> Unit,
    onSelectCategory: (String) -> Unit
) {
    val adaptive = LocalAdaptiveMetrics.current
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val primaryText = if (isDark) NovelColors.GlassOnDarkText else NovelColors.GlassOnLightText
    val secondaryText = if (isDark) {
        NovelColors.GlassOnDarkText.copy(alpha = 0.72f)
    } else {
        NovelColors.GlassOnLightText.copy(alpha = 0.65f)
    }
    val accent = if (isDark) NovelColors.GlassAccentBlueDark else MaterialTheme.colorScheme.primary
    val targets = categories.filter { it != "全部" }

    AdaptiveGlassSheet(
        onDismissRequest = onDismiss,
        maxWidth = adaptive.maxDialogSheetWidth
    ) {
        Text(
            text = "移动《$bookTitle》到书单",
            style = MaterialTheme.typography.titleLarge,
            color = primaryText,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.height(10.dp))
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 360.dp)
                .verticalScroll(rememberScrollState())
        ) {
            if (targets.isEmpty()) {
                Text(
                    text = "暂无自定义书单，请先在书架顶部创建新书单。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = secondaryText,
                    modifier = Modifier.padding(vertical = 12.dp)
                )
            } else {
                targets.forEach { cat ->
                    val isCurrent = cat == currentCategory
                    SimpleAction(
                        icon = if (isCurrent) Icons.Rounded.Check else Icons.Rounded.DriveFileMove,
                        label = if (isCurrent) "$cat（当前）" else cat,
                        tint = if (isCurrent) accent else primaryText.copy(alpha = 0.85f),
                        textColor = if (isCurrent) accent else primaryText,
                        isDark = isDark,
                        onClick = { onSelectCategory(cat) }
                    )
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = "提示：可在书架顶部「新建书单」创建更多分类",
            style = MaterialTheme.typography.bodySmall,
            color = secondaryText
        )
    }
}

@Composable
internal fun AddCategoryDialog(
    name: String,
    onNameChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    val adaptive = LocalAdaptiveMetrics.current
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val primaryText = if (isDark) NovelColors.GlassOnDarkText else NovelColors.GlassOnLightText
    val secondaryText = if (isDark) {
        NovelColors.GlassOnDarkText.copy(alpha = 0.72f)
    } else {
        NovelColors.GlassOnLightText.copy(alpha = 0.65f)
    }

    AdaptiveGlassDialog(
        onDismissRequest = onDismiss,
        maxWidth = adaptive.maxDialogSheetWidth
    ) {
        Text(
            text = "新建书单",
            style = MaterialTheme.typography.titleLarge,
            color = primaryText
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = name,
            onValueChange = onNameChange,
            placeholder = {
                Text(
                    text = "如：奇幻 / 已读完 / AI 推演中",
                    color = secondaryText
                )
            },
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = primaryText,
                unfocusedTextColor = primaryText,
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = if (isDark) Color.White.copy(alpha = 0.24f) else Color.Black.copy(alpha = 0.16f)
            ),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(18.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End)
        ) {
            GlassCapsuleButton(
                text = "取消",
                filled = false,
                onClick = onDismiss
            )
            GlassCapsuleButton(
                text = "创建",
                filled = true,
                enabled = name.isNotBlank(),
                onClick = onConfirm
            )
        }
    }
}

@Composable
internal fun DeleteCategoryDialog(
    category: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    val adaptive = LocalAdaptiveMetrics.current
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val primaryText = if (isDark) NovelColors.GlassOnDarkText else NovelColors.GlassOnLightText
    val secondaryText = if (isDark) {
        NovelColors.GlassOnDarkText.copy(alpha = 0.78f)
    } else {
        NovelColors.GlassOnLightText.copy(alpha = 0.72f)
    }

    AdaptiveGlassDialog(
        onDismissRequest = onDismiss,
        maxWidth = adaptive.maxDialogSheetWidth
    ) {
        Text(
            text = "删除书单「$category」？",
            style = MaterialTheme.typography.titleLarge,
            color = primaryText
        )
        Spacer(Modifier.height(10.dp))
        Text(
            text = "该书单将被删除，该书单下的书籍将保留在书架中（移至默认分类）。",
            style = MaterialTheme.typography.bodyMedium,
            color = secondaryText
        )
        Spacer(Modifier.height(18.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End)
        ) {
            GlassCapsuleButton(
                text = "取消",
                filled = false,
                onClick = onDismiss
            )
            DestructiveCapsuleButton(
                text = "删除",
                onClick = onConfirm
            )
        }
    }
}

@Composable
internal fun DeleteBookDialog(
    bookTitle: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    val adaptive = LocalAdaptiveMetrics.current
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val primaryText = if (isDark) NovelColors.GlassOnDarkText else NovelColors.GlassOnLightText
    val secondaryText = if (isDark) {
        NovelColors.GlassOnDarkText.copy(alpha = 0.78f)
    } else {
        NovelColors.GlassOnLightText.copy(alpha = 0.72f)
    }

    AdaptiveGlassDialog(
        onDismissRequest = onDismiss,
        maxWidth = adaptive.maxDialogSheetWidth
    ) {
        Text(
            text = "删除《$bookTitle》？",
            style = MaterialTheme.typography.titleLarge,
            color = primaryText
        )
        Spacer(Modifier.height(10.dp))
        Text(
            text = "章节、AI 续写与分支记录将一并删除，且不可恢复。",
            style = MaterialTheme.typography.bodyMedium,
            color = secondaryText
        )
        Spacer(Modifier.height(18.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End)
        ) {
            GlassCapsuleButton(
                text = "取消",
                filled = false,
                onClick = onDismiss
            )
            DestructiveCapsuleButton(
                text = "删除",
                onClick = onConfirm
            )
        }
    }
}

@Composable
private fun DestructiveCapsuleButton(
    text: String,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(percent = 50)
    Box(
        modifier = Modifier
            .height(38.dp)
            .clip(shape)
            .background(MaterialTheme.colorScheme.error)
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onError,
            maxLines = 1
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SimpleAction(
    icon: ImageVector,
    label: String,
    tint: Color,
    textColor: Color,
    isDark: Boolean,
    onClick: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .combinedClickable(onClick = onClick, onLongClick = onClick)
            .padding(vertical = 10.dp, horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(tint.copy(alpha = if (isDark) 0.20f else 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.width(12.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = textColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
