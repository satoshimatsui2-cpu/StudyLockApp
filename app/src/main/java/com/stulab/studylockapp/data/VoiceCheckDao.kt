package com.stulab.studylockapp.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface VoiceCheckDao {
    @Query("SELECT * FROM voice_check_results WHERE wordId IN (:wordIds) AND checkType = :checkType")
    suspend fun getResultsByIds(wordIds: List<Long>, checkType: String = "word"): List<VoiceCheckResultEntity>

    @Query("SELECT * FROM voice_check_results WHERE wordId IN (:wordIds)")
    suspend fun getAllResultsByIds(wordIds: List<Long>): List<VoiceCheckResultEntity>

    @Query("SELECT * FROM voice_check_results WHERE wordId = :wordId AND checkType = :checkType")
    suspend fun getResult(wordId: Long, checkType: String = "word"): VoiceCheckResultEntity?

    @Query("SELECT COUNT(DISTINCT wordId) FROM voice_check_results WHERE wordId IN (SELECT `no` FROM words WHERE grade = :grade) AND checkType = 'word' AND checked = 1")
    fun countPassedPronunciationWordsFlow(grade: Int): Flow<Int>

    @Query("SELECT COUNT(DISTINCT `no`) FROM words WHERE grade = :grade AND `no` IN (SELECT wordId FROM word_mastery WHERE challengeCount > 0) AND `no` NOT IN (SELECT wordId FROM voice_check_results WHERE checkType = 'word' AND checked = 1)")
    fun countEligiblePronunciationWordsFlow(grade: Int): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnore(entity: VoiceCheckResultEntity): Long

    @Update
    suspend fun updateResult(entity: VoiceCheckResultEntity)

    @Transaction
    suspend fun recordResult(
        wordId: Long,
        isSuccess: Boolean,
        confidence: Float,
        checkType: String = "word"
    ) {
        val existing = getResult(wordId, checkType)
        val now = System.currentTimeMillis()

        if (existing == null) {
            val newEntity = VoiceCheckResultEntity(
                wordId = wordId,
                checkType = checkType,
                checked = isSuccess,
                successCount = if (isSuccess) 1 else 0,
                attemptCount = 1,
                bestConfidence = if (isSuccess) confidence else 0f,
                lastCheckedAt = if (isSuccess) now else null,
                updatedAt = now
            )
            insertIgnore(newEntity)
        } else {
            val updatedEntity = existing.copy(
                checked = existing.checked || isSuccess,
                successCount = if (isSuccess) existing.successCount + 1 else existing.successCount,
                attemptCount = existing.attemptCount + 1,
                bestConfidence = if (isSuccess) maxOf(existing.bestConfidence, confidence) else existing.bestConfidence,
                lastCheckedAt = if (isSuccess) now else existing.lastCheckedAt,
                updatedAt = now
            )
            updateResult(updatedEntity)
        }
    }

    /**
     * 指定された単語IDの範囲の発音チェック結果を削除。
     * マイ単語帳の入れ替え時に使用。
     */
    @Query("DELETE FROM voice_check_results WHERE wordId >= :startId AND wordId <= :endId")
    suspend fun deleteByWordIdRange(startId: Long, endId: Long)
}
