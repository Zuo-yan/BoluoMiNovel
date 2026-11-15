package com.novelai.assistant

import com.novelai.assistant.data.sf.KtorSfComicClient
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test

/** Opt-in live public Web API smoke test. Does not read account cookies. */
class SfComicLiveTest {
    @Test fun publicCatalogSearchDetailAndPageApiWork() = runBlocking {
        assumeTrue(System.getenv("NOVELAI_SF_PUBLIC_VERIFY") == "1")
        val client = KtorSfComicClient()
        val detail = client.detail("1887")
        val chapter = detail.chapters.first()
        val pages = client.pages("1887", chapter.id)
        val catalogPage = client.catalog(1, 1)
        val catalog = catalogPage.comics
        val search = client.search("妖神记")
        assertTrue(catalog.isNotEmpty())
        assertTrue(catalog.any { it.category.isNotBlank() })
        assertTrue(search.any { it.slug == "1887" })
        assertTrue(detail.chapters.isNotEmpty())
        assertTrue(pages.isNotEmpty())
        assertTrue(pages.all { it.startsWith("https://coldpic.sfacg.com/") })
        println("PASS comic catalog=${catalog.size}, lastPage=${catalogPage.lastPage}, search=${search.size}, chapters=${detail.chapters.size}, " +
            "firstChapter=${chapter.id}, pages=${pages.size}")
    }
}
