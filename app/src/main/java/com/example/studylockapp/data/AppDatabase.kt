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
        WordMasteryEntity::class
    ],
    version = 18,
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

        private val MIGRATION_16_17 = object : Migration(16, 17) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // 初期テーブル作成 (Attemptsあり版)
                createMasteryTable(db)
            }
        }

        private val MIGRATION_17_18 = object : Migration(17, 18) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // スキーマハッシュ不一致解消のため、一度削除して正しい定義で再作成
                db.execSQL("DROP TABLE IF EXISTS `word_mastery`")
                createMasteryTable(db)
            }
        }

        private fun createMasteryTable(db: SupportSQLiteDatabase) {
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS `word_mastery` (
                    `wordId` INTEGER NOT NULL, 
                    `level` INTEGER NOT NULL, 
                    `scheduledMode` TEXT NOT NULL, 
                    `nextReviewTime` INTEGER NOT NULL, 
                    `challengeCount` INTEGER NOT NULL, 
                    `successCount` INTEGER NOT NULL, 
                    `failureCount` INTEGER NOT NULL, 
                    `enToJpAttempts` INTEGER NOT NULL, 
                    `enToJpCorrects` INTEGER NOT NULL, 
                    `jpToEnAttempts` INTEGER NOT NULL, 
                    `jpToEnCorrects` INTEGER NOT NULL, 
                    `listenAttempts` INTEGER NOT NULL, 
                    `listenCorrects` INTEGER NOT NULL, 
                    `currentStreak` INTEGER NOT NULL, 
                    `bestStreak` INTEGER NOT NULL, 
                    `isBasicMastered` INTEGER NOT NULL, 
                    `isLongTermMastered` INTEGER NOT NULL, 
                    `pendingListenReview` INTEGER NOT NULL, 
                    `deferredListenCount` INTEGER NOT NULL, 
                    `lastCorrectTime` INTEGER NOT NULL, 
                    `lastSeen` INTEGER NOT NULL, 
                    PRIMARY KEY(`wordId`), 
                    FOREIGN KEY(`wordId`) REFERENCES `words`(`no`) ON UPDATE NO ACTION ON DELETE CASCADE 
                )
            """)
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_word_mastery_wordId` ON `word_mastery` (`wordId`)")
        }

        fun getInstance(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "app-db"
                )
                    .addMigrations(MIGRATION_16_17, MIGRATION_17_18)
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { INSTANCE = it }
            }
    }
}