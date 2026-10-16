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

    @Test fun signatureRetriesAreBoundedAndRiskStopsImmediately() = runBlocking {
        var calls = 0
        val api = SfApi(SfTransport { _, _, _ ->
            calls++
            if (calls == 1) SfResponse(417, """{"status":{"httpCode":417}}""")
            else SfResponse(422, """{"status":{"httpCode":422,"errorCode":1148}}""")
        }, device)
        try { api.inspect("test"); fail() } catch (e: SfException) { assertEquals(SfFailureKind.RISK, e.kind) }
        assertEquals(2, calls)
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

    @Test fun repeatedSignatureRejectionEventuallyStops() = runBlocking {
        var calls = 0
        val api = SfApi(SfTransport { _, _, _ -> calls++; SfResponse(417, """{"status":{"httpCode":417}}""") }, device)
        try { api.inspect("test"); fail() } catch (e: SfException) { assertEquals(SfFailureKind.SIGNATURE, e.kind) }
        assertEquals(8, calls)
    }

    @Test fun deviceRejectionIsNotMisreportedAsExpiredLogin() = runBlocking {
        val api = SfApi(SfTransport { _, _, _ -> SfResponse(401, """{"status":{"httpCode":401,"errorCode":507}}""") }, device)
        try { api.inspect("test"); fail() } catch (e: SfException) { assertEquals(SfFailureKind.INVALID, e.kind) }
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
}
