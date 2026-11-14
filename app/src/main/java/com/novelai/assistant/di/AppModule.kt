package com.novelai.assistant.di

import android.content.ContentResolver
import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.room.Room
import com.novelai.assistant.data.db.BookDao
import com.novelai.assistant.data.db.CategoryDao
import com.novelai.assistant.data.db.ChapterDao
import com.novelai.assistant.data.db.ChatDao
import com.novelai.assistant.data.db.NovelDatabase
import com.novelai.assistant.data.db.SF_MIGRATION_1_2
import com.novelai.assistant.data.db.SF_MIGRATION_2_3
import com.novelai.assistant.data.prefs.ReadingPreferencesRepository
import com.novelai.assistant.data.sf.KtorSfTransport
import com.novelai.assistant.data.sf.SfApi
import com.novelai.assistant.data.sf.SfHttpClientFactory
import com.novelai.assistant.data.sf.SfNonRedirectClient
import com.novelai.assistant.data.sf.SfProtocol
import com.novelai.assistant.data.sf.SfRequestGuard
import com.novelai.assistant.data.sf.SfSafetyStore
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.serialization.json.Json
import java.util.concurrent.TimeUnit
import javax.inject.Qualifier
import javax.inject.Singleton

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    @ApplicationScope
    fun provideApplicationScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Provides
    fun provideContentResolver(@ApplicationContext context: Context): ContentResolver =
        context.contentResolver

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): NovelDatabase =
        Room.databaseBuilder(context, NovelDatabase::class.java, "novel_ai.db")
            .addMigrations(SF_MIGRATION_1_2, SF_MIGRATION_2_3)
            .build()

    @Provides
    @Singleton
    fun provideSfApi(
        transport: KtorSfTransport,
        safetyStore: SfSafetyStore
    ): SfApi {
        val guard = SfRequestGuard(safetyStore.load(), safetyStore::save)
        return SfApi(transport, SfProtocol.COMPATIBLE_DEVICE, guard)
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
    @SfNonRedirectClient
    fun provideSfNonRedirectClient(): HttpClient = SfHttpClientFactory.nonRedirectingClient

    @Provides
    @Singleton
    fun provideHttpClient(json: Json): HttpClient = HttpClient(OkHttp) {
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
            json(json)
        }
    }
}
