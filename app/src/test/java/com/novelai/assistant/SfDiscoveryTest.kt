package com.novelai.assistant

import com.novelai.assistant.data.sf.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class SfDiscoveryTest {
    private fun ok(data: String) = SfResponse(200, """{"status":{"httpCode":200},"data":$data}""")
    private fun api(block: suspend (String) -> SfResponse) = SfApi(SfTransport { path, headers, body ->
        assertNull(headers["Cookie"])
        assertNull(body)
        block(path)
    }, SfProtocol.COMPATIBLE_DEVICE)

    @Test fun recommendationsUnwrapBooksAndPreserveCoverAndTags() = runBlocking {
        val paths = mutableListOf<String>()
        val client = api { path ->
            paths += path
            ok("""{"newPush":[{"novel":{"novelId":123,"novelName":"新作","authorName":"作者","expand":{"bigNovelCover":"https://rss.sfacg.com/cover.jpg","sysTags":[{"tagName":"异世界"}]}}},{"novel":{"novelId":123,"novelName":"重复"}},{"novel":{"novelId":"bad"}}]}""")
        }
        val books = client.newRecommendations(2)
        assertEquals(1, books.size)
        assertEquals("新作", books.single().title)
        assertEquals("作者", books.single().author)
        assertEquals(listOf("异世界"), books.single().tags)
        assertEquals("https://rss.sfacg.com/cover.jpg", books.single().cover)
        assertTrue(paths.single().contains("pushNames=newpush&page=1"))
    }

    @Test fun relatedListsKeepFullCountSeparateFromPartialPreview() = runBlocking {
        val client = api { path ->
            assertEquals("novel/540307/bookList?page=1&size=20", path)
            ok("""{"items":[{"bookListID":76,"title":"读者书单","summary":"<b>推荐</b>","novelNum":30,"user":{"nickName":"读者"},"novels":[{"novelId":1,"novelName":"第一本"},{"novelId":2,"novelName":"第二本"},{"novelId":3,"novelName":"第三本"}]}]}""")
        }
        val result = client.relatedBookLists("540307", 2).single()
        assertEquals("76", result.id)
        assertEquals(30, result.totalBooks)
        assertEquals(3, result.books.size)
        assertEquals("推荐", result.summary)
        assertEquals("读者", result.author)
    }

    @Test fun malformedFeedsAreErrorsWhileRealEmptyFeedsStayEmpty() = runBlocking {
        for (related in listOf(false, true)) {
            val bad = api { ok("{}") }
            try {
                if (related) bad.relatedBookLists("1") else bad.newRecommendations()
                fail("Malformed payload cannot look like a genuinely empty feed")
            } catch (e: SfException) { assertEquals(SfFailureKind.INVALID, e.kind) }
            val empty = api { ok(if (related) """{"items":[]}""" else """{"newPush":[]}""") }
            assertTrue(if (related) empty.relatedBookLists("1").isEmpty() else empty.newRecommendations().isEmpty())
        }
    }

    @Test fun invalidRelatedBookIdDoesNotReachNetwork() = runBlocking {
        var calls = 0
        val client = api { calls++; ok("{}") }
        try { client.relatedBookLists("../../user"); fail() } catch (_: IllegalArgumentException) { }
        assertEquals(0, calls)
    }

    @Test fun officialAuthorPortfolioKeepsRealBookIdsAndDropsNavigationLinks() {
        val portfolio = SfAuthorProtocol.parse(
            """
            <ul class="book_Author">
              <li><img src="//rss.sfacg.com/avatar.jpg" width="60"><br>子与我非鱼</li>
              <li><div><span>作品集：</span><br>
                <a href="/b/540307/"><span>凤傲天小说里的黄毛反派也想幸福</span></a><br>
                <a href="/b/128715/"><span>就算穿越成龙族萝莉吾也要开后宫</span></a><br>
                <a href="/b/540307/"><span>重复作品</span></a>
              </div><div>请勿转载</div></li>
            </ul>
            """
        )
        assertEquals("子与我非鱼", portfolio.name)
        assertEquals("https://rss.sfacg.com/avatar.jpg", portfolio.avatar)
        assertEquals(listOf("540307", "128715"), portfolio.works.map { it.id })
        assertEquals("凤傲天小说里的黄毛反派也想幸福", portfolio.works.first().title)
    }

    @Test fun malformedAuthorPortfolioIsAnErrorNotAnEmptyPortfolio() {
        try {
            SfAuthorProtocol.parse("<html><body>没有作者模块</body></html>")
            fail("A changed page must not look like an author with zero works")
        } catch (e: SfException) {
            assertEquals(SfFailureKind.INVALID, e.kind)
        }
    }
}
