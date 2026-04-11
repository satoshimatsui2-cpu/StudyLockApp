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
    suspend fun countAll(): Int

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
        WHERE difficulty <= :level 
        ORDER BY RANDOM() 
        LIMIT 1
    """)
    suspend fun getRandomWordByLevel(level: Int): WordEntity?

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
        WHERE word != :excludeWord AND grade = :grade AND pos = :pos
        ORDER BY RANDOM() 
        LIMIT :limit
    """)
    suspend fun getRandomDistractorsByPos(
        excludeWord: String,
        grade: Int,
        pos: String,
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
        WHERE japanese != :excludeJp AND grade = :grade AND pos = :pos
        ORDER BY RANDOM() 
        LIMIT :limit
    """)
    suspend fun getRandomJapaneseDistractorsByPos(
        excludeJp: String,
        grade: Int,
        pos: String,
        limit: Int
    ): List<String>
}
