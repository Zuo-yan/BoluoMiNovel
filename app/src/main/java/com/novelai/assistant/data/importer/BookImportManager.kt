package com.novelai.assistant.data.importer

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.provider.OpenableColumns
import com.novelai.assistant.data.db.BookEntity
import com.novelai.assistant.data.db.BookSourceType
import com.novelai.assistant.data.db.ChapterEntity
import com.novelai.assistant.data.db.ChapterDao
import com.novelai.assistant.data.db.BookDao
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.util.UUID
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.max
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** 导入调度器：识别扩展名 → 分发解析器 → 落库 */
@Singleton
class BookImportManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val txtImporter: TxtImporter,
    private val markdownImporter: MarkdownImporter,
    private val epubImporter: EpubImporter,
    private val bookDao: BookDao,
    private val chapterDao: ChapterDao
) {
    sealed class ImportResult {
        data class Success(val bookId: String, val title: String, val chapterCount: Int) : ImportResult()
        data class Failure(val message: String) : ImportResult()
    }

    suspend fun importFromUri(uri: Uri): ImportResult {
        return try {
            val fileName = queryDisplayName(uri) ?: return ImportResult.Failure("无法读取文件名")
            val ext = fileName.substringAfterLast('.', "").lowercase()
            val importer = when (ext) {
                "txt" -> txtImporter
                "md", "markdown" -> markdownImporter
                "epub" -> epubImporter
                else -> return ImportResult.Failure("暂不支持 .$ext 格式（支持 txt / md / epub）")
            }
            val parsed = context.contentResolver.openInputStream(uri)?.use {
                importer.parse(it, fileName)
            } ?: return ImportResult.Failure("无法打开文件")

            if (parsed.chapters.isEmpty()) return ImportResult.Failure("解析失败：未找到有效章节内容")

            val bookId = UUID.randomUUID().toString()
            var coverPath: String? = null
            parsed.coverBytes?.let { bytes ->
                runCatching {
                    val dir = File(context.filesDir, "covers").apply { mkdirs() }
                    val coverFile = File(dir, "$bookId.img")
                    coverFile.writeBytes(bytes)
                    coverPath = coverFile.absolutePath
                }
            }

            val book = BookEntity(
                id = bookId,
                title = parsed.title.ifBlank { fileName },
                author = parsed.author,
                coverUri = coverPath,
                sourcePath = parsed.sourcePath,
                sourceType = parsed.sourceType,
                totalChapters = parsed.chapters.size,
                createdAt = System.currentTimeMillis()
            )
            val entities = parsed.chapters.mapIndexed { i, c ->
                ChapterEntity(
                    id = UUID.randomUUID().toString(),
                    bookId = bookId,
                    chapterIndex = i,
                    title = c.title,
                    content = c.content,
                    originType = com.novelai.assistant.data.db.ChapterOriginType.ORIGINAL
                )
            }
            bookDao.insert(book)
            chapterDao.insertAll(entities)
            ImportResult.Success(bookId, book.title, entities.size)
        } catch (t: Throwable) {
            ImportResult.Failure("导入失败：${t.message ?: t.javaClass.simpleName}")
        }
    }

    private fun queryDisplayName(uri: Uri): String? {
        if (uri.scheme == "content") {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (cursor.moveToFirst() && nameIdx >= 0) {
                    return cursor.getString(nameIdx)
                }
            }
        }
        return uri.lastPathSegment
    }

    private fun coversDir(): File = File(context.filesDir, "covers").apply { mkdirs() }

    private val coverFileSeq = AtomicLong(System.currentTimeMillis())

    /**
     * 每次写封面都生成全新的文件路径。路径必须随更换而变化：
     * coverUri 不变时 Room 不会重发该行，UI 的 remember(key) 也不会重新解码，会一直显示旧封面。
     */
    private fun newCoverFile(bookId: String): File {
        var file: File
        do {
            file = File(coversDir(), "${bookId}_${coverFileSeq.incrementAndGet()}.img")
        } while (file.exists())
        return file
    }

    /** 保存抓取到的封面字节（自动封面），失败返回 null */
    fun storeCoverBytes(bookId: String, bytes: ByteArray): String? = runCatching {
        val file = File(coversDir(), "$bookId.img")
        file.writeBytes(bytes)
        file.absolutePath
    }.getOrNull()

    /**
     * 从系统相册导入封面：EXIF 转正、最长边限制 1024、压缩为 JPEG 落盘，并清理旧封面文件。
     * 每次写入新文件名（见 [newCoverFile]），成功返回新封面绝对路径，失败返回 null。
     */
    suspend fun setCoverFromUri(bookId: String, oldCoverPath: String?, uri: Uri): String? =
        withContext(Dispatchers.IO) {
            runCatching {
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                context.contentResolver.openInputStream(uri)?.use {
                    BitmapFactory.decodeStream(it, null, bounds)
                }
                if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@runCatching null

                val sample = max(1, max(bounds.outWidth, bounds.outHeight) / 1024)
                val decoded = context.contentResolver.openInputStream(uri)?.use {
                    BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
                } ?: return@runCatching null

                val rotation = context.contentResolver.openInputStream(uri)?.use { stream ->
                    when (ExifInterface(stream).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                        ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                        ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                        ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                        else -> 0f
                    }
                } ?: 0f

                var bitmap = if (rotation != 0f) {
                    val matrix = Matrix().apply { postRotate(rotation) }
                    Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
                } else decoded

                val longest = maxOf(bitmap.width, bitmap.height)
                if (longest > 1024) {
                    val scale = 1024f / longest
                    bitmap = Bitmap.createScaledBitmap(
                        bitmap,
                        (bitmap.width * scale).toInt().coerceAtLeast(1),
                        (bitmap.height * scale).toInt().coerceAtLeast(1),
                        true
                    )
                }

                val file = newCoverFile(bookId)
                file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }
                bitmap.recycle()

                if (!oldCoverPath.isNullOrBlank() && oldCoverPath != file.absolutePath) {
                    deleteCoverFile(oldCoverPath)
                }
                file.absolutePath
            }.getOrNull()
        }

    /** 清除封面文件（仅允许删除应用私有 covers 目录内的文件） */
    fun clearCover(oldCoverPath: String?) {
        if (oldCoverPath.isNullOrBlank()) return
        deleteCoverFile(oldCoverPath)
    }

    private fun deleteCoverFile(path: String) {
        runCatching {
            val file = File(path)
            val coversRoot = File(context.filesDir, "covers").canonicalPath
            if (file.exists() && file.canonicalPath.startsWith(coversRoot)) {
                file.delete()
            }
        }
    }

    companion object {
        fun sourceTypeLabel(type: BookSourceType): String = when (type) {
            BookSourceType.LOCAL_TXT -> "TXT"
            BookSourceType.LOCAL_MD -> "MD"
            BookSourceType.LOCAL_EPUB -> "EPUB"
            BookSourceType.FANQIE -> "番茄"
            BookSourceType.BOLUOBAO -> "菠萝包"
        }
    }
}
