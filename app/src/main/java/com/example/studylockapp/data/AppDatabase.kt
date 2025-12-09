package com.example.studylockapp.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        WordEntity::class,
        WordProgressEntity::class  // 追加
    ],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun wordDao(): WordDao
    abstract fun wordProgressDao(): WordProgressDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null
        fun getInstance(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "words.db"
                )
                    // 開発中のみ楽をするなら↓を付ける（本番は Migration 推奨）
                    .fallbackToDestructiveMigration()
                    .build().also { INSTANCE = it }
            }
    }
}