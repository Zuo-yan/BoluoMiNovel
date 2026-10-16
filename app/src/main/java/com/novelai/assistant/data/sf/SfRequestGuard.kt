package com.novelai.assistant.data.sf

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable

@Serializable
data class SfSafetyState(
    val kind: SfFailureKind? = null,
    val message: String = "",
    val until: Long = 0,
    val nextRequestAt: Long = 0
) {
    val blocked get() = kind == SfFailureKind.RISK && until > System.currentTimeMillis()
    val requiresLogin get() = kind == SfFailureKind.AUTH || kind == SfFailureKind.RISK
}

/** 温和的请求平滑队列流控，不盲目自锁，仅在官方明确返回 1148 验证码时温和提示 */
class SfRequestGuard(
    initial: SfSafetyState = SfSafetyState(),
    private val persist: (SfSafetyState) -> Unit = {},
    private val now: () -> Long = System::currentTimeMillis,
    private val wait: suspend (Long) -> Unit = { delay(it) }
) {
    private val lock = Mutex()
    private val epoch = java.util.concurrent.atomic.AtomicLong()
    private val mutable = MutableStateFlow(initial)
    val state = mutable.asStateFlow()
    fun invalidatePending() { epoch.incrementAndGet() }
    private fun save(value: SfSafetyState) { persist(value); mutable.value = value }

    suspend fun acknowledgeCooldown() = lock.withLock {
        save(SfSafetyState())
    }

    suspend fun forceBypass() = lock.withLock {
        save(SfSafetyState())
    }

    suspend fun loginSucceeded() = lock.withLock {
        save(SfSafetyState())
    }

    suspend fun <T> request(path: String, recovery: Boolean = false, block: suspend () -> T): T {
        val requestedEpoch = epoch.get()
        return lock.withLock {
            if (requestedEpoch != epoch.get()) throw CancellationException("Session changed")
            var s = mutable.value
            // 仅在官方明确风控中且未被强行解除时拦截
            if (s.blocked && !recovery) {
                if (now() < s.until) {
                    throw SfException(s.kind ?: SfFailureKind.RISK, s.message.ifBlank { "官方平台要求安全验证，请在官方 App 完成后继续" })
                } else {
                    save(SfSafetyState())
                }
            }

            // 平滑温和队列排队，避免高频并发，不抛异常阻断用户
            val time = now()
            val chapter = path.startsWith("Chaps/")
            val due = s.nextRequestAt
            if (due > time) {
                wait((due - time).coerceAtMost(3_000L))
            }
            if (requestedEpoch != epoch.get()) throw CancellationException("Session changed")

            val gap = if (chapter) CHAPTER_GAP else REQUEST_GAP
            save(s.copy(nextRequestAt = now() + gap))

            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: SfException) {
                if (e.kind == SfFailureKind.RISK) {
                    // 仅官方明确返回 1148 安全验证时记录提示
                    save(SfSafetyState(kind = SfFailureKind.RISK, message = e.message.orEmpty(), until = now() + RISK_COOLING))
                }
                throw e
            }
        }
    }

    companion object {
        const val REQUEST_GAP = 300L
        const val CHAPTER_GAP = 600L
        const val RISK_COOLING = 15 * 60_000L
    }
}
