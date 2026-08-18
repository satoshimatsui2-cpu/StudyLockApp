package com.stulab.studylockapp.data.db

import androidx.room.Dao
import androidx.room.Query
import com.stulab.studylockapp.data.WordEntity

@Dao
interface ProgressSummaryDao {

    @Query("SELECT COUNT(*) FROM words WHERE grade = :grade")
    suspend fun getTotalWordCount(grade: Int): Int

    @Query("""
        SELECT COUNT(*) FROM word_mastery 
        WHERE wordId IN (SELECT `no` FROM words WHERE grade = :grade)
          AND level >= 5 
          AND enToJpCorrects >= 1 
          AND jpToEnCorrects >= 1
          AND listenCorrects >= 1
    """)
    suspend fun getShortTermMasterCount(grade: Int): Int

    @Query("""
        SELECT COUNT(*) FROM word_mastery 
        WHERE wordId IN (SELECT `no` FROM words WHERE grade = :grade)
          AND level >= 10 
          AND enToJpCorrects >= 1 
          AND jpToEnCorrects >= 3 
          AND listenCorrects >= 3
    """)
    suspend fun getLongTermMasterCount(grade: Int): Int

    @Query("""
        SELECT COUNT(*) FROM spelling_progress
        WHERE wordId IN (SELECT `no` FROM words WHERE grade = :grade)
          AND status = 'CLEARED'
    """)
    suspend fun getSpellingClearedCount(grade: Int): Int

    @Query("""
        SELECT COUNT(*) FROM voice_check_results
        WHERE wordId IN (SELECT `no` FROM words WHERE grade = :grade)
          AND checkType = 'word'
          AND checked = 1
    """)
    suspend fun getWordPronunciationClearedCount(grade: Int): Int

    @Query("""
        SELECT COUNT(*) FROM voice_check_results
        WHERE wordId IN (SELECT `no` FROM words WHERE grade = :grade)
          AND checkType = 'sentence'
          AND checked = 1
    """)
    suspend fun getSentencePronunciationClearedCount(grade: Int): Int
    
    @Query("SELECT * FROM words WHERE grade = :grade")
    suspend fun getWordsByGrade(grade: Int): List<WordEntity>
}
