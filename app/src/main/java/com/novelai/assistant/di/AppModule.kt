package com.novelai.assistant.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.room.Room
import com.novelai.assistant.data.db.CategoryDao
import com.novelai.assistant.data.db.ChatDao
import com.novelai.assistant.data.db.ChapterDao
import com.novelai.assistant.data.db.BookDao
import com.novelai.assistant.data.db.NovelDatabase
import com.novelai.assistant.data.prefs.ReadingPreferencesRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): NovelDatabase =
        Room.databaseBuilder(context, NovelDatabase::class.java, "novel_ai.db")
            .addMigrations(com.novelai.assistant.data.db.SF_MIGRATION_1_2, com.novelai.assistant.data.db.SF_MIGRATION_2_3)
            .build()

    @Provides
    @Singleton
    fun provideSfApi(transport: com.novelai.assistant.data.sf.KtorSfTransport, storage: com.novelai.assistant.data.security.SecureStorage): com.novelai.assistant.data.sf.SfApi {
        val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
        val key = "sf_request_safety_v1"
        val saved = if (!storage.containsEncrypted(key)) com.novelai.assistant.data.sf.SfSafetyState() else runCatching {
            json.decodeFromString<com.novelai.assistant.data.sf.SfSafetyState>(storage.getDecrypted(key) ?: error("unreadable"))
        }.getOrDefault(com.novelai.assistant.data.sf.SfSafetyState())
        val guard = com.novelai.assistant.data.sf.SfRequestGuard(saved, { storage.putEncrypted(key, json.encodeToString(it)) })
        return com.novelai.assistant.data.sf.SfApi(transport, com.novelai.assistant.data.sf.SfProtocol.COMPATIBLE_DEVICE, guard)
    }

    @Provides fun provideBookDao(db: NovelDatabase): BookDao = db.bookDao()
    @Provides fun provideChapterDao(db: NovelDatabase): ChapterDao = db.chapterDao()
    @Provides fun provideChatDao(db: NovelDatabase): ChatDao = db.chatDao()
    @Provides fun provideCategoryDao(db: NovelDatabase): CategoryDao = db.categoryDao()

    @Provides
    @Singleton
    fun provideDataStore(@ApplicationContext context: Context): DataStore<Preferences> =
        ReadingPreferencesRepository.createDataStore(context)

    @Provides
    @Singleton
    fun provideJson(): Json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        isLenient = true
    }

    @Provides
    @Singleton
    fun provideHttpClient(): HttpClient = HttpClient(OkHttp) {
        engine {
            config {
                connectTimeout(15, TimeUnit.SECONDS)
                // SSE 长流式响应：读超时放宽，不设整体超时
                readTimeout(300, TimeUnit.SECONDS)
                writeTimeout(60, TimeUnit.SECONDS)
                callTimeout(0, TimeUnit.MILLISECONDS)
                retryOnConnectionFailure(true)
            }
        }
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true; isLenient = true })
        }
    }
}
