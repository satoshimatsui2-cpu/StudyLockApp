package com.example.studylockapp.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.studylockapp.data.db.AppUnlockDao
import com.example.studylockapp.data.db.AppUnlockEntity
import com.example.studylockapp.data.db.LockedAppDao
import com.example.studylockapp.data.db.LockedAppEntity
import com.example.studylockapp.data.db.PointHistoryDao
import com.example.studylockapp.data.db.WordDao
import com.example.studylockapp.data.db.WordProgressDao

@Database(
    entities = [
        WordEntity::class,
        WordProgressEntity::class,
        PointHistoryEntity::class,
        LockedAppEntity::class,
        AppUnlockEntity::class
    ],
    version = 4, // 現在の値に +1 していることを確認
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun wordDao(): WordDao
    abstract fun wordProgressDao(): WordProgressDao
    abstract fun pointHistoryDao(): PointHistoryDao
    abstract fun lockedAppDao(): LockedAppDao
    abstract fun appUnlockDao(): AppUnlockDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "app-db"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { INSTANCE = it }
            }
    }
}