package com.novelai.assistant.data.sf

import android.content.Context
import com.novelai.assistant.data.security.SecureStorage
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SfSessionRepository @Inject constructor(
    private val api: SfApi,
    private val authorClient: SfAuthorClient,
    private val storage: SecureStorage,
    @ApplicationContext private val context: Context
) {
    private val lock = Mutex()
    private var cookie = storage.getDecrypted("sf_app_session").orEmpty()
    private val _account = MutableStateFlow<SfAccount?>(null)
    val account = _account.asStateFlow()
    private val _wealth = MutableStateFlow<SfWealth?>(null)
    val wealth = _wealth.asStateFlow()
    val safety = api.guard.state
    suspend fun acknowledgeCooldown() = api.guard.acknowledgeCooldown()
    suspend fun forceBypassSafety() = api.guard.forceBypass()
    val hasSession get() = cookie.isNotBlank()
    private val decoder by lazy {
        context.assets.open("sf-map.json").bufferedReader().use { reader ->
            Json.parseToJsonElement(reader.readText()).jsonObject.mapValues { it.value.jsonPrimitive.content }
        }
    }

    suspend fun login(username: String, password: String) = lock.withLock {
        val (account, newCookie) = api.login(username, password)
        withContext(Dispatchers.IO) {
            storage.putEncrypted("sf_app_session", newCookie)
            storage.putEncrypted("sfacg_cookie", "") // obsolete web-cookie setting
        }
        cookie = newCookie
        _account.value = account
        loadWealth()
    }

    suspend fun validate() = lock.withLock {
        if (cookie.isBlank()) return@withLock
        try {
            _account.value = api.inspect(cookie)
            loadWealth()
        } catch (e: SfException) {
            if (e.kind == SfFailureKind.AUTH) clear()
            throw e
        }
    }

    suspend fun logout() = lock.withLock { clear() }
    private fun clear() {
        api.guard.invalidatePending()
        cookie = ""
        api.clearShelfCache()
        _account.value = null
        _wealth.value = null
        storage.putEncrypted("sf_app_session", "")
        storage.putEncrypted("sfacg_cookie", "")
    }

    suspend fun refreshWealth() = lock.withLock {
        if (cookie.isBlank()) return@withLock
        loadWealth()
    }

    private suspend fun loadWealth() {
        try { _wealth.value = api.wealth(cookie) }
        catch (e: kotlinx.coroutines.CancellationException) { throw e }
        catch (e: SfException) {
            if (e.kind == SfFailureKind.AUTH) clear()
            if (e.kind != SfFailureKind.INVALID) throw e
        }
    }

    suspend fun sign(): SfSignResult {
        val result = api.sign(cookie)
        if (result.success) {
            refreshWealth()
        }
        return result
    }

    suspend fun search(keyword: String, page: Int = 1) = api.search(keyword, page)
    suspend fun browseRank(sort: String = "hot", page: Int = 1) = api.browseRank(sort, page)
    suspend fun novelDetail(id: String) = api.novelDetail(id)
    suspend fun authorPortfolio(bookId: String) = authorClient.portfolio(bookId)
    suspend fun paragraphComments(chapterId: String, page: Int = 1, sort: String = "hot", row: Int? = null) =
        api.paragraphComments(chapterId, page, sort = sort, row = row)
    suspend fun paragraphReplies(id: String) = api.paragraphReplies(id)
    suspend fun fetchOfficialTags() = api.fetchOfficialTags()
    suspend fun fetchCategories() = api.fetchCategories()
    suspend fun browseFiltered(filter: SfBrowseFilter, page: Int = 1, sort: String = "viewtimes") = api.browseFiltered(filter, page, sort = sort)
    suspend fun browseByTag(sysTagId: Int, page: Int = 1) = api.browseByTag(sysTagId, page)
    suspend fun browseByMultiTags(sysTagIds: List<Int>, page: Int = 1) = api.browseByMultiTags(sysTagIds, page)
    suspend fun addToCloudShelf(novelId: String, pocketId: String = "-1") = api.addToCloudShelf(cookie, novelId, pocketId)
    suspend fun novelComments(novelId: String, page: Int = 1) = api.novelComments(novelId, page)
    suspend fun newRecommendations(page: Int = 1) = api.newRecommendations(page)
    suspend fun relatedBookLists(id: String, page: Int = 1) = api.relatedBookLists(id, page)

    suspend fun shelves() = api.shelves(cookie)
    suspend fun shelfPage(id: String, name: String, page: Int) = api.shelfPage(cookie, id, name, page)
    suspend fun catalog(id: String) = api.catalog(id)
    suspend fun chapter(id: String, paid: Boolean): String {
        try {
            return withContext(Dispatchers.IO) {
                api.chapter(id, cookie, paid) { raw -> buildString { raw.forEach { append(decoder[it.toString()] ?: it) } } }
            }
        } catch (e: SfException) {
            if (e.kind == SfFailureKind.AUTH) { _account.value = null }
            throw e
        }
    }

    suspend fun chapterWithComments(
        id: String,
        paid: Boolean
    ): Pair<String, List<SfParagraphCommentSummary>> {
        try {
            return withContext(Dispatchers.IO) {
                api.chapterWithComments(id, cookie, paid) { raw ->
                    buildString { raw.forEach { append(decoder[it.toString()] ?: it) } }
                }
            }
        } catch (e: SfException) {
            if (e.kind == SfFailureKind.AUTH) _account.value = null
            throw e
        }
    }
}
