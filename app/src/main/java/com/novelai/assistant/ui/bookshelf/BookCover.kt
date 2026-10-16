package com.novelai.assistant.ui.bookshelf

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.novelai.assistant.data.db.BookEntity
import com.novelai.assistant.data.importer.BookImportManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.URL
import java.security.MessageDigest
import kotlin.math.abs
import kotlin.math.max

/** 封面：支持本地文件、EPUB 封面、以及网络封面自动持久化离线存储 */
@Composable
fun BookCover(book: BookEntity, modifier: Modifier = Modifier, corner: androidx.compose.ui.graphics.Shape? = null) {
    val shape = corner ?: MaterialTheme.shapes.medium
    val context = LocalContext.current
    val bitmap by produceState<Bitmap?>(null, book.coverUri) {
        value = null
        val uri = book.coverUri
        if (uri.isNullOrBlank()) return@produceState
        value = withContext(Dispatchers.IO) {
            runCatching {
                val file = if (uri.startsWith("http://") || uri.startsWith("https://")) {
                    // 持久化存储到应用内部 files/book-covers 目录，离线永久可见
                    val coversDir = File(context.filesDir, "book-covers").apply { mkdirs() }
                    val hash = MessageDigest.getInstance("SHA-256").digest(uri.toByteArray()).joinToString("") { "%02x".format(it) }
                    File(coversDir, hash).also { target ->
                        if (!target.exists() || target.length() == 0L) {
                            val conn = URL(uri.replaceFirst("http://", "https://")).openConnection().apply {
                                connectTimeout = 10_000
                                readTimeout = 10_000
                            }
                            val bytes = conn.getInputStream().use { input ->
                                val out = ByteArrayOutputStream()
                                val buf = ByteArray(8192)
                                while (out.size() <= 4 * 1024 * 1024) {
                                    val n = input.read(buf)
                                    if (n < 0) break
                                    out.write(buf, 0, n)
                                }
                                out.toByteArray()
                            }
                            if (bytes.isNotEmpty() && bytes.size <= 4 * 1024 * 1024) {
                                target.writeBytes(bytes)
                            }
                        }
                    }
                } else {
                    File(uri)
                }

                if (file.exists() && file.length() > 0) {
                    val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeFile(file.path, opts)
                    val sample = max(1, max(opts.outWidth, opts.outHeight) / 512)
                    BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample })
                } else null
            }.getOrNull()
        }
    }

    Box(modifier.clip(shape).background(MaterialTheme.colorScheme.surfaceVariant, shape)) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap!!.asImageBitmap(),
                contentDescription = book.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            val colors = gradientFor(book.title)
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Brush.linearGradient(colors), shape)
            )
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
