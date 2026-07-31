package com.stulab.studylockapp.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.stulab.studylockapp.data.db.AppUnlockDao
import com.stulab.studylockapp.data.db.LockedAppDao
import com.stulab.studylockapp.data.db.PracticalHistoryDao
import com.stulab.studylockapp.data.db.StudyLogDao
import com.stulab.studylockapp.data.db.UnlockHistoryDao
import com.stulab.studylockapp.data.db.WordDao
import com.stulab.studylockapp.data.db.WordMasteryDao
import com.stulab.studylockapp.data.db.FavoriteWordDao
import com.stulab.studylockapp.data.db.FavoriteWordEntity
import com.stulab.studylockapp.data.db.WordMasteryEntity
import com.stulab.studylockapp.data.db.PracticalHistoryEntity
import com.stulab.studylockapp.data.db.LockedAppEntity
import com.stulab.studylockapp.data.db.AppUnlockEntity

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
        PracticalHistoryEntity::class,
        FavoriteWordEntity::class
    ],
    version = 26,
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
    abstract fun favoriteWordDao(): FavoriteWordDao

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

        val MIGRATION_23_24 = object : Migration(23, 24) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // 1. カラムの追加
                db.execSQL("ALTER TABLE practical_history ADD COLUMN isScored INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE practical_history ADD COLUMN usedReplay INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE practical_history ADD COLUMN resultStatus TEXT NOT NULL DEFAULT 'UNSCORED'")

                // 2. 既存データの移行: isCorrect に基づいて CORRECT / WRONG を割り振る
                db.execSQL("""
                    UPDATE practical_history
                    SET resultStatus = CASE
                        WHEN isCorrect = 1 THEN 'CORRECT'
                        ELSE 'WRONG'
                    END
                """.trimIndent())
            }
        }

        val MIGRATION_24_25 = object : Migration(24, 25) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE practical_history ADD COLUMN ttsScriptSnapshot TEXT")
            }
        }

        val MIGRATION_25_26 = object : Migration(25, 26) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `favorite_words` (
                        `wordId` INTEGER NOT NULL, 
                        `createdAt` INTEGER NOT NULL, 
                        PRIMARY KEY(`wordId`)
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
                    .addMigrations(
                        MIGRATION_21_22, 
                        MIGRATION_22_23, 
                        MIGRATION_23_24, 
                        MIGRATION_24_25,
                        MIGRATION_25_26
                    )
                    .build()
                    .also { INSTANCE = it }
            }
    }
}
