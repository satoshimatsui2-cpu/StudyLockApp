package com.example.studylockapp.data.db

import androidx.room.*

@Dao
interface WordMasteryDao {
    @Query("SELECT * FROM word_mastery WHERE wordId = :wordId")
    suspend fun getMastery(wordId: Int): WordMasteryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(mastery: WordMasteryEntity)

    @Query("SELECT * FROM word_mastery WHERE nextReviewTime > 0 AND nextReviewTime <= :now")
    suspend fun getDueMasteries(now: Long): List<WordMasteryEntity>

    /**
     * 指定レベルの単語数をカウント
     */
    @Query("SELECT COUNT(*) FROM word_mastery WHERE level = :level")
    suspend fun countByLevel(level: Int): Int

    /**
     * 基礎マスター数の集計
     * フラグではなく実力値（レベル5以上 + 全モード最低正解数）を直接カウントする
     */
    @Query("""
        SELECT COUNT(*) FROM word_mastery 
        WHERE level >= 5 
        AND enToJpCorrects >= 1 
        AND jpToEnCorrects >= 1
        AND listenCorrects >= 1
    """)
    suspend fun countBasicMastered(): Int

    /**
     * 長期マスター数の集計
     * フラグではなく実力値（レベル10以上 + 全モード複数回正解）を直接カウントする
     */
    @Query("""
        SELECT COUNT(*) FROM word_mastery 
        WHERE level >= 10 
        AND enToJpCorrects >= 1 
        AND jpToEnCorrects >= 3 
        AND listenCorrects >= 3
    """)
    suspend fun countLongTermMastered(): Int

    /**
     * 音声復習待ちかつ、再出題時刻を過ぎているものを取得
     */
    @Query("""
        SELECT * FROM word_mastery 
        WHERE pendingListenReview = 1 
        AND nextReviewTime <= :now
    """)
    suspend fun getPendingListenMasteries(now: Long): List<WordMasteryEntity>
    
    @Query("SELECT * FROM word_mastery")
    suspend fun getAllMasteries(): List<WordMasteryEntity>

    /**
     * 本日完了した新規単語数をカウント
     * 条件：レベル2以上、最終学習日が本日、かつ最初の学習記録が本日であること
     */
    @Query("""
        SELECT COUNT(*) FROM word_mastery m
        WHERE m.level >= 2 
        AND m.lastSeen BETWEEN :startOfDay AND :endOfDay
        AND m.wordId IN (
            SELECT wordId FROM study_logs 
            GROUP BY wordId 
            HAVING MIN(learnedAt) >= :startOfDay
        )
    """)
    suspend fun countCompletedNewWordsToday(startOfDay: Long, endOfDay: Long): Int

    /**
     * 今日新しく学習を開始した単語数をカウント
     * 条件：最初の学習記録が本日であること
     */
    @Query("""
        SELECT COUNT(*) FROM (
            SELECT wordId FROM study_logs 
            GROUP BY wordId 
            HAVING MIN(learnedAt) >= :startOfDay
        )
    """)
    suspend fun countStartedNewWordsToday(startOfDay: Long): Int

    /**
     * 本日分の残り復習数をカウント
     * 条件：
     * 1. 復習期限が本日中（23:59:59まで）に設定されている
     * 2. 指定された級（grade）に合致する（または他級復習ONなら全級）
     * 3. サイレントモードならリスニング問題を除外
     */
    /**
     * 現時点での残り復習数をカウント
     * 条件：
     * 1. 復習期限が現時点（:now）までに設定されている
     * 2. 指定された級（grade）に合致する（または他級復習ONなら全級）
     * 3. サイレントモードならリスニング問題を除外
     */
    @Query("""
        SELECT COUNT(*) FROM word_mastery
        WHERE nextReviewTime > 0 
        AND nextReviewTime <= :now
        AND (:includeOtherGrades = 1 OR wordId IN (SELECT no FROM words WHERE grade = :currentGrade))
        AND (:isSilentMode = 0 OR scheduledMode NOT IN ('LISTEN_EN', 'LISTEN_FILL_BLANK'))
    """)
    suspend fun countRemainingReviewsAvailable(
        now: Long,
        currentGrade: Int, 
        includeOtherGrades: Int,
        isSilentMode: Int
    ): Int
    @Query("""
        SELECT COUNT(*) FROM word_mastery m
        WHERE m.lastSeen BETWEEN :startOfDay AND :endOfDay
        AND m.wordId NOT IN (
            SELECT wordId FROM study_logs 
            GROUP BY wordId 
            HAVING MIN(learnedAt) >= :startOfDay
        )
    """)
    suspend fun countCompletedReviewsToday(startOfDay: Long, endOfDay: Long): Int
    @Query("""
        SELECT COUNT(*) FROM word_mastery 
        WHERE level >= 5 
        AND enToJpCorrects >= 1 
        AND jpToEnCorrects >= 1
        AND listenCorrects >= 1
        AND wordId IN (SELECT no FROM words WHERE grade = :grade)
    """)
    suspend fun countBasicMasteredByGrade(grade: Int): Int

    @Query("""
        SELECT COUNT(*) FROM word_mastery 
        WHERE level >= 10 
        AND enToJpCorrects >= 1 
        AND jpToEnCorrects >= 3 
        AND listenCorrects >= 3
        AND wordId IN (SELECT no FROM words WHERE grade = :grade)
    """)
    suspend fun countLongTermMasteredByGrade(grade: Int): Int

    @Query("SELECT COUNT(*) FROM words WHERE grade = :grade")
    suspend fun countTotalWordsByGrade(grade: Int): Int
}
