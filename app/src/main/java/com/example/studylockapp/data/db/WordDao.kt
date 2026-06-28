package com.example.studylockapp.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import com.example.studylockapp.data.WordEntity
import com.example.studylockapp.data.WordHistoryQueryResult

@Dao
interface WordDao {

    @Upsert
    suspend fun upsertAll(words: List<WordEntity>)

    @Query("SELECT COUNT(*) FROM words")
    suspend fun countAllWords(): Int

    @Query("SELECT * FROM words")
    suspend fun getAll(): List<WordEntity>

    @Query("SELECT * FROM words WHERE no = :id LIMIT 1")
    suspend fun getWordById(id: Int): WordEntity?

    @Query("SELECT * FROM words WHERE word = :spelling LIMIT 1")
    suspend fun getWordBySpelling(spelling: String): WordEntity?

    @Query("SELECT * FROM words ORDER BY RANDOM() LIMIT 1")
    suspend fun getAnyRandomWord(): WordEntity?

    /**
     * 指定された学習レベル（grade）からランダムに1件取得します。
     */
    @Query("""
        SELECT * FROM words 
        WHERE grade = :grade 
        ORDER BY RANDOM() 
        LIMIT 1
    """)
    suspend fun getRandomWordByGrade(grade: Int): WordEntity?

    /**
     * 未学習（マスタリーレコードがない）の単語を
     * 指定されたグレードから優先順位に従って1件取得します。
     * 優先順位: frequency DESC, difficulty ASC, no ASC
     */
    @Query("""
        SELECT w.*
        FROM words w
        LEFT JOIN word_mastery m ON w.no = m.wordId
        WHERE w.grade = :grade
          AND m.wordId IS NULL
        ORDER BY 
            (CASE WHEN COALESCE(w.frequency, 0) >= 3 THEN 0 ELSE 1 END) ASC,
            (CASE WHEN COALESCE(w.difficulty, 0) < 5 THEN 0 ELSE 1 END) ASC,
            RANDOM(),
            w.no ASC
        LIMIT 1
    """)
    suspend fun getPriorityNewWordByGrade(grade: Int): WordEntity?

    /**
     * 全グレードから未学習の単語を優先順位に従って1件取得します。
     */
    @Query("""
        SELECT w.*
        FROM words w
        LEFT JOIN word_mastery m ON w.no = m.wordId
        WHERE m.wordId IS NULL
        ORDER BY 
            (CASE WHEN COALESCE(w.frequency, 0) >= 3 THEN 0 ELSE 1 END) ASC,
            (CASE WHEN COALESCE(w.difficulty, 0) < 5 THEN 0 ELSE 1 END) ASC,
            RANDOM(),
            w.no ASC
        LIMIT 1
    """)
    suspend fun getPriorityNewWordFromAnyGrade(): WordEntity?

    @Query("""
        SELECT japanese FROM words 
        WHERE grade = :grade AND japanese != :excludeJp
        ORDER BY RANDOM() 
        LIMIT :limit
    """)
    suspend fun getRandomJapaneseDistractors(grade: Int, excludeJp: String, limit: Int): List<String>

    @Query("""
        SELECT word FROM words 
        WHERE grade = :grade AND word != :excludeWord
        ORDER BY RANDOM() 
        LIMIT :limit
    """)
    suspend fun getRandomEnglishDistractors(grade: Int, excludeWord: String, limit: Int): List<String>

    /**
     * 学習履歴（マスタリーレコードがある単語）を取得します。
     */
    @Query("""
        SELECT 
            w.no, w.word, w.japanese, w.description, w.sentence, w.japaneseSentence, w.pos, w.grade,
            m.level, m.scheduledMode, m.nextReviewTime, m.lastSeen, m.lastCorrectTime,
            m.challengeCount, m.successCount, m.failureCount, m.currentStreak, m.bestStreak,
            m.isBasicMastered, m.isLongTermMastered, m.pendingListenReview, m.deferredListenCount
        FROM words w
        INNER JOIN word_mastery m ON w.no = m.wordId
        ORDER BY m.lastSeen DESC, w.no ASC
    """)
    suspend fun getLearningHistory(): List<WordHistoryQueryResult>

    @Query("DELETE FROM words")
    suspend fun deleteAll()

    @Query("DELETE FROM word_mastery")
    suspend fun deleteAllMastery()

    @Query("DELETE FROM study_logs")
    suspend fun deleteAllStudyLogs()

    @Query("DELETE FROM voice_check_results")
    suspend fun deleteAllVoiceCheckResults()
}
