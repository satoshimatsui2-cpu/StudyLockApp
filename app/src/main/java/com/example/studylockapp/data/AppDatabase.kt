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
import com.example.studylockapp.data.db.UnlockHistoryDao
import com.example.studylockapp.data.db.WordDao
import com.example.studylockapp.data.db.WordProgressDao

@Database(
    entities = [
        WordEntity::class,
        WordProgressEntity::class,
        PointHistoryEntity::class,
        LockedAppEntity::class,
        AppUnlockEntity::class,
        UnlockHistoryEntity::class
    ],
    version = 5, 
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun wordDao(): com.example.studylockapp.data.db.WordDao
    abstract fun wordProgressDao(): com.example.studylockapp.data.db.WordProgressDao
    abstract fun pointHistoryDao(): com.example.studylockapp.data.db.PointHistoryDao
    abstract fun lockedAppDao(): com.example.studylockapp.data.db.LockedAppDao
    abstract fun appUnlockDao(): com.example.studylockapp.data.db.AppUnlockDao
    abstract fun unlockHistoryDao(): com.example.studylockapp.data.db.UnlockHistoryDao

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