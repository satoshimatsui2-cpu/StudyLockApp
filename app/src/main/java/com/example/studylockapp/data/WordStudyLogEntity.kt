package com.example.studylockapp.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "study_logs")
data class WordStudyLogEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val wordId: Int,
    val mode: String,       // "英日"などのモードも記録しておくと後で便利です
    val learnedAt: Long     // 学習した日時 (Epoch millis)
)