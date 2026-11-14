package com.novelai.assistant.ui.bookshelf

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.novelai.assistant.data.db.BookEntity
import com.novelai.assistant.data.importer.BookImportManager
import com.novelai.assistant.ui.components.AsyncCover
import com.novelai.assistant.ui.theme.NovelColors
import kotlin.math.abs

/**
 * 书籍封面：基于统一 AsyncCover 实现，提供大圆角 Squircle 阴影、
 * 书脊光影渐变备用封面，以及高对比度液态玻璃格式与阅读进度角标。
 */
@Composable
fun BookCover(
    book: BookEntity,
    modifier: Modifier = Modifier,
    corner: Shape? = null,
    elevation: Dp = 8.dp,
    showBadges: Boolean = false
) {
    val shape = corner ?: RoundedCornerShape(18.dp)
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val borderBrush = if (isDark) {
        Brush.verticalGradient(
            listOf(
                Color.White.copy(alpha = 0.24f),
                Color.White.copy(alpha = 0.06f),
                Color.White.copy(alpha = 0.14f)
            )
        )
    } else {
        Brush.verticalGradient(
            listOf(
                Color.White.copy(alpha = 0.72f),
                Color.Black.copy(alpha = 0.06f),
                Color.Black.copy(alpha = 0.14f)
            )
        )
    }

    val elevatedModifier = if (elevation > 0.dp) {
        modifier.shadow(
            elevation = elevation,
            shape = shape,
            clip = false,
            ambientColor = Color.Black.copy(alpha = if (isDark) 0.45f else 0.18f),
            spotColor = Color.Black.copy(alpha = if (isDark) 0.55f else 0.24f)
        )
    } else {
        modifier
    }

    Box(
        modifier = elevatedModifier
            .clip(shape)
            .border(width = 0.6.dp, brush = borderBrush, shape = shape)
    ) {
        AsyncCover(
            title = book.title,
            uri = book.coverUri,
            modifier = Modifier.fillMaxSize(),
            shape = shape,
            maxDimension = 512,
            placeholder = {
                val colors = gradientFor(book.title)
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(Brush.linearGradient(colors), shape)
                ) {
                    // 书籍左侧书脊高光折痕
                    Box(
                        Modifier
                            .fillMaxHeight()
                            .width(10.dp)
                            .background(
                                Brush.horizontalGradient(
                                    listOf(
                                        Color.Black.copy(alpha = 0.20f),
                                        Color.White.copy(alpha = 0.18f),
                                        Color.Transparent
                                    )
                                )
                            )
                    )
                    Text(
                        text = book.title,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = Color.White,
                        textAlign = TextAlign.Center,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(horizontal = 14.dp, vertical = 12.dp)
                    )
                    if (!showBadges) {
                        Text(
                            text = BookImportManager.sourceTypeLabel(book.sourceType),
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.White.copy(alpha = 0.82f),
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(8.dp)
                        )
                    }
                }
            }
        )

        if (showBadges) {
            // 底部渐变暗角保证角标与进度条在亮色封面上也清晰可读
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(44.dp)
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, Color.Black.copy(alpha = 0.44f))
                        )
                    )
            )

            CoverGlassPill(
                text = BookImportManager.sourceTypeLabel(book.sourceType),
                isDark = isDark,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
            )

            val progress = book.readingProgressPercent.coerceIn(0f, 100f)
            val progressText = when {
                progress >= 99.5f -> "已读完"
                progress > 0f -> "${progress.toInt()}%"
                else -> "未读"
            }
            CoverGlassPill(
                text = progressText,
                isDark = isDark,
                accent = progress > 0f,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 8.dp, bottom = 10.dp)
            )

            if (progress > 0f) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(3.dp)
                        .background(Color.White.copy(alpha = 0.22f))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(fraction = (progress / 100f).coerceIn(0.03f, 1f))
                            .background(
                                if (isDark) NovelColors.GlassAccentBlueDark else MaterialTheme.colorScheme.primary
                            )
                    )
                }
            }
        }
    }
}

@Composable
private fun CoverGlassPill(
    text: String,
    isDark: Boolean,
    modifier: Modifier = Modifier,
    accent: Boolean = false
) {
    val pillShape = RoundedCornerShape(percent = 50)
    // 封面图文复杂，深浅主题下均采用高对比度半透明磨砂底与清晰文字
    val bgColor = when {
        accent && isDark -> NovelColors.Indigo.copy(alpha = 0.86f)
        accent -> MaterialTheme.colorScheme.primary.copy(alpha = 0.88f)
        isDark -> Color(0xCC12141C)
        else -> Color(0xDBFFFFFF)
    }
    val textColor = when {
        accent -> Color.White
        isDark -> NovelColors.GlassOnDarkText
        else -> NovelColors.GlassOnLightText
    }
    val borderColor = when {
        accent -> Color.White.copy(alpha = 0.36f)
        isDark -> Color.White.copy(alpha = 0.22f)
        else -> Color.Black.copy(alpha = 0.08f)
    }

    Box(
        modifier = modifier
            .clip(pillShape)
            .background(bgColor, pillShape)
            .border(0.6.dp, borderColor, pillShape)
            .padding(horizontal = 7.dp, vertical = 2.5.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 10.sp,
                lineHeight = 12.sp,
                fontWeight = FontWeight.SemiBold
            ),
            color = textColor,
            maxLines = 1
        )
    }
}

private fun gradientFor(seed: String): List<Color> {
    val palettes = listOf(
        listOf(Color(0xFF5856D6), Color(0xFF7B79FF)),
        listOf(Color(0xFF30B0C7), Color(0xFF64D2FF)),
        listOf(Color(0xFFFF6482), Color(0xFFFF9F0A)),
        listOf(Color(0xFF00C7BE), Color(0xFF30D158)),
        listOf(Color(0xFFB0578D), Color(0xFF7B4B94)),
        listOf(Color(0xFF4E7E8C), Color(0xFF30B0C7))
    )
    return palettes[abs(seed.hashCode()) % palettes.size]
}
