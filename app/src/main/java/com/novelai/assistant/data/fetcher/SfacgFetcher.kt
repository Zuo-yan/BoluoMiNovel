package com.novelai.assistant.data.fetcher

import com.novelai.assistant.data.sf.SfSessionRepository
import kotlinx.coroutines.CancellationException
import javax.inject.Inject
import javax.inject.Singleton

const val DESKTOP_UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/126.0.0.0 Safari/537.36"
data class FetchedChapter(val url: String, val title: String, val volume: String)
data class FetchedBook(val bookId: String, val title: String, val author: String, val chapters: List<FetchedChapter>, val coverUrl: String? = null)

/** SF text uses App sessions instead of scraping VIP web pages. */
@Singleton
class SfacgFetcher @Inject constructor(private val session: SfSessionRepository) {
    suspend fun fetchBook(input: String): Result<FetchedBook> = capture {
        val id = extractBookId(input) ?: error("请输入菠萝包书号或书籍链接")
        val book = session.catalog(id)
        FetchedBook(book.id, book.title, book.author, book.chapters.map {
            FetchedChapter("https://book.sfacg.com/${if (it.paid) "vip/c" else "Novel/$id/0"}/${it.id}/", it.title, it.volume)
        }, book.cover)
    }
    suspend fun fetchChapterContent(url: String): Result<List<String>> = capture {
        val id = Regex("/(\\d+)/?$").find(url)?.groupValues?.get(1) ?: error("章节地址无效")
        session.chapter(id, url.contains("/vip/")).lines().filter { it.isNotBlank() }
    }
    private suspend fun <T> capture(block: suspend () -> T): Result<T> = try { Result.success(block()) }
        catch (e: CancellationException) { throw e } catch (e: Exception) { Result.failure(e) }
    companion object {
        fun extractBookId(input: String): String? {
            val value = input.trim()
            if (value.matches(Regex("\\d{1,10}"))) return value
            val uri = runCatching { java.net.URI(value) }.getOrNull() ?: return null
            if (uri.scheme != "https" || uri.host !in listOf("book.sfacg.com", "m.sfacg.com") || uri.userInfo != null || uri.port != -1) return null
            return Regex("/Novel/(\\d{1,10})(?:/|$)", RegexOption.IGNORE_CASE).find(uri.path)?.groupValues?.get(1)
        }
    }
}
