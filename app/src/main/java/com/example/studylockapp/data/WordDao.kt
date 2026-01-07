package com.example.studylockapp.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.studylockapp.data.WordEntity

@Dao
interface WordDao {

    @Query("SELECT * FROM words")
    suspend fun getAll(): List<WordEntity>

    @Query("SELECT COUNT(*) FROM words WHERE grade = :grade")
    suspend fun countByGrade(grade: String): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(words: List<WordEntity>)

    @Update
    suspend fun update(word: WordEntity)

    // 追加: 単語の意味と説明を更新するクエリ
    @Query("UPDATE words SET japanese = :meaning, description = :description WHERE word = :word")
    suspend fun updateWordInfo(word: String, meaning: String, description: String): Int
}