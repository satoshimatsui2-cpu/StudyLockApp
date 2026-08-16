package com.stulab.studylockapp.data.db

import androidx.room.*

@Dao
interface ChoiceMeaningDao {

    @Upsert
    suspend fun upsertAll(meanings: List<ChoiceMeaningEntity>)

    @Query("SELECT * FROM choice_meanings WHERE normalizedText IN (:normalizedTexts)")
    suspend fun getByNormalizedTexts(normalizedTexts: List<String>): List<ChoiceMeaningEntity>

    @Query("DELETE FROM choice_meanings")
    suspend fun deleteAll()

    @Query("SELECT COUNT(*) FROM choice_meanings")
    suspend fun countAll(): Int
}
