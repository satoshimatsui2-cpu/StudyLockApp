package com.example.studylockapp.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.studylockapp.data.db.AppUnlockDao
import com.example.studylockapp.data.db.AppUnlockEntity
import com.example.studylockapp.data.db.LockedAppDao
import com.example.studylockapp.data.db.LockedAppEntity

@Database(
    entities = [
        WordEntity::class,
        WordProgressEntity::class,
        PointHistoryEntity::class,
        LockedAppEntity::class,
        AppUnlockEntity::class
    ],
    version = 6, // 今使っているバージョンに合わせてください
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun wordDao(): WordDao
    abstract fun wordProgressDao(): WordProgressDao
    abstract fun pointHistoryDao(): PointHistoryDao

    abstract fun lockedAppDao(): LockedAppDao      // ← ここが error になっていたので明示
    abstract fun appUnlockDao(): AppUnlockDao      // ← 同上

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "app_database"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}