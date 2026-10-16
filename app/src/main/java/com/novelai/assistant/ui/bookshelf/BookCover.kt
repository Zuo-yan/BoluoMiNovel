package com.novelai.assistant.ui.bookshelf

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.novelai.assistant.data.db.BookEntity
import com.novelai.assistant.data.importer.BookImportManager
import com.novelai.assistant.ui.components.AsyncCover
import kotlin.math.abs

/** 封面：基于统一 AsyncCover 实现，提供书名渐变备用背景 */
@Composable
fun BookCover(book: BookEntity, modifier: Modifier = Modifier, corner: Shape? = null) {
    val shape = corner ?: MaterialTheme.shapes.medium
    AsyncCover(
        title = book.title,
        uri = book.coverUri,
        modifier = modifier,
        shape = shape,
        maxDimension = 512,
        placeholder = {
            val colors = gradientFor(book.title)
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Brush.linearGradient(colors), shape)
            ) {
                Text(
                    text = book.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                    textAlign = TextAlign.Center,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(12.dp)
                )
                Text(
                    text = BookImportManager.sourceTypeLabel(book.sourceType),
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White.copy(alpha = 0.75f),
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(8.dp)
                )
            }
        }
    )
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
