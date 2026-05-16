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
     * 明確に「間違い」と判定された問題を、直近のものから順に取得します。
     * リプレイによる「採点対象外(UNSCORED)」は含めません。
     */
    @Query("SELECT * FROM practical_history WHERE resultStatus = 'WRONG' ORDER BY answeredAt DESC")
    suspend fun getWrongAnswers(): List<PracticalHistoryEntity>

    /**
     * 正解した問題の総数を取得します。
     */
    @Query("SELECT COUNT(*) FROM practical_history WHERE resultStatus = 'CORRECT'")
    suspend fun getCorrectCount(): Int

    /**
     * 実践テストでの合計獲得ポイントを取得します。
     */
    @Query("SELECT COALESCE(SUM(points), 0) FROM practical_history")
    suspend fun getTotalPoints(): Int

    /**
     * 採点対象の履歴のみを取得します。
     */
    @Query("SELECT * FROM practical_history WHERE isScored = 1 ORDER BY answeredAt DESC")
    suspend fun getScoredHistory(): List<PracticalHistoryEntity>

    /**
     * 採点対象外（もう一度聞いた等）の履歴のみを取得します。
     */
    @Query("SELECT * FROM practical_history WHERE isScored = 0 ORDER BY answeredAt DESC")
    suspend fun getUnscoredHistory(): List<PracticalHistoryEntity>
}
