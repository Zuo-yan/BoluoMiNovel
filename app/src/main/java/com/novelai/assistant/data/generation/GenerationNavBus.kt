package com.novelai.assistant.data.generation

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import javax.inject.Inject
import javax.inject.Singleton

data class GenerationNavTarget(
    val bookId: String,
    val chapterId: String,
    val mode: String
)

/** 通知栏进度/完成卡片点击 → 回到对应 AI 生成工作台的单次导航事件总线 */
@Singleton
class GenerationNavBus @Inject constructor() {

    private val _openGeneration = MutableSharedFlow<GenerationNavTarget>(replay = 1, extraBufferCapacity = 8)
    val openGeneration: SharedFlow<GenerationNavTarget> = _openGeneration

    fun requestOpenGeneration(bookId: String, chapterId: String, mode: String) {
        _openGeneration.tryEmit(GenerationNavTarget(bookId, chapterId, mode))
    }
}
