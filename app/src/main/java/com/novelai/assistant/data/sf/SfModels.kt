package com.novelai.assistant.data.sf

enum class SfFailureKind { AUTH, RISK, UNPURCHASED, NETWORK, SIGNATURE, INVALID, RATE_LIMIT, CLIENT, COOLDOWN }

class SfException(val kind: SfFailureKind, message: String, val retryAfterMillis: Long? = null) : Exception(message)

data class SfAccount(val id: String, val name: String, val vipLevel: Int = 0, val avatar: String? = null)

data class SfWealth(val fireMoney: Int = 0, val coupons: Int = 0, val vipLevel: Int = 0, val tickets: Int = 0)

data class SfSearchResult(
    val id: String,
    val title: String,
    val author: String,
    val cover: String?,
    val intro: String = "",
    val tags: List<String> = emptyList(),
    val point: Float = 0f,
    val markCount: Long = 0L,
    val viewTimes: Long = 0L
)

data class SfSysTag(val id: Int, val name: String)

data class SfComment(
    val id: String,
    val displayName: String,
    val avatar: String?,
    val content: String,
    val createTime: String,
    val favCount: Int = 0,
    val replyNum: Int = 0,
    val vipLevel: Int = 0,
    val isStick: Boolean = false
)

data class SfBookDetail(
    val id: String,
    val title: String,
    val author: String,
    val cover: String?,
    val intro: String = "",
    val tags: List<String> = emptyList(),
    val typeName: String = "",
    val isFinish: Boolean = false,
    val charCount: Long = 0L,
    val markCount: Long = 0L,
    val viewTimes: Long = 0L,
    val favCount: Long = 0L,
    val ticketCount: Int = 0,
    val point: Float = 0f,
    val lastUpdateTime: String = "",
    val latestChapterTitle: String = "",
    val totalChapters: Int = 0
)

data class SfParagraphComment(
    val id: String,
    val row: Int,
    val displayName: String,
    val avatar: String?,
    val content: String,
    val createTime: String,
    val favCount: Int = 0,
    val replyNum: Int = 0,
    val roleName: String = "",
    val replies: List<SfParagraphReply> = emptyList()
)

data class SfParagraphReply(
    val id: String,
    val displayName: String,
    val avatar: String?,
    val content: String,
    val createTime: String,
    val roleName: String = ""
)

data class SfParagraphCommentSummary(
    val row: Int,
    val count: Int
)

data class SfSignResult(val success: Boolean, val message: String)

data class SfShelfBook(val id: String, val title: String, val author: String, val cover: String?, val shelf: String)

data class SfRemoteChapter(val id: String, val title: String, val paid: Boolean, val volume: String)

data class SfCatalog(val id: String, val title: String, val author: String, val cover: String?, val chapters: List<SfRemoteChapter>)

data class SfResponse(
    val status: Int,
    val body: String,
    val cookies: List<String> = emptyList(),
    val retryAfterMillis: Long? = null
)
