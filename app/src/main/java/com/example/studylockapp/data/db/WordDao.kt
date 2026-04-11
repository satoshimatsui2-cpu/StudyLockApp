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

    // --- EN_TO_JP 用に追加 ---

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
}
