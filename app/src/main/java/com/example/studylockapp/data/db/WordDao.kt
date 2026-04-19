package com.example.studylockapp.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.studylockapp.data.WordEntity

@Dao
interface WordDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(words: List<WordEntity>)

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
     * 未学習（マスタリーレコードがない、またはレベル0）の単語を
     * 指定されたグレードからランダムに1件取得します。
     */
    @Query("""
        SELECT w.*
        FROM words w
        LEFT JOIN word_mastery m ON w.no = m.wordId
        WHERE w.grade = :grade
          AND (m.wordId IS NULL OR m.level = 0)
        ORDER BY RANDOM()
        LIMIT 1
    """)
    suspend fun getRandomNewWordByGrade(grade: Int): WordEntity?

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
}
