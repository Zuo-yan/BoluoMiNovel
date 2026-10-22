package com.novelai.assistant

import com.novelai.assistant.ui.reader.*
import com.novelai.assistant.data.sf.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class ReaderEnhancementsTest {
    @Test fun illustrationRecognizesSfMarkupWithoutAcceptingUnsafeSchemes() {
        assertEquals("https://rss.sfacg.com/a.jpg", ReaderIllustration.parse("[img=700,875]http://rss.sfacg.com/a.jpg[/img]")?.url)
        assertNull(ReaderIllustration.parse("[img]file:///secret[/img]"))
        assertNull(ReaderIllustration.parse("[img]https://user:pass@example.com/a[/img]"))
        assertNull(ReaderIllustration.parse("正文"))
    }
    @Test fun anchorRestoresSplitParagraphAndClampsAfterEdits() {
        val pages = listOf(ReaderPage(listOf(PageFragment(0,"a",0))),
            ReaderPage(listOf(PageFragment(0,"b",100))), ReaderPage(listOf(PageFragment(3,"c",0))))
        assertEquals(1, Paginator.pageForAnchor(pages, ReadingAnchor(0,150)))
        assertEquals(0, Paginator.pageForAnchor(pages, ReadingAnchor()))
        assertEquals(2, Paginator.pageForAnchor(pages, ReadingAnchor(99)))
    }
    @Test fun htmlImagesSurviveTextNormalization() {
        assertEquals("[img]https://rss.sfacg.com/a.jpg[/img]", SfProtocol.normalizeSfParagraphRows("<img src='https://rss.sfacg.com/a.jpg'>"))
    }
    @Test fun anchorOnBlankOrMissingParagraphFallsBackToReadablePage() {
        // 空白段落不产生分页片段，锚点落在空白段时应停在它之前最近的可见页，而不是越界。
        val pages = listOf(ReaderPage(listOf(PageFragment(0, "a", 0))),
            ReaderPage(listOf(PageFragment(2, "b", 0))))
        assertEquals(0, Paginator.pageForAnchor(pages, ReadingAnchor(1)))
        assertEquals(1, Paginator.pageForAnchor(pages, ReadingAnchor(2, 0)))
        assertEquals(1, Paginator.pageForAnchor(pages, ReadingAnchor(500)))
    }

    @Test fun illustrationRowIsKeptAsOwnPageInsteadOfBeingMeasuredAsText() {
        // 插图行应独占一页，并且不被当正文测量吞掉。
        val illus = "[img=700,875]https://rss.sfacg.com/a.jpg[/img]"
        assertEquals("https://rss.sfacg.com/a.jpg", ReaderIllustration.parse(illus)?.url)
        assertNull(ReaderIllustration.parse("正文 [img]https://rss.sfacg.com/a.jpg[/img]"))
    }

    @Test fun repliesUseIndependentAnonymousReadEndpoint() = runBlocking {
        val api = SfApi(SfTransport { path, headers, body ->
            assertEquals("tsukkomis/35702792/replys?expand=avatar%2CroleName", path)
            assertNull(headers["Cookie"]); assertNull(body)
            SfResponse(200, """{"status":{"httpCode":200},"data":[{"replyId":5,"content":"reply","replyUser":{"nickName":"reader"}}]}""")
        }, SfProtocol.COMPATIBLE_DEVICE)
        assertEquals("5", api.paragraphReplies("35702792").single().id)
    }
}
