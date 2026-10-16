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
    fun stringToChapterOriginType(value: String): ChapterOriginType =
        ChapterOriginType.entries.firstOrNull { it.name == value } ?: ChapterOriginType.ORIGINAL
}

@Database(
    entities = [BookEntity::class, ChapterEntity::class, AiChatRecordEntity::class, CategoryEntity::class],
    version = 3,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class NovelDatabase : RoomDatabase() {
    abstract fun bookDao(): BookDao
    abstract fun chapterDao(): ChapterDao
    abstract fun chatDao(): ChatDao
    abstract fun categoryDao(): CategoryDao
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
