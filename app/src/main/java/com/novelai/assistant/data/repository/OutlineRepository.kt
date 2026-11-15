package com.novelai.assistant.data.repository

import com.novelai.assistant.data.db.BookOutlineEntity
import com.novelai.assistant.data.db.OutlineDao
import com.novelai.assistant.data.db.OutlineDiscussionDao
import com.novelai.assistant.data.db.OutlineDiscussionEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.jsonPrimitive
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OutlineRepository @Inject constructor(
    private val outlineDao: OutlineDao,
    private val discussionDao: OutlineDiscussionDao,
    private val json: Json
) {
    fun observeOutline(bookId: String): Flow<BookOutlineEntity?> = outlineDao.observeByBook(bookId)

    suspend fun getOutline(bookId: String): BookOutlineEntity? = outlineDao.getByBook(bookId)

    suspend fun saveOutline(
        bookId: String,
        masterOutline: String,
        upcomingPlan: String,
        useInContinuation: Boolean? = null
    ) {
        val existing = outlineDao.getByBook(bookId)
        val resolvedUse = useInContinuation ?: existing?.useInContinuation ?: true
        outlineDao.upsert(
            BookOutlineEntity(
                bookId = bookId,
                masterOutline = masterOutline.trim(),
                upcomingPlan = upcomingPlan.trim(),
                useInContinuation = resolvedUse,
                updatedAt = System.currentTimeMillis()
            )
        )
    }

    suspend fun setUseInContinuation(bookId: String, enabled: Boolean) {
        val existing = outlineDao.getByBook(bookId)
        if (existing == null) {
            outlineDao.upsert(
                BookOutlineEntity(
                    bookId = bookId,
                    masterOutline = "",
                    upcomingPlan = "",
                    useInContinuation = enabled,
                    updatedAt = System.currentTimeMillis()
                )
            )
        } else {
            outlineDao.updateUseInContinuation(bookId, enabled, System.currentTimeMillis())
        }
    }

    suspend fun clearOutline(bookId: String) = outlineDao.deleteByBook(bookId)

    fun observeDiscussion(bookId: String): Flow<List<OutlineDiscussionEntity>> =
        discussionDao.observeByBook(bookId)

    suspend fun recentDiscussion(bookId: String, limit: Int = 14): List<OutlineDiscussionEntity> =
        discussionDao.getRecent(bookId, limit).reversed()

    suspend fun saveDiscussionMessage(message: OutlineDiscussionEntity) =
        discussionDao.insert(message)

    suspend fun clearDiscussion(bookId: String) = discussionDao.deleteByBook(bookId)

    fun encodeQuickOptions(options: List<String>): String =
        runCatching { json.encodeToString(options.filter { it.isNotBlank() }.take(4)) }
            .getOrDefault("[]")

    fun decodeQuickOptions(rawJson: String): List<String> {
        if (rawJson.isBlank() || rawJson == "[]") return emptyList()
        return runCatching {
            val arr = json.parseToJsonElement(rawJson) as? JsonArray ?: return emptyList()
            arr.mapNotNull { runCatching { it.jsonPrimitive.content.trim() }.getOrNull() }
                .filter { it.isNotBlank() }
                .take(4)
        }.getOrDefault(emptyList())
    }
}
