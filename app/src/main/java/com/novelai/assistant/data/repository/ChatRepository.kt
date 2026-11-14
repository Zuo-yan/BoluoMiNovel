package com.novelai.assistant.data.repository

import com.novelai.assistant.data.db.AiChatRecordEntity
import com.novelai.assistant.data.db.ChatDao
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ChatRepository @Inject constructor(
    private val chatDao: ChatDao
) {
    fun observeChat(bookId: String): Flow<List<AiChatRecordEntity>> = chatDao.observeByBook(bookId)

    suspend fun recentMessages(bookId: String, limit: Int = 12): List<AiChatRecordEntity> =
        chatDao.getRecent(bookId, limit)

    suspend fun save(record: AiChatRecordEntity) = chatDao.insert(record)

    suspend fun clear(bookId: String) = chatDao.clearByBook(bookId)
}
