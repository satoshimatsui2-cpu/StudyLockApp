package com.example.studylockapp.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface WordDao {

    // すべての単語を取得（CsvImporter / MainActivity / WordListActivity で使用）
    @Query("SELECT * FROM words")
    suspend fun getAll(): List<WordEntity>

    // IDでまとめて取得したい場合に使用（必要に応じてどうぞ）
    @Query("SELECT * FROM words WHERE no IN (:ids)")
    suspend fun getByIds(ids: List<Int>): List<WordEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(words: List<WordEntity>)

    @Query("DELETE FROM words")
    suspend fun deleteAll()
    // 既存の getAll(), insertAll() はそのまま

    @Query("SELECT COUNT(*) FROM words")
    suspend fun getCount(): Int
    // getAll(), insertAll() は既存のまま

}

