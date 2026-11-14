package com.novelai.assistant.data.sf

import com.novelai.assistant.data.security.SecureStorage
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 菠萝包流控与风控状态的本地加密持久化存储。
 */
@Singleton
class SfSafetyStore @Inject constructor(
    private val storage: SecureStorage,
    private val json: Json
) {
    fun load(): SfSafetyState {
        if (!storage.containsEncrypted(KEY_SAFETY_STATE)) return SfSafetyState()
        return runCatching {
            val raw = storage.getDecrypted(KEY_SAFETY_STATE) ?: error("unreadable")
            json.decodeFromString(SfSafetyState.serializer(), raw)
        }.getOrDefault(SfSafetyState())
    }

    fun save(state: SfSafetyState) {
        storage.putEncrypted(KEY_SAFETY_STATE, json.encodeToString(SfSafetyState.serializer(), state))
    }

    private companion object {
        const val KEY_SAFETY_STATE = "sf_request_safety_v1"
    }
}
