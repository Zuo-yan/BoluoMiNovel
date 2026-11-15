package com.novelai.assistant.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters

class Converters {
    @TypeConverter
    fun bookSourceTypeToString(value: BookSourceType): String = value.name

    @TypeConverter
    fun stringToBookSourceType(value: String): BookSourceType =
        BookSourceType.entries.firstOrNull { it.name == value } ?: BookSourceType.LOCAL_TXT

    @TypeConverter
    fun chapterOriginTypeToString(value: ChapterOriginType): String = value.name

    @TypeConverter
    fun stringToChapterOriginType(value: String): ChapterOriginType = when (value) {
        ChapterOriginType.AI_CONTINUATION.name, "AI_BRANCH" -> ChapterOriginType.AI_CONTINUATION
        else -> ChapterOriginType.ORIGINAL
    }
}

@Database(
    entities = [
        BookEntity::class,
        ChapterEntity::class,
        AiChatRecordEntity::class,
        CategoryEntity::class,
        BookOutlineEntity::class,
        OutlineDiscussionEntity::class,
        ChapterSummaryEntity::class,
        RagVectorCacheEntity::class,
        BookmarkEntity::class
    ],
    version = 5,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class NovelDatabase : RoomDatabase() {
    abstract fun bookDao(): BookDao
    abstract fun chapterDao(): ChapterDao
    abstract fun chatDao(): ChatDao
    abstract fun categoryDao(): CategoryDao
    abstract fun outlineDao(): OutlineDao
    abstract fun outlineDiscussionDao(): OutlineDiscussionDao
    abstract fun chapterSummaryDao(): ChapterSummaryDao
    abstract fun ragVectorCacheDao(): RagVectorCacheDao
    abstract fun bookmarkDao(): BookmarkDao
}

val SF_MIGRATION_1_2 = object : androidx.room.migration.Migration(1, 2) {
    override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE chapters ADD COLUMN remoteId TEXT DEFAULT NULL")
        db.execSQL("ALTER TABLE chapters ADD COLUMN isPaid INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE chapters ADD COLUMN downloaded INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE chapters ADD COLUMN fetchError TEXT NOT NULL DEFAULT ''")
        // Old failed imports must not be treated as readable/exportable text.
        db.execSQL("UPDATE chapters SET content = '', fetchError = '旧版抓取失败，请重新获取本书目录' WHERE content LIKE '（本章抓取失败：%'")
    }
}

val SF_MIGRATION_2_3 = object : androidx.room.migration.Migration(2, 3) {
    override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE books ADD COLUMN inBookshelf INTEGER NOT NULL DEFAULT 1")
    }
}

val SF_MIGRATION_3_4 = object : androidx.room.migration.Migration(3, 4) {
    override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `book_outlines` (
                `bookId` TEXT NOT NULL,
                `masterOutline` TEXT NOT NULL,
                `upcomingPlan` TEXT NOT NULL,
                `useInContinuation` INTEGER NOT NULL,
                `updatedAt` INTEGER NOT NULL,
                PRIMARY KEY(`bookId`)
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `outline_discussions` (
                `id` TEXT NOT NULL,
                `bookId` TEXT NOT NULL,
                `anchorChapterId` TEXT,
                `role` TEXT NOT NULL,
                `content` TEXT NOT NULL,
                `quickOptionsJson` TEXT NOT NULL,
                `proposedMasterOutline` TEXT,
                `proposedUpcomingPlan` TEXT,
                `modelUsed` TEXT,
                `timestamp` INTEGER NOT NULL,
                PRIMARY KEY(`id`)
            )
            """.trimIndent()
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_outline_discussions_bookId_timestamp` ON `outline_discussions` (`bookId`, `timestamp`)"
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `chapter_summaries` (
                `chapterId` TEXT NOT NULL,
                `bookId` TEXT NOT NULL,
                `contentHash` TEXT NOT NULL,
                `plotSummary` TEXT NOT NULL,
                `characterStates` TEXT NOT NULL,
                `updatedAt` INTEGER NOT NULL,
                PRIMARY KEY(`chapterId`)
            )
            """.trimIndent()
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_chapter_summaries_bookId` ON `chapter_summaries` (`bookId`)"
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `rag_vector_cache` (
                `cacheKey` TEXT NOT NULL,
                `bookId` TEXT NOT NULL,
                `chapterId` TEXT NOT NULL,
                `contentHash` TEXT NOT NULL,
                `vectorBytes` BLOB NOT NULL,
                `updatedAt` INTEGER NOT NULL,
                PRIMARY KEY(`cacheKey`)
            )
            """.trimIndent()
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_rag_vector_cache_bookId` ON `rag_vector_cache` (`bookId`)"
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_rag_vector_cache_bookId_chapterId` ON `rag_vector_cache` (`bookId`, `chapterId`)"
        )
    }
}

val SF_MIGRATION_4_5 = object : androidx.room.migration.Migration(4, 5) {
    override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `bookmarks` (
                `id` TEXT NOT NULL,
                `bookId` TEXT NOT NULL,
                `chapterId` TEXT NOT NULL,
                `chapterIndex` INTEGER NOT NULL,
                `chapterTitle` TEXT NOT NULL,
                `paragraphIndex` INTEGER NOT NULL,
                `charOffset` INTEGER NOT NULL,
                `scrollOffset` INTEGER NOT NULL,
                `excerpt` TEXT NOT NULL,
                `createdAt` INTEGER NOT NULL,
                PRIMARY KEY(`id`)
            )
            """.trimIndent()
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_bookmarks_bookId_createdAt` ON `bookmarks` (`bookId`, `createdAt`)"
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_bookmarks_bookId_chapterId` ON `bookmarks` (`bookId`, `chapterId`)"
        )
    }
}
