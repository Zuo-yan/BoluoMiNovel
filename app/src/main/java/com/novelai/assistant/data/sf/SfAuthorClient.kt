package com.novelai.assistant.data.sf

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import org.jsoup.Jsoup
import javax.inject.Inject
import javax.inject.Singleton

data class SfAuthorWork(val id: String, val title: String)
data class SfAuthorPortfolio(
    val name: String,
    val avatar: String?,
    val works: List<SfAuthorWork>
)

/** Reads the public author portfolio rendered by the official mobile book page. */
@Singleton
class SfAuthorClient @Inject constructor(private val http: HttpClient) {
    suspend fun portfolio(bookId: String): SfAuthorPortfolio {
        require(bookId.matches(Regex("\\d+"))) { "无效书号" }
        val response = try {
            http.get("https://m.sfacg.com/b/$bookId/") {
                header(HttpHeaders.UserAgent, MOBILE_USER_AGENT)
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (_: Exception) {
            throw SfException(SfFailureKind.NETWORK, "无法读取作者作品集，请检查网络后重试")
        }
        when (response.status.value) {
            429 -> throw SfException(SfFailureKind.RATE_LIMIT, "作者作品集请求被限流，请稍后重试")
            !in 200..299 -> throw SfException(SfFailureKind.NETWORK, "作者作品集返回 HTTP ${response.status.value}")
        }
        return SfAuthorProtocol.parse(response.bodyAsText())
    }

    private companion object {
        const val MOBILE_USER_AGENT =
            "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 Chrome/126.0 Mobile Safari/537.36"
    }
}

object SfAuthorProtocol {
    fun parse(html: String): SfAuthorPortfolio {
        val doc = Jsoup.parse(html, "https://m.sfacg.com/")
        val section = doc.selectFirst("ul.book_Author")
            ?: throw SfException(SfFailureKind.INVALID, "作者作品集格式已变化")
        val identity = section.selectFirst("li:first-child")
            ?: throw SfException(SfFailureKind.INVALID, "作者作品集缺少作者信息")
        val name = identity.ownText().trim()
            .ifBlank { identity.text().removePrefix(identity.selectFirst("img")?.text().orEmpty()).trim() }
        val avatar = identity.selectFirst("img[src]")?.absUrl("src")?.takeIf { it.isNotBlank() }
        val works = section.select("a[href^=/b/]").mapNotNull { link ->
            val id = Regex("^/b/(\\d+)/?$").find(link.attr("href"))?.groupValues?.get(1)
                ?: return@mapNotNull null
            val title = link.text().trim()
            if (title.isBlank()) null else SfAuthorWork(id, title)
        }.distinctBy { it.id }
        if (name.isBlank()) throw SfException(SfFailureKind.INVALID, "作者作品集缺少作者昵称")
        return SfAuthorPortfolio(name, avatar, works)
    }
}
