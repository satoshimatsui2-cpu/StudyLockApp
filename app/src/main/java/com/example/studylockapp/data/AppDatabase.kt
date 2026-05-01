package com.example.studylockapp.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.studylockapp.data.db.*

@Database(
    entities = [
        WordEntity::class,
        PointHistoryEntity::class,
        LockedAppEntity::class,
        AppUnlockEntity::class,
        UnlockHistoryEntity::class,
        WordStudyLogEntity::class,
        WordMasteryEntity::class,
        VoiceCheckResultEntity::class
    ],
    version = 22,
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
    abstract fun voiceCheckDao(): VoiceCheckDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null

        val MIGRATION_21_22 = object : Migration(21, 22) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `voice_check_results` (
                        `wordId` INTEGER NOT NULL, 
                        `checkType` TEXT NOT NULL, 
                        `checked` INTEGER NOT NULL, 
                        `successCount` INTEGER NOT NULL, 
                        `attemptCount` INTEGER NOT NULL, 
                        `bestConfidence` REAL NOT NULL, 
                        `lastCheckedAt` INTEGER, 
                        `updatedAt` INTEGER NOT NULL, 
                        PRIMARY KEY(`wordId`, `checkType`)
                    )
                """.trimIndent())
            }
        }

        fun getInstance(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "app-db"
                )
                    .addMigrations(MIGRATION_21_22)
                    .build()
                    .also { INSTANCE = it }
            }
    }
}
