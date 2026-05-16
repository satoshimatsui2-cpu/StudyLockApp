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
        VoiceCheckResultEntity::class,
        PracticalHistoryEntity::class
    ],
    version = 23,
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
    abstract fun practicalHistoryDao(): PracticalHistoryDao

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

        val MIGRATION_22_23 = object : Migration(22, 23) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `practical_history` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, 
                        `questionNo` TEXT NOT NULL, 
                        `questionType` TEXT NOT NULL, 
                        `grade` INTEGER NOT NULL, 
                        `unit` TEXT NOT NULL, 
                        `questionText` TEXT NOT NULL, 
                        `choicesJson` TEXT NOT NULL, 
                        `correctAnswer` TEXT NOT NULL, 
                        `selectedAnswer` TEXT NOT NULL, 
                        `isCorrect` INTEGER NOT NULL, 
                        `points` INTEGER NOT NULL, 
                        `explanation` TEXT NOT NULL, 
                        `answeredAt` INTEGER NOT NULL, 
                        `sessionId` TEXT NOT NULL
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
                    .addMigrations(MIGRATION_21_22, MIGRATION_22_23)
                    .build()
                    .also { INSTANCE = it }
            }
    }
}
