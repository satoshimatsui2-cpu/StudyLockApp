package com.example.studylockapp.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

/**
 * 実践テスト履歴用のDAO
 */
@Dao
interface PracticalHistoryDao {
    @Insert
    suspend fun insert(history: PracticalHistoryEntity)

    /**
     * すべての履歴を、新しい順に取得します。
     */
    @Query("SELECT * FROM practical_history ORDER BY answeredAt DESC")
    suspend fun getAllHistory(): List<PracticalHistoryEntity>

    /**
     * 間違えた問題を、直近のものから順に取得します。
     */
    @Query("SELECT * FROM practical_history WHERE isCorrect = 0 ORDER BY answeredAt DESC")
    suspend fun getWrongAnswers(): List<PracticalHistoryEntity>

    /**
     * 正解した問題の総数を取得します。
     */
    @Query("SELECT COUNT(*) FROM practical_history WHERE isCorrect = 1")
    suspend fun getCorrectCount(): Int

    /**
     * 実践テストでの合計獲得ポイントを取得します。履歴がない場合は 0 を返します。
     */
    @Query("SELECT COALESCE(SUM(points), 0) FROM practical_history")
    suspend fun getTotalPoints(): Int
}
