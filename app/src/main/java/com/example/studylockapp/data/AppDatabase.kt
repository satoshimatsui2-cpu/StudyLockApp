package com.example.studylockapp.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.example.studylockapp.data.db.*

@Database(
    entities = [
        WordEntity::class,
        PointHistoryEntity::class,
        LockedAppEntity::class,
        AppUnlockEntity::class,
        UnlockHistoryEntity::class,
        WordStudyLogEntity::class,
        WordMasteryEntity::class
    ],
    version = 21, // TSV形式への移行に伴いバージョンアップ
    exportSchema = false
)
@TypeConverters(WordConverters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun wordDao(): WordDao
    abstract fun pointHistoryDao(): PointHistoryDao
    abstract fun lockedAppDao(): LockedAppDao
    abstract fun appUnlockDao(): AppUnlockDao
    abstract fun unlockHistoryDao(): UnlockHistoryDao
    abstract fun studyLogDao(): StudyLogDao
    abstract fun wordMasteryDao(): WordMasteryDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "app-db"
                )
                    .fallbackToDestructiveMigration() // リリース前のため、スキーマ変更時はDBリセットを許容
                    .build()
                    .also { INSTANCE = it }
            }
    }
}
