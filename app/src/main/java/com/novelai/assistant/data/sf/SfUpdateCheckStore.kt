package com.novelai.assistant.data.sf

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
data class ShelfUpdate(
    val latest: String = "",
    val time: String = "",
    val unread: Int = 0,
    val added: List<String> = emptyList(),
    val checkedAt: Long = 0,
    val error: String? = null
) {
    fun label(): String = error ?: "更新：$time · 未读 $unread 章\n$latest" +
        if (added.isNotEmpty()) " · 新增 ${added.size} 章" else ""
}

@Singleton
class SfUpdateCheckStore @Inject constructor(
    @ApplicationContext context: Context,
    private val json: Json
) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val _updates = MutableStateFlow(load())
    val updates: StateFlow<Map<String, ShelfUpdate>> = _updates.asStateFlow()

    fun load(): Map<String, ShelfUpdate> = runCatching {
        val raw = prefs.getString(KEY_RESULTS, null) ?: return@runCatching emptyMap()
        json.decodeFromString<Map<String, ShelfUpdate>>(raw)
    }.getOrDefault(emptyMap())

    fun save(map: Map<String, ShelfUpdate>) {
        _updates.value = map
        prefs.edit().putString(KEY_RESULTS, json.encodeToString(map)).apply()
    }

    fun put(bookId: String, entry: ShelfUpdate) {
        var snapshot: Map<String, ShelfUpdate> = emptyMap()
        _updates.update { current ->
            (current + (bookId to entry)).also { snapshot = it }
        }
        prefs.edit().putString(KEY_RESULTS, json.encodeToString(snapshot)).apply()
    }

    private companion object {
        const val PREFS_NAME = "sf_update_checks"
        const val KEY_RESULTS = "results"
    }
}
