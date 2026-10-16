package com.novelai.assistant.ui.components

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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.URL
import java.security.MessageDigest
import kotlin.math.max

/** 统一异步网络/本地封面组件：具备磁盘二级持久缓存与自适应按需采样解码 */
@Composable
fun AsyncCover(
    title: String,
    uri: String?,
    modifier: Modifier = Modifier,
    shape: Shape = MaterialTheme.shapes.medium,
    contentScale: ContentScale = ContentScale.Crop,
    maxDimension: Int = 512,
    placeholder: @Composable () -> Unit = {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.secondaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = title.take(4),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(4.dp)
            )
        }
    }
) {
    val context = LocalContext.current
    val bitmap by produceState<Bitmap?>(null, uri) {
        value = null
        if (uri.isNullOrBlank()) return@produceState
        value = withContext(Dispatchers.IO) {
            runCatching {
                val file = if (uri.startsWith("http://") || uri.startsWith("https://")) {
                    val coversDir = File(context.filesDir, "book-covers").apply { mkdirs() }
                    val hash = MessageDigest.getInstance("SHA-256").digest(uri.toByteArray()).joinToString("") { "%02x".format(it) }
                    File(coversDir, hash).also { target ->
                        if (!target.exists() || target.length() == 0L) {
                            val conn = URL(uri.replaceFirst("http://", "https://")).openConnection().apply {
                                connectTimeout = 8_000
                                readTimeout = 8_000
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
                    val sample = max(1, max(opts.outWidth, opts.outHeight) / maxDimension)
                    BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample })
                } else null
            }.getOrNull()
        }
    }

    Box(modifier = modifier.clip(shape), contentAlignment = Alignment.Center) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap!!.asImageBitmap(),
                contentDescription = title,
                contentScale = contentScale,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            placeholder()
        }
    }
}

/** 中文数量格式化工具函数 */
fun formatChineseCount(count: Long, unit: String = ""): String {
    return when {
        count >= 100_000_000L -> "%.1f亿%s".format(count / 100_000_000.0, unit)
        count >= 10_000L -> "%.1f万%s".format(count / 10_000.0, unit)
        else -> "$count$unit"
    }
}
