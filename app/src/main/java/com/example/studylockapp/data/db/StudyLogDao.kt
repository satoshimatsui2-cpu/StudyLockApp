package com.example.studylockapp.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.example.studylockapp.data.WordStudyLogEntity

@Dao
interface StudyLogDao {
    // ログの追加
    @Insert
    suspend fun insert(log: WordStudyLogEntity)

    /**
     * 指定期間内に学習した「ユニークな単語数」をカウントする
     * 同じ日に同じ単語を何度やっても、COUNT(DISTINCT wordId) なので "1" とカウントされます
     */
    @Query("SELECT COUNT(DISTINCT wordId) FROM study_logs WHERE learnedAt BETWEEN :startTime AND :endTime")
    suspend fun getLearnedWordCountInTerm(startTime: Long, endTime: Long): Int

    // (オプション) モード別で集計したい場合
    @Query("SELECT COUNT(DISTINCT wordId) FROM study_logs WHERE mode = :mode AND learnedAt BETWEEN :startTime AND :endTime")
    suspend fun getLearnedWordCountInTermByMode(mode: String, startTime: Long, endTime: Long): Int
}