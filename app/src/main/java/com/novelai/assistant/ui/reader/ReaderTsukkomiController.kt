package com.novelai.assistant.ui.reader

import com.novelai.assistant.data.db.ChapterEntity
import com.novelai.assistant.data.sf.SfParagraphComment
import com.novelai.assistant.data.sf.SfParagraphReply
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Remote chapter IDs are separate from the local `sf:<book>:<chapter>` primary key. */
internal fun sfTsukkomiChapterId(localId: String, remoteId: String?): String? =
    remoteId?.takeIf { it.matches(Regex("\\d+")) }
        ?: localId.takeIf { it.matches(Regex("\\d+")) }

data class ReplyThread(
    val replies: List<SfParagraphReply> = emptyList(),
    val loading: Boolean = false,
    val loaded: Boolean = false,
    val error: String? = null
)

class ReaderTsukkomiController(
    private val scope: CoroutineScope,
    private val fetchComments: suspend (chapterId: String, sort: String, row: Int?, page: Int) -> List<SfParagraphComment>,
    private val fetchReplies: suspend (commentId: String) -> List<SfParagraphReply>
) {
    private val _comments = MutableStateFlow<List<SfParagraphComment>>(emptyList())
    val comments: StateFlow<List<SfParagraphComment>> = _comments.asStateFlow()

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val _loadingMore = MutableStateFlow(false)
    val loadingMore: StateFlow<Boolean> = _loadingMore.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _sort = MutableStateFlow("hot")
    val sort: StateFlow<String> = _sort.asStateFlow()

    private val _open = MutableStateFlow(false)
    val open: StateFlow<Boolean> = _open.asStateFlow()

    private val _quote = MutableStateFlow("")
    val quote: StateFlow<String> = _quote.asStateFlow()

    private val _row = MutableStateFlow<Int?>(null)
    val row: StateFlow<Int?> = _row.asStateFlow()

    private val _commentCounts = MutableStateFlow<Map<Int, Int>>(emptyMap())
    val commentCounts: StateFlow<Map<Int, Int>> = _commentCounts.asStateFlow()

    private val _expandedReplies = MutableStateFlow<Set<String>>(emptySet())
    val expandedReplies: StateFlow<Set<String>> = _expandedReplies.asStateFlow()

    private val _replyThreads = MutableStateFlow<Map<String, ReplyThread>>(emptyMap())
    val replyThreads: StateFlow<Map<String, ReplyThread>> = _replyThreads.asStateFlow()

    private var commentsJob: Job? = null
    private var commentsPage = 0
    private val replyJobs = mutableMapOf<String, Job>()

    fun setCommentCounts(counts: Map<Int, Int>) {
        _commentCounts.value = counts
    }

    fun clearCommentCounts() {
        _commentCounts.value = emptyMap()
    }

    private fun clearReplyThreads() {
        replyJobs.values.forEach { it.cancel() }
        replyJobs.clear()
        _replyThreads.value = emptyMap()
    }

    fun open(
        chapter: ChapterEntity?,
        sort: String = _sort.value,
        quote: String = _quote.value,
        row: Int? = _row.value
    ) {
        if (chapter == null) return
        _quote.value = quote
        _row.value = row
        _sort.value = sort
        _open.value = true
        _loading.value = true
        _loadingMore.value = false
        _error.value = null
        commentsPage = 0
        _expandedReplies.value = emptySet()
        clearReplyThreads()
        commentsJob?.cancel()

        val remoteId = sfTsukkomiChapterId(chapter.id, chapter.remoteId)
        if (remoteId == null) {
            _loading.value = false
            _error.value = "本地章节没有可读取的菠萝包间贴"
            return
        }
        commentsJob = scope.launch {
            try {
                val remote = fetchComments(remoteId, sort, row, 1)
                _comments.value = remote
                commentsPage = 1
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _comments.value = emptyList()
                _error.value = e.message ?: "章节间贴加载失败"
            } finally {
                _loading.value = false
            }
        }
    }

    fun loadMore(chapter: ChapterEntity?) {
        if (_loading.value || _loadingMore.value) return
        if (_comments.value.size < 20) return
        if (chapter == null) return
        val remoteId = sfTsukkomiChapterId(chapter.id, chapter.remoteId) ?: return
        val before = _comments.value
        val nextPage = commentsPage + 1
        commentsJob = scope.launch {
            _loadingMore.value = true
            try {
                val page = fetchComments(remoteId, _sort.value, _row.value, nextPage)
                _comments.value = (before + page).distinctBy { it.id }
                commentsPage = nextPage
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _error.value = e.message ?: "更多间贴加载失败"
            } finally {
                _loadingMore.value = false
            }
        }
    }

    fun toggleReplies(commentId: String) {
        if (_comments.value.none { it.id == commentId }) return
        _expandedReplies.update { current ->
            if (commentId in current) current - commentId else current + commentId
        }
        if (commentId in _expandedReplies.value && _replyThreads.value[commentId]?.loaded != true) {
            loadReplies(commentId)
        }
    }

    fun loadReplies(commentId: String) {
        if (_replyThreads.value[commentId]?.loading == true) return
        _replyThreads.update {
            it + (commentId to (it[commentId] ?: ReplyThread()).copy(loading = true, error = null))
        }
        replyJobs[commentId] = scope.launch {
            try {
                val replies = fetchReplies(commentId)
                _replyThreads.update {
                    it + (commentId to ReplyThread(replies = replies, loaded = true))
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _replyThreads.update {
                    it + (commentId to (it[commentId] ?: ReplyThread()).copy(
                        loading = false,
                        error = e.message ?: "回复加载失败"
                    ))
                }
            }
        }
    }

    fun close() {
        commentsJob?.cancel()
        _open.value = false
        _loading.value = false
        _loadingMore.value = false
        commentsPage = 0
        _row.value = null
        _expandedReplies.value = emptySet()
        clearReplyThreads()
    }
}
