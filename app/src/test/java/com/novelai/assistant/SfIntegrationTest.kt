package com.novelai.assistant

import com.novelai.assistant.data.db.*
import com.novelai.assistant.data.fetcher.SfacgFetcher
import com.novelai.assistant.data.sf.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipInputStream
import javax.xml.parsers.DocumentBuilderFactory

class SfIntegrationTest {
    private val device = "910D166A-736E-3231-8B21-8D12DFD75F16"
    private fun ok(data: String) = SfResponse(200, """{"status":{"httpCode":200},"data":$data}""")
    private fun obj(value: String) = Json.parseToJsonElement(value).jsonObject

    @Test fun signatureMatchesIndependentPublicProtocolVector() {
        assertEquals("6D62ABB5F366C39D86C27C870AB19B12", SfProtocol.signature("00112233-4455-6677-8899-AABBCCDDEEFF", 1728000000000, device))
    }

    @Test fun appLoginExtractsHttpOnlyCookiesAndValidatesAccount() = runBlocking {
        val calls = mutableListOf<String>()
        val api = SfApi(SfTransport { path, headers, body ->
            calls += path
            assertTrue(headers.getValue("sfsecurity").contains("sign="))
            when (path) {
                "sessions" -> {
                    assertTrue(body!!.contains("password"))
                    ok("{}").copy(cookies = listOf(".SFCommunity=TEST_A; Path=/; HttpOnly", "session_APP=TEST_B; Path=/; Secure"))
                }
                "user" -> { assertEquals(".SFCommunity=TEST_A; session_APP=TEST_B", headers["Cookie"]); ok("""{"accountId":42,"nickName":"读者"}""") }
                else -> error("unexpected request")
            }
        }, device)
        val (account, cookie) = api.login("test-user", "test-password")
        assertEquals("42", account.id); assertEquals("读者", account.name)
        assertTrue(cookie.contains("session_APP=")); assertEquals(listOf("sessions", "user"), calls)
    }

    @Test fun pcSessionCannotBeAcceptedAsAppSession() {
        try { SfProtocol.loginCookie(listOf(".SFCommunity=A; session_PC=B")); fail() }
        catch (e: SfException) { assertEquals(SfFailureKind.INVALID, e.kind) }
    }

    @Test fun categoryAndMultipleTagsUseRealFilterParametersOnEveryPage() = runBlocking {
        val paths = mutableListOf<String>()
        val api = SfApi(SfTransport { path, _, _ -> paths += path; ok("[]") }, device)
        val tags = SfBrowseFilter(tagIds = setOf(58, 22))
        val combined = tags.copy(categoryId = 21)
        api.browseFiltered(combined, 1)
        api.browseFiltered(combined, 2)
        api.browseFiltered(combined.copy(tagIds = combined.tagIds - 58), 1, sort = "updatetime")
        api.browseFiltered(combined.copy(tagIds = emptySet()), 1)
        assertTrue(paths[0].startsWith("novels/21/sysTags/novels?"))
        assertTrue(paths[0].contains("systagids=22,58"))
        assertTrue(paths[0].contains("page=0"))
        assertTrue(paths[1].contains("page=1"))
        assertTrue(paths[1].contains("systagids=22,58"))
        assertTrue(paths[2].contains("systagids=22&"))
        assertTrue(paths[2].contains("sort=updatetime"))
        assertTrue(paths[3].startsWith("novels/21/"))
        assertFalse(paths[3].contains("systagids="))
        assertTrue(paths.none { it.startsWith("search/") || it.contains("sysTagId=") })
        assertEquals(setOf(58, 22), tags.tagIds)
        assertEquals(0, tags.categoryId)
    }

    @Test fun categoryNamesAndIdsComeFromPlatformMetadata() = runBlocking {
        val api = SfApi(SfTransport { path, _, _ ->
            assertEquals("noveltypes", path)
            ok("""[{"typeId":21,"typeName":"魔幻"},{"typeId":24,"typeName":"科幻"}]""")
        }, device)
        assertEquals(listOf(SfCategory(21, "魔幻"), SfCategory(24, "科幻")), api.fetchCategories())
    }

    @Test fun signatureRejectionStopsImmediately() = runBlocking {
        var calls = 0
        val api = SfApi(SfTransport { _, _, _ -> calls++; SfResponse(417, """{"status":{"httpCode":417}}""") }, device)
        try { api.inspect("test"); fail() } catch (e: SfException) { assertEquals(SfFailureKind.SIGNATURE, e.kind) }
        assertEquals(1, calls)
    }

    @Test fun anonymousSignatureRejectionRetriesWithFreshHeadersAndNoCookie() = runBlocking {
        val security = mutableListOf<String>()
        val api = SfApi(SfTransport { _, headers, _ ->
            assertNull(headers["Cookie"])
            security += headers.getValue("sfsecurity")
            if (security.size < 3) SfResponse(200, """{"status":{"httpCode":417}}""")
            else ok("""{"content":"免费正文"}""")
        }, device)
        assertEquals("免费正文", api.chapter("123", "unused-private-cookie", false) { it })
        assertEquals(3, security.size)
        assertEquals(3, security.distinct().size)
        assertFalse(api.guard.state.value.blocked)
    }

    @Test fun anonymousSignatureRetriesAreBoundedAndDoNotDemandLogin() = runBlocking {
        var calls = 0
        val api = SfApi(SfTransport { _, _, _ -> calls++; SfResponse(417, "") }, device)
        try { api.chapter("123", "", false) { it }; fail() }
        catch (e: SfException) {
            assertEquals(SfFailureKind.SIGNATURE, e.kind)
            assertFalse(e.message!!.contains("登录"))
            assertFalse(e.message!!.contains("已停止请求"))
        }
        assertEquals(3, calls)
        assertFalse(api.guard.state.value.blocked)
    }

    @Test fun paidSignatureRejectionAndLoginAreNeverAutomaticallyRetried() = runBlocking {
        var calls = 0
        val api = SfApi(SfTransport { _, _, _ -> calls++; SfResponse(417, """{"status":{"httpCode":417}}""") }, device)
        try { api.chapter("123", "private-cookie", true) { it }; fail() }
        catch (e: SfException) { assertEquals(SfFailureKind.SIGNATURE, e.kind) }
        assertEquals(1, calls)
        try { api.login("test", "test-password"); fail() }
        catch (e: SfException) { assertEquals(SfFailureKind.SIGNATURE, e.kind) }
        assertEquals(2, calls)
    }

    @Test fun anonymousRetriesStopAtRiskOrRateLimitOrClientRejection() = runBlocking {
        for ((response, kind) in listOf(
            SfResponse(422, """{"status":{"httpCode":422,"errorCode":1148}}""") to SfFailureKind.RISK,
            SfResponse(429, "<html>rate limit</html>", retryAfterMillis = 60_000) to SfFailureKind.RATE_LIMIT,
            SfResponse(417, "<html>captcha required</html>") to SfFailureKind.RISK,
            SfResponse(401, """{"status":{"httpCode":401,"errorCode":507}}""") to SfFailureKind.CLIENT
        )) {
            var calls = 0
            val api = SfApi(SfTransport { _, _, _ ->
                calls++
                if (calls == 1) SfResponse(417, """{"status":{"httpCode":417}}""") else response
            }, device)
            try { api.chapter("123", "", false) { it }; fail() }
            catch (e: SfException) { assertEquals(kind, e.kind) }
            assertEquals(2, calls)
        }
    }

    @Test fun cancellationDuringAnonymousSignatureBackoffPreventsRetry() = runBlocking {
        var calls = 0
        val api = SfApi(SfTransport { _, _, _ ->
            calls++
            SfResponse(417, """{"status":{"httpCode":417}}""")
        }, device)
        val result = kotlinx.coroutines.withTimeoutOrNull(200) { api.chapter("123", "", false) { it } }
        assertNull(result)
        assertEquals(1, calls)
    }

    @Test fun riskStopsImmediately() = runBlocking {
        var calls = 0
        val api = SfApi(SfTransport { _, _, _ -> calls++; SfResponse(422, """{"status":{"httpCode":422,"errorCode":1148}}""") }, device)
        try { api.inspect("test"); fail() } catch (e: SfException) { assertEquals(SfFailureKind.RISK, e.kind) }
        assertEquals(1, calls)
    }

    @Test fun invalidLoginDoesNotExposeServerBodyOrSubmittedPassword() = runBlocking {
        val api = SfApi(SfTransport { _, _, _ -> SfResponse(401, """{"status":{"httpCode":401,"msg":"secret-password session_cookie"}}""") }, device)
        try { api.login("test", "secret-password"); fail() }
        catch (e: SfException) { assertEquals(SfFailureKind.AUTH, e.kind); assertFalse(e.message!!.contains("secret-password")) }
    }

    @Test fun cancellationPropagates() = runBlocking {
        val api = SfApi(SfTransport { _, _, _ -> throw CancellationException("cancel") }, device)
        try { api.inspect("test"); fail() } catch (_: CancellationException) { }
    }

    @Test fun deviceRejectionIsNotMisreportedAsExpiredLogin() = runBlocking {
        val api = SfApi(SfTransport { _, _, _ -> SfResponse(401, """{"status":{"httpCode":401,"errorCode":507}}""") }, device)
        try { api.inspect("test"); fail() } catch (e: SfException) { assertEquals(SfFailureKind.CLIENT, e.kind) }
    }

    @Test fun freeChaptersNeverSendAccountCookieAndPaidChaptersRequireLogin() = runBlocking {
        val api = SfApi(SfTransport { _, headers, _ ->
            assertNull(headers["Cookie"])
            ok("""{"content":"<p>第一段</p><p>第二段</p>","isBuy":false}""")
        }, device)
        assertTrue(api.chapter("123", "private-cookie", false) { it }.contains("第二段"))
        try { api.chapter("123", "", true) { it }; fail() }
        catch (e: SfException) { assertEquals(SfFailureKind.AUTH, e.kind) }
    }

    @Test fun previewAndEmptyTextAreNeverSuccessfulChapters() {
        for (data in listOf("""{"content":"预览","isPreview":true}""", """{"content":"正文","isBuy":false}""", "{}")) {
            try { SfProtocol.content(obj(data)) { it }; fail() }
            catch (e: SfException) { assertEquals(SfFailureKind.UNPURCHASED, e.kind) }
        }
    }

    @Test fun expansionContentAndSubstitutionDecoderAreAppliedOnce() {
        val text = SfProtocol.content(obj("""{"content":"烬","expand":{"content":"好"}}""")) { it.replace("烬", "爱") }
        assertEquals("爱好", text)
    }

    @Test fun shelfExpansionPaginatesBeyondThirtyWithoutDuplicates() = runBlocking {
        val novels = (1..65).joinToString(",") { """{"novelId":$it,"novelName":"书$it"}""" }
        var calls = 0
        val api = SfApi(SfTransport { path, _, _ ->
            calls++; assertEquals("user/Pockets?expand=novels", path)
            ok("""[{"pocketId":-1,"name":"默认书架","expand":{"novels":[$novels]}}]""")
        }, device)
        assertEquals("-1", api.shelves("test").single().first)
        val all = (1..3).flatMap { api.shelfPage("test", "-1", "默认书架", it) }
        assertEquals(65, all.size); assertEquals(65, all.map { it.id }.distinct().size); assertEquals(1, calls)
    }

    @Test fun directoryKeepsPaidChaptersAndDoesNotDownloadTheirBody() = runBlocking {
        val paths = mutableListOf<String>()
        val api = SfApi(SfTransport { path, _, _ ->
            paths += path
            if (path.contains("/dirs")) ok("""{"volumeList":[{"title":"卷一","chapterList":[{"chapId":1,"title":"免费"},{"chapId":2,"title":"VIP","originNeedFireMoney":20}]}]}""")
            else ok("""{"novelName":"测试书","authorName":"作者"}""")
        }, device)
        val catalog = api.catalog("123")
        assertEquals(2, catalog.chapters.size); assertTrue(catalog.chapters[1].paid)
        assertEquals(2, paths.size); assertFalse(paths.any { it.startsWith("Chaps") })
    }

    private val book = BookEntity("sf:1", "书<&名", "作者", null, "", BookSourceType.BOLUOBAO, 3)
    private fun chapter(i: Int, content: String) = ChapterEntity("sf:1:$i", book.id, i, "第$i 章<&", content, ChapterOriginType.ORIGINAL, remoteId = "$i")

    @Test fun partialExportRequiresExplicitConfirmationAndListsMissingChapters() {
        val chapters = listOf(chapter(2, "第二段"), chapter(0, "第一段"), chapter(1, ""))
        try { BookExporter.write(ByteArrayOutputStream(), book, chapters, BookExportFormat.TXT, false); fail() } catch (_: IllegalArgumentException) { }
        val out = ByteArrayOutputStream()
        BookExporter.write(out, book, chapters, BookExportFormat.MARKDOWN, true)
        val text = out.toString("UTF-8")
        assertTrue(text.contains("缺少 1 章")); assertTrue(text.contains("第1 章")); assertTrue(text.indexOf("第一段") < text.indexOf("第二段"))
    }

    @Test fun epubContainsStoredMimetypeValidXmlNavigationAndOrderedSpine() {
        val out = ByteArrayOutputStream()
        BookExporter.write(out, book, listOf(chapter(1, "段落<&>"), chapter(0, "")), BookExportFormat.EPUB, true)
        val entries = linkedMapOf<String, ByteArray>()
        ZipInputStream(ByteArrayInputStream(out.toByteArray())).use { zip ->
            var entry = zip.nextEntry
            assertEquals("mimetype", entry!!.name); assertEquals(0, entry.method)
            while (entry != null) { entries[entry.name] = zip.readBytes(); entry = zip.nextEntry }
        }
        assertEquals("application/epub+zip", entries.getValue("mimetype").toString(Charsets.UTF_8))
        val factory = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
        entries.filterKeys { it.endsWith("xml") || it.endsWith("xhtml") || it.endsWith("opf") }.forEach { (_, bytes) ->
            factory.newDocumentBuilder().parse(ByteArrayInputStream(bytes))
        }
        val opf = entries.getValue("OEBPS/content.opf").toString(Charsets.UTF_8)
        assertTrue(opf.indexOf("idref=\"notice\"") < opf.indexOf("idref=\"c0\""))
        assertTrue(entries.getValue("OEBPS/chapter0.xhtml").toString(Charsets.UTF_8).contains("&lt;&amp;&gt;"))
    }

    @Test fun sourceLinksOnlyAcceptOfficialHttpsBookUrls() {
        assertEquals("123", SfacgFetcher.extractBookId("https://book.sfacg.com/Novel/123/"))
        assertNull(SfacgFetcher.extractBookId("https://evil.example/Novel/123/"))
        assertNull(SfacgFetcher.extractBookId("http://book.sfacg.com/Novel/123/"))
    }

    @Test fun searchAndRankParsingWorkAsExpected() = runBlocking {
        val api = SfApi(SfTransport { path, _, _ ->
            if (path.startsWith("search")) {
                ok("""{"novels":[{"novelId":101,"novelName":"异界魔王","authorName":"作者A","point":8.5,"markCount":1000,"expand":{"intro":"简介","tags":[{"tagName":"奇幻"}]}}]}""")
            } else {
                ok("""{"novels":[{"novelId":202,"novelName":"畅销轻小说","authorName":"作者B","point":9.0,"markCount":5000,"expand":{"bigNovelCover":"http://cover.jpg"}}]}""")
            }
        }, device)
        val searchResults = api.search("魔王")
        assertEquals(1, searchResults.size)
        assertEquals("101", searchResults[0].id)
        assertEquals("异界魔王", searchResults[0].title)
        assertEquals(listOf("奇幻"), searchResults[0].tags)
        assertEquals(8.5f, searchResults[0].point)
        assertEquals(1000L, searchResults[0].markCount)

        val rankResults = api.browseRank("all")
        assertEquals(1, rankResults.size)
        assertEquals("202", rankResults[0].id)
        assertEquals("畅销轻小说", rankResults[0].title)
        assertEquals("http://cover.jpg", rankResults[0].cover)
        assertEquals(9.0f, rankResults[0].point)
        assertEquals(5000L, rankResults[0].markCount)
    }

    @Test fun wealthAndSignWorkAsExpected() = runBlocking {
        val api = SfApi(SfTransport { path, _, _ ->
            if (path.startsWith("user/money")) {
                ok("""{"fireMoney":500,"coupons":120,"vipLevel":2,"ticket":3}""")
            } else {
                ok("""{"msg":"签到成功，已领取代券"}""")
            }
        }, device)
        val wealth = api.wealth("test-cookie")
        assertEquals(500, wealth.fireMoney)
        assertEquals(120, wealth.coupons)
        assertEquals(2, wealth.vipLevel)
        assertEquals(3, wealth.tickets)

        val signRes = api.sign("test-cookie")
        assertTrue(signRes.success)
        assertTrue(signRes.message.contains("签到成功"))
    }

    @Test fun novelDetailAndRemainFieldsWorkAsExpected() = runBlocking {
        val api = SfApi(SfTransport { path, _, _ ->
            if (path.startsWith("novels/999")) {
                ok("""{"novelId":999,"novelName":"血姬与骑士","authorName":"蒸汽姬一只","charCount":1580000,"isFinish":false,"expand":{"bigNovelCover":"https://cover.jpg","intro":"完整精彩故事简介","sysTags":[{"tagName":"重生"},{"tagName":"变身"}],"tags":[{"tagName":"断章苟"}],"fav":138781,"ticket":10}}""")
            } else {
                ok("""{"fireMoneyRemain":880,"couponsRemain":360,"ticketRemain":5}""")
            }
        }, device)

        val detail = api.novelDetail("999")
        assertEquals("999", detail.id)
        assertEquals("血姬与骑士", detail.title)
        assertEquals("蒸汽姬一只", detail.author)
        assertEquals(1580000L, detail.charCount)
        assertFalse(detail.isFinish)
        assertEquals("完整精彩故事简介", detail.intro)
        assertEquals(listOf("重生", "变身"), detail.tags)
        assertEquals(138781L, detail.favCount)
        assertEquals(10, detail.ticketCount)

        val wealth = api.wealth("test-cookie")
        assertEquals(880, wealth.fireMoney)
        assertEquals(360, wealth.coupons)
        assertEquals(5, wealth.tickets)
    }

    @Test fun novelCommentsAndAddToPocketWorkAsExpected() = runBlocking {
        val api = SfApi(SfTransport { path, _, body ->
            if (path.contains("Cmts")) {
                ok("""{"cmts":[{"commentId":888,"displayName":"读者小明","content":"真好看！","createTime":"2026-10-16T12:00:00.000","vipLevel":1}]}""")
            } else if (path.startsWith("pockets")) {
                assertTrue(body!!.contains("155640"))
                ok("""{"msg":"已成功加入菠萝包云端书架"}""")
            } else {
                ok("""{}""")
            }
        }, device)

        val comments = api.novelComments("155640")
        assertEquals(1, comments.size)
        assertEquals("读者小明", comments[0].displayName)
        assertEquals("真好看！", comments[0].content)

        val shelfRes = api.addToCloudShelf("test-cookie", "155640")
        assertTrue(shelfRes.success)
    }

    @Test fun novelCommentPagesKeepDistinctHistoryAndUseCursor() = runBlocking {
        val paths = mutableListOf<String>()
        val api = SfApi(SfTransport { path, _, _ ->
            paths += path
            if (path.contains("page=0")) {
                ok("""{"cmts":[{"commentId":1,"displayName":"甲","content":"第一条"},{"commentId":2,"displayName":"乙","content":"第二条"}]}""")
            } else {
                ok("""{"cmts":[{"commentId":3,"displayName":"丙","content":"历史评论"}]}""")
            }
        }, device)
        val first = api.novelComments("155640", page = 1)
        val second = api.novelComments("155640", page = 2)
        assertEquals(listOf("1", "2", "3"), (first + second).map { it.id })
        assertTrue(paths[0].contains("page=0"))
        assertTrue(paths[1].contains("page=1"))
        assertTrue(paths.all { it.contains("sort=smart") && it.contains("type=clear") })
    }

    @Test fun novelCommentsAcceptKnownContainersAndRejectUnusableRows() {
        val comment = """{"commentId":7,"displayName":"读者","content":"书评"}"""
        val dummyApi = SfApi(SfTransport { _, _, _ -> error("parser-only test") }, device)
        assertEquals(1, dummyApi.parseNovelComments(Json.parseToJsonElement("""{"comments":[$comment]}""")).size)
        assertEquals(1, dummyApi.parseNovelComments(Json.parseToJsonElement("""{"items":[$comment]}""")).size)
        assertTrue(dummyApi.parseNovelComments(Json.parseToJsonElement("""{"comments":[{"commentId":8,"content":""}]}""")).isEmpty())
    }

    @Test fun chapterResponseProvidesAuthoritativeParagraphCommentCounts() {
        val summaries = SfProtocol.paragraphCommentSummaries(
            obj(
                """{"expand":{"tsukkomi":[
                    {"row":2,"count":1},
                    {"row":0,"count":4},
                    {"row":2,"count":3},
                    {"row":-1,"count":9},
                    {"row":4,"count":0},
                    {"row":"bad","count":2}
                ]}}"""
            )
        )
        assertEquals(
            listOf(
                SfParagraphCommentSummary(0, 4),
                SfParagraphCommentSummary(2, 3)
            ),
            summaries
        )
    }

    @Test fun paragraphNormalizationPreservesServerRowLineBreaks() {
        assertEquals(
            "第一段\n第三段\n第四段",
            SfProtocol.normalizeSfParagraphRows("第一段<br>第三段<br/>第四段")
        )
        assertEquals(
            "第一段\n第二段\n第三段",
            SfProtocol.normalizeSfParagraphRows("<p>第一段</p><p>第二段</p><p>第三段</p>")
        )
    }

    @Test fun pagedTsukkomiRequestTargetsChosenRow() = runBlocking {
        var path = ""
        val api = SfApi(SfTransport { actual, _, _ ->
            path = actual
            ok("[]")
        }, device)
        api.paragraphComments("6367390", row = 2, sort = "new")
        assertTrue(path.startsWith("chaps/0/6367390/tsukkomis?"))
        assertTrue(path.contains("row=2"))
        assertTrue(path.contains("sort=addtime"))
        assertTrue(path.contains("expand=vipLevel"))
        assertTrue(path.contains("replys"))
    }

    @Test fun realChapterTsukkomiFieldsAreParsedWithoutInventingVip() {
        val parsed = SfProtocol.parseParagraphComments(
            Json.parseToJsonElement(
                """[
                  {"tsukkomiId":35702792,"row":0,"favNum":27,"replyNum":10,
                   "content":"看到后面有些失望[em:sfgirl:007]",
                   "postDate":"2025-04-08T13:58:07",
                   "postUser":{"accountId":10596429,"nickName":"hyxv5","expand":{"avatar":"https://example.com/main.jpg"}},
                   "roleName":"火种"}
                ]"""
            )
        )
        assertEquals(1, parsed.size)
        val comment = parsed.single()
        assertEquals("35702792", comment.id)
        assertEquals(0, comment.row)
        assertEquals("hyxv5", comment.displayName)
        assertEquals("https://example.com/main.jpg", comment.avatar)
        assertEquals("看到后面有些失望[表情]", comment.content)
        assertEquals("2025-04-08 13:58:07", comment.createTime)
        assertEquals(27, comment.favCount)
        assertEquals(10, comment.replyNum)
        assertEquals("火种", comment.roleName)
    }

    @Test fun tsukkomiContainersAndMalformedEntriesHaveDistinctResults() {
        val item = """{"id":"1","content":"合法","postUser":{"nickName":"读者"}}"""
        assertEquals(1, SfProtocol.parseParagraphComments(
            Json.parseToJsonElement("""{"tsukkomis":[$item]}""")
        ).size)
        assertEquals(1, SfProtocol.parseParagraphComments(
            Json.parseToJsonElement("""{"items":[$item]}""")
        ).size)
        try {
            SfProtocol.parseParagraphComments(Json.parseToJsonElement("""[{"id":"bad","content":""}]"""))
            fail("Malformed comment must not appear as a valid empty state")
        } catch (e: SfException) {
            assertEquals(SfFailureKind.INVALID, e.kind)
        }
    }

    @Test fun tsukkomiReplyThreadParsesRealNestedFieldsAndDeduplicates() {
        val parsed = SfProtocol.parseParagraphComments(
            Json.parseToJsonElement(
                """[{
                  "tsukkomiId":35702792,"row":0,"replyNum":10,"content":"主吐槽",
                  "postUser":{"nickName":"楼主"},
                  "expand":{"replys":[
                    {"replyId":12993575,"content":"第一条[em:sfgirl:007]",
                     "replyDate":"2026-06-05T13:24:30",
                     "replyUser":{"nickName":"归烟35","expand":{"avatar":"https://example.com/a.jpg"}},
                     "roleName":"灰烬"},
                    {"replyId":12529185,"content":"回复@楼主 第二条",
                     "replyDate":"2025-08-24T17:40:48",
                     "replyUser":{"nickName":"咸鱼XI"},"roleName":"灰烬"},
                    {"replyId":12993575,"content":"重复条目","replyUser":{"nickName":"重复"}},
                    {"replyId":1,"content":""}
                  ]}
                }]"""
            )
        )
        val comment = parsed.single()
        assertEquals(2, comment.replies.size)
        assertEquals("12993575", comment.replies[0].id)
        assertEquals("归烟35", comment.replies[0].displayName)
        assertEquals("https://example.com/a.jpg", comment.replies[0].avatar)
        assertEquals("第一条[表情]", comment.replies[0].content)
        assertEquals("2026-06-05 13:24:30", comment.replies[0].createTime)
        assertEquals("灰烬", comment.replies[0].roleName)
    }
}
