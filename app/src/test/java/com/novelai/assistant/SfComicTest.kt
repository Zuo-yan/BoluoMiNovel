package com.novelai.assistant

import com.novelai.assistant.data.sf.SfComicLibrary
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.*
import org.junit.Test

class SfComicTest {
    @Test fun mobileDetailExtractsChronologicalChaptersAndMetadata() {
        val html = """<html><head><title>赤地万里_赤地万里漫画</title></head><body>
            <ul class="book_info"><li><span class="book_newtitle">赤地万里</span>
            <div class="book_info2"><span>悬疑</span><span>连载中</span></div></li>
            <li><img src="https://rs.sfacg.com/cover.jpg"></li></ul>
            <ul class="book_profile"><li>作品简介</li><li class="book_bk_qs1">故事简介</li></ul>
            <div class="comic_main_list">
              <a href="/b/CDWL/ZP/0037_4880/"><div>第17幕</div></a>
              <a href="/b/CDWL/ZP/0001_4504/"><div>预告</div></a>
            </div><ul class="book_Author"><li>作者甲</li></ul>
            </body></html>"""
        val info = SfComicLibrary.parseDetail("CDWL", html)
        assertEquals("赤地万里", info.comic.title)
        assertEquals("故事简介", info.comic.intro)
        assertEquals("悬疑", info.comic.category)
        assertEquals("连载中", info.comic.status)
        assertEquals(listOf("ZP/0001_4504", "ZP/0037_4880"), info.chapters.map { it.id })
    }

    @Test fun catalogExtractsOfficialComicLinksAndMetadata() {
        val html = """<ul class="Comic_Pic_List"><li class="Conjunction"><a href="http://manhua.sfacg.com/mh/CDWL/" target="_blank"><img src="http://rs.sfacg.com/web/comic/images/Logo/cover.jpg" alt="赤地万里"></a></li><li><strong><a href="http://manhua.sfacg.com/mh/CDWL/" class="orange_link2">赤地万里</a></strong><br>作者： 作者甲<br>综合信息： <a href="/catalog/?tid=10">奇幻</a></li></ul>"""
        val comics = SfComicLibrary.parseCatalog(html)
        assertEquals(1, comics.size)
        assertEquals("CDWL", comics.single().slug)
        assertEquals("赤地万里", comics.single().title)
        assertEquals("奇幻", comics.single().category)
        assertTrue(comics.single().cover.orEmpty().startsWith("http://rs.sfacg.com/"))
        val searched = SfComicLibrary.parseCatalog("""<ul style="width:100%"><li class="Conjunction"><img src="//rs.sfacg.com/web/comic/images/Logo/search.jpg" alt="赤地万里"></li><li><strong class="F14PX"><a href="http://manhua.sfacg.com/mh/CDWL" class="orange_link2">赤地万里</a></strong><br>综合信息：第17幕</li></ul>""")
        assertEquals("CDWL", searched.single().slug)
    }

    @Test fun catalogReadsRealLastPageFromPagination() {
        val html = """<a href="/catalog/default.aspx?PageIndex=10">10</a>
            <a href="https://manhua.sfacg.com/catalog/default.aspx?PageIndex=11&tid=10">11</a>
            <a href="/catalog/?PageIndex=2">下一页</a>"""
        assertEquals(11, SfComicLibrary.parseLastPage(html))
        assertNull(SfComicLibrary.parseLastPage("<html><body>无分页</body></html>"))
    }

    @Test fun detailKeepsChapterIdsAndRejectsMissingDirectory() {
        val html = """<html><head><title>赤地万里,赤地万里漫画</title></head><body>
            <a href="/mh/CDWL/115750/">第17幕</a><a href="/mh/CDWL/115669/">第17幕 上</a>
            </body></html>"""
        val info = SfComicLibrary.parseDetail("CDWL", html)
        assertEquals("赤地万里", info.comic.title)
        assertEquals(listOf("115669", "115750"), info.chapters.map { it.id })
        try { SfComicLibrary.parseDetail("CDWL", "<html></html>"); fail() } catch (_: Exception) {}
    }

    @Test fun pageBackendParsesRealResponseAndRejectsUnexpectedHosts() {
        val json = """{"status":200,"data":["https://coldpic.sfacg.com/Pic/OnlineComic5/CDWL/ZP/1.jpg","https://evil.example/image.jpg"]}"""
        assertEquals(listOf("https://coldpic.sfacg.com/Pic/OnlineComic5/CDWL/ZP/1.jpg"), SfComicLibrary.parsePicResponse(json))
        try { SfComicLibrary.parsePicResponse("""{"status":555,"msg":"need login"}"""); fail() }
        catch (_: Exception) {}
    }

    @Test fun chapterParamsReadsSiteScriptAndRequiresSafeValues() {
        val html = """<script>var c = 3127; var chapId = 115750; var fn = "ZP"; var nv = "0037_4880"; var comicFolder = "CDWL";</script>"""
        assertEquals(listOf("3127", "CDWL", "0037_4880", "ZP", "115750"), SfComicLibrary.chapterParams(html))
        val mobile = """<a href="/b/CDWL/">返回</a><script>var comicId = 3127; var chapterId=115750; var fn = "ZP"; var nv = "0037_4880";</script>"""
        assertEquals(listOf("3127", "CDWL", "0037_4880", "ZP", "115750"), SfComicLibrary.chapterParams(mobile))
        try { SfComicLibrary.chapterParams("var c = 3127;"); fail() } catch (_: Exception) {}
    }

    @Test fun officialUrlGuardOnlyAllowsHttpsComicHosts() {
        assertTrue(SfComicLibrary.isAllowedUrl("https://mm.sfacg.com/b/CDWL/"))
        assertTrue(SfComicLibrary.isAllowedUrl("https://coldpic.sfacg.com/Pic/a.jpg"))
        assertFalse(SfComicLibrary.isAllowedUrl("http://mm.sfacg.com/b/CDWL/"))
        assertFalse(SfComicLibrary.isAllowedUrl("https://evil.example/b/CDWL/"))
        assertFalse(SfComicLibrary.isAllowedUrl("https://mm.sfacg.com.evil.example/b/CDWL/"))
        assertFalse(SfComicLibrary.isAllowedUrl("https://user:pass@mm.sfacg.com/b/CDWL/"))
    }

    @Test fun apiComicListAndDetailParseChronologicalChaptersAndTypes() {
        val typesJson = Json.parseToJsonElement("""[{"typeId":1,"typeName":"热血"},{"typeId":7,"typeName":"恋爱"}]""")
        val types = SfComicLibrary.parseApiComicTypes(typesJson)
        assertEquals(2, types.size)
        assertEquals("热血", types[0].name)

        val listJson = Json.parseToJsonElement(
            """[{"comicId":1887,"comicName":"妖神记","folderName":"YSJ","typeId":1,"isFinished":false,"comicCover":"https://rss.sfacg.com/web/comic/images/Logo/1.jpg","latestChapterTitle":"第538话","expand":{"typeName":"热血","authorName":"踏雪动漫","intro":"简介内容"}}]"""
        )
        val comics = SfComicLibrary.parseApiComicList(listJson)
        assertEquals(1, comics.size)
        assertEquals("1887", comics.single().slug)
        assertEquals("YSJ", comics.single().folderName)
        assertEquals("热血", comics.single().category)
        assertEquals("连载中", comics.single().status)

        val meta = listJson.let { Json.parseToJsonElement(it.toString()) }
        val metaObj = Json.parseToJsonElement(
            """{"comicId":1887,"comicName":"妖神记","folderName":"YSJ","isFinished":false,"expand":{"typeName":"热血","authorName":"踏雪动漫","intro":"简介内容"}}"""
        ).jsonObject
        val chaps = Json.parseToJsonElement(
            """[
                {"chapterId":115922,"chapterTitle":"第538话（下）","pathName":"1011_8219","isVip":true,"expand":{"needFireMoney":0}},
                {"chapterId":4519,"chapterTitle":"第1话 重生","pathName":"001","isVip":false,"expand":{"needFireMoney":0}}
            ]"""
        )
        val info = SfComicLibrary.parseApiComicDetail(metaObj, chaps)
        assertEquals(listOf("4519", "115922"), info.chapters.map { it.id })
        assertFalse(info.chapters[0].isVip)
        assertTrue(info.chapters[1].isVip)
        assertEquals("第538话（下）", info.comic.latestChapter)
    }

    @Test fun parseDetailDetectsOfficialWebErrorPages() {
        try {
            SfComicLibrary.parseDetail("YSJ", "<html><head><title>出错啦</title></head><body>很抱歉</body></html>")
            fail()
        } catch (e: Exception) {
            assertTrue(e.message.orEmpty().contains("网页版"))
        }
    }
}
