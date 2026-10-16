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
    private val storage: SecureStorage,
    @ApplicationContext private val context: Context
) {
    private val lock = Mutex()
    private var cookie = storage.getDecrypted("sf_app_session").orEmpty()
    private val _account = MutableStateFlow<SfAccount?>(null)
    val account = _account.asStateFlow()
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
    }

    suspend fun validate() = lock.withLock {
        if (cookie.isBlank()) return@withLock
        try { _account.value = api.inspect(cookie) }
        catch (e: SfException) {
            if (e.kind == SfFailureKind.AUTH) clear()
            throw e
        }
    }

    suspend fun logout() = lock.withLock { clear() }
    private fun clear() {
        cookie = ""
        api.clearShelfCache()
        _account.value = null
        storage.putEncrypted("sf_app_session", "")
        storage.putEncrypted("sfacg_cookie", "")
    }

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
}
