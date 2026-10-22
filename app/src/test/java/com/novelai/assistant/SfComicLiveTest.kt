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
        val detail = client.detail("CDWL")
        val chapter = detail.chapters.first()
        val pages = client.pages("CDWL", chapter.id)
        val catalogPage = client.catalog(10, 1)
        val catalog = catalogPage.comics
        val search = client.search("赤地万里")
        assertTrue(catalog.isNotEmpty())
        assertTrue(catalog.any { it.category.isNotBlank() })
        assertTrue(catalogPage.lastPage != null && catalogPage.lastPage!! >= 1)
        assertTrue(search.any { it.slug == "CDWL" })
        assertTrue(detail.chapters.isNotEmpty())
        assertTrue(pages.isNotEmpty())
        assertTrue(pages.all { it.startsWith("https://coldpic.sfacg.com/") })
        println("PASS comic catalog=${catalog.size}, lastPage=${catalogPage.lastPage}, search=${search.size}, chapters=${detail.chapters.size}, " +
            "firstChapter=${chapter.id}, pages=${pages.size}")
    }
}
