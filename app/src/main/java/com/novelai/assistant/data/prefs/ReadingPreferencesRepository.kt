package com.novelai.assistant.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStoreFile
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

enum class PageMode { SCROLL, PAGED }

enum class ReaderBgTheme(val label: String) {
    WHITE("净白"), SEPIA("羊皮纸"), GREEN("护眼"), DARK("暗夜"), BLACK("纯黑")
}

enum class AppThemeMode { FOLLOW_SYSTEM, LIGHT, DARK }

data class ReadingAnchor(val paragraph: Int = 0, val character: Int = 0, val scroll: Int = 0)

data class ReadingSettings(
    val fontSizeSp: Int = 18,
    val lineSpacingMultiplier: Float = 1.65f,
    val horizontalPaddingDp: Int = 22,
    val paragraphSpacingDp: Int = 10,
    val pageMode: PageMode = PageMode.PAGED,
    val bgTheme: ReaderBgTheme = ReaderBgTheme.WHITE,
    val serifFont: Boolean = false,
    val appThemeMode: AppThemeMode = AppThemeMode.FOLLOW_SYSTEM
)

@Singleton
class ReadingPreferencesRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>,
    @ApplicationContext private val context: Context
) {
    private val positions by lazy {
        context.getSharedPreferences("reader_positions", Context.MODE_PRIVATE)
    }

    fun getLastChapterIndex(bookId: String): Int =
        positions.getInt("$bookId:lastChapter", -1)

    fun getChapterAnchor(chapterId: String): ReadingAnchor = ReadingAnchor(
        paragraph = positions.getInt("$chapterId:paragraph", 0),
        character = positions.getInt("$chapterId:character", 0),
        scroll = positions.getInt("$chapterId:scroll", 0)
    )

    fun saveChapterAnchor(
        bookId: String,
        chapterId: String,
        chapterIndex: Int,
        anchor: ReadingAnchor
    ) {
        positions.edit()
            .putInt("$chapterId:paragraph", anchor.paragraph)
            .putInt("$chapterId:character", anchor.character)
            .putInt("$chapterId:scroll", anchor.scroll)
            .putInt("$bookId:lastChapter", chapterIndex)
            .apply()
    }
    private object Keys {
        val fontSize = intPreferencesKey("reader_font_size")
        val lineSpacing = floatPreferencesKey("reader_line_spacing")
        val hPadding = intPreferencesKey("reader_h_padding")
        val paraSpacing = intPreferencesKey("reader_para_spacing")
        val pageMode = stringPreferencesKey("reader_page_mode_v2")
        val bgTheme = stringPreferencesKey("reader_bg_theme")
        val serif = booleanPreferencesKey("reader_serif")
        val appTheme = stringPreferencesKey("app_theme_mode")
        val onboardingDone = booleanPreferencesKey("onboarding_done")
    }

    val onboardingDoneFlow: Flow<Boolean> = dataStore.data.map { it[Keys.onboardingDone] ?: false }

    suspend fun setOnboardingDone() = dataStore.edit { it[Keys.onboardingDone] = true }

    private val ttsSpeed = floatPreferencesKey("tts_speed")

    suspend fun setTtsSpeed(speed: Float) = dataStore.edit { it[ttsSpeed] = speed.coerceIn(0.5f, 3f) }
    suspend fun currentTtsSpeed(): Float = dataStore.data.map { it[ttsSpeed] ?: 1.0f }.first()

    private val ttsVoiceName = stringPreferencesKey("tts_voice_name")
    private val listenPillX = floatPreferencesKey("listen_pill_x_frac")
    private val listenPillY = floatPreferencesKey("listen_pill_y_frac")

    /** 听书音色（null = 系统默认音色） */
    suspend fun setTtsVoice(name: String?) = dataStore.edit {
        if (name == null) it.remove(ttsVoiceName) else it[ttsVoiceName] = name
    }

    suspend fun currentTtsVoice(): String? = dataStore.data.map { it[ttsVoiceName] }.first()

    /** 听书悬浮胶囊位置（屏幕比例坐标 0..1，跨分辨率恢复） */
    val listenPillPosition: Flow<Pair<Float, Float>> = dataStore.data.map { p ->
        (p[listenPillX] ?: 0.78f) to (p[listenPillY] ?: 0.52f)
    }

    suspend fun setListenPillPosition(xFraction: Float, yFraction: Float) = dataStore.edit {
        it[listenPillX] = xFraction.coerceIn(0f, 1f)
        it[listenPillY] = yFraction.coerceIn(0f, 1f)
    }

    val settings: Flow<ReadingSettings> = dataStore.data.map { p ->
        ReadingSettings(
            fontSizeSp = p[Keys.fontSize] ?: 18,
            lineSpacingMultiplier = p[Keys.lineSpacing] ?: 1.65f,
            horizontalPaddingDp = p[Keys.hPadding] ?: 22,
            paragraphSpacingDp = p[Keys.paraSpacing] ?: 10,
            pageMode = p[Keys.pageMode]?.let { runCatching { PageMode.valueOf(it) }.getOrNull() } ?: PageMode.PAGED,
            bgTheme = p[Keys.bgTheme]?.let { runCatching { ReaderBgTheme.valueOf(it) }.getOrNull() } ?: ReaderBgTheme.WHITE,
            serifFont = p[Keys.serif] ?: false,
            appThemeMode = p[Keys.appTheme]?.let { runCatching { AppThemeMode.valueOf(it) }.getOrNull() }
                ?: AppThemeMode.FOLLOW_SYSTEM
        )
    }

    suspend fun current(): ReadingSettings = settings.first()

    suspend fun setFontSize(size: Int) = dataStore.edit { it[Keys.fontSize] = size.coerceIn(12, 32) }
    suspend fun setLineSpacing(m: Float) = dataStore.edit { it[Keys.lineSpacing] = m.coerceIn(1.2f, 2.4f) }
    suspend fun setHorizontalPadding(dp: Int) = dataStore.edit { it[Keys.hPadding] = dp.coerceIn(8, 48) }
    suspend fun setParagraphSpacing(dp: Int) = dataStore.edit { it[Keys.paraSpacing] = dp.coerceIn(2, 28) }
    suspend fun setPageMode(mode: PageMode) = dataStore.edit { it[Keys.pageMode] = mode.name }
    suspend fun setBgTheme(theme: ReaderBgTheme) = dataStore.edit { it[Keys.bgTheme] = theme.name }
    suspend fun setSerif(serif: Boolean) = dataStore.edit { it[Keys.serif] = serif }
    suspend fun setAppThemeMode(mode: AppThemeMode) = dataStore.edit { it[Keys.appTheme] = mode.name }

    companion object {
        fun createDataStore(context: Context): DataStore<Preferences> =
            androidx.datastore.preferences.core.PreferenceDataStoreFactory.create(
                produceFile = { context.preferencesDataStoreFile("novel_ai_settings") }
            )
    }
}
