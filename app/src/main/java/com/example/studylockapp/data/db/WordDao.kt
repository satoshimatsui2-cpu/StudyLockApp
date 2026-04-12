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

    @Query("SELECT * FROM words WHERE word IN (:spellings)")
    suspend fun getWordsBySpellings(spellings: List<String>): List<WordEntity>

    @Query("""
        SELECT * FROM words 
        WHERE grade = :level 
        ORDER BY RANDOM() 
        LIMIT 1
    """)
    suspend fun getRandomWordByLevel(level: Int): WordEntity?

    @Query("SELECT * FROM words ORDER BY RANDOM() LIMIT 1")
    suspend fun getAnyRandomWord(): WordEntity?

    @Query("""
        SELECT word FROM words 
        WHERE word != :excludeWord AND grade = :grade AND word LIKE :prefix || '%'
        ORDER BY RANDOM() 
        LIMIT :limit
    """)
    suspend fun getWordsByPrefix(excludeWord: String, grade: Int, prefix: String, limit: Int): List<String>

    @Query("""
        SELECT word FROM words 
        WHERE word != :excludeWord AND grade = :grade 
        AND length(word) BETWEEN :minLen AND :maxLen
        ORDER BY RANDOM() 
        LIMIT :limit
    """)
    suspend fun getWordsByLengthRange(excludeWord: String, grade: Int, minLen: Int, maxLen: Int, limit: Int): List<String>

    @Query("""
        SELECT word FROM words 
        WHERE word != :excludeWord AND grade = :grade AND type = :type 
        AND length(word) BETWEEN :minLen AND :maxLen
        ORDER BY RANDOM() 
        LIMIT :limit
    """)
    suspend fun getWordsByLengthRangeAndType(excludeWord: String, grade: Int, type: String, minLen: Int, maxLen: Int, limit: Int): List<String>

    @Query("""
        SELECT word FROM words 
        WHERE word != :excludeWord AND grade = :grade
        ORDER BY RANDOM() 
        LIMIT :limit
    """)
    suspend fun getRandomDistractors(
        excludeWord: String,
        grade: Int,
        limit: Int
    ): List<String>

    @Query("""
        SELECT word FROM words 
        WHERE word != :excludeWord AND grade = :grade AND type = :type
        ORDER BY RANDOM() 
        LIMIT :limit
    """)
    suspend fun getRandomDistractorsByType(
        excludeWord: String,
        grade: Int,
        type: String,
        limit: Int
    ): List<String>

    @Query("""
        SELECT word FROM words 
        WHERE word != :excludeWord AND grade = :grade AND pos = :pos AND type = :type
        ORDER BY RANDOM() 
        LIMIT :limit
    """)
    suspend fun getRandomDistractorsByPosAndType(
        excludeWord: String,
        grade: Int,
        pos: String,
        type: String,
        limit: Int
    ): List<String>

    @Query("SELECT japanese FROM words WHERE word IN (:words)")
    suspend fun getJapaneseByWords(words: List<String>): List<String>

    @Query("""
        SELECT japanese FROM words 
        WHERE japanese != :excludeJp AND grade = :grade 
        ORDER BY RANDOM() 
        LIMIT :limit
    """)
    suspend fun getRandomJapaneseDistractors(
        excludeJp: String,
        grade: Int,
        limit: Int
    ): List<String>

    @Query("""
        SELECT japanese FROM words 
        WHERE japanese != :excludeJp AND grade = :grade AND type = :type
        ORDER BY RANDOM() 
        LIMIT :limit
    """)
    suspend fun getRandomJapaneseDistractorsByType(
        excludeJp: String,
        grade: Int,
        type: String,
        limit: Int
    ): List<String>

    @Query("""
        SELECT japanese FROM words 
        WHERE japanese != :excludeJp AND grade = :grade AND pos = :pos AND type = :type
        ORDER BY RANDOM() 
        LIMIT :limit
    """)
    suspend fun getRandomJapaneseDistractorsByPosAndType(
        excludeJp: String,
        grade: Int,
        pos: String,
        type: String,
        limit: Int
    ): List<String>
}
