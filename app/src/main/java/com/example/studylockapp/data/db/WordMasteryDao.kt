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
}
