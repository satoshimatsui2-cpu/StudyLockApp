package com.example.studylockapp.learning

import android.content.Context
import android.util.Log
import com.example.studylockapp.data.AppDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object LearningLogic {

    /**
     * 復習対象のIDリストを苦手度（直近ミス・累計ミス）＋SRSでソートする
     */
    suspend fun prioritizeWeakWords(
        context: Context,
        dueIds: List<Int>,
        mode: String
    ): List<Int> {

        if (dueIds.isEmpty()) return emptyList()

        val db = AppDatabase.getInstance(context)
        val progressDao = db.wordProgressDao()

        // DBアクセス（IOスレッド）
        val progressList = withContext(Dispatchers.IO) {
            progressDao.getProgressByIds(dueIds, mode)
        }

        val progressMap = progressList.associateBy { it.wordId }

        // --- スコアを事前計算（ログもここで出す） ---
        val scoreMap = mutableMapOf<Int, Int>()

        dueIds.forEachIndexed { index, id ->

            val p = progressMap[id]

            val score = if (p == null) {
                Log.d("WeakPriority", "wordId=$id score=0 (no data)")
                0
            } else {
                val lastResultPenalty = if (!p.lastResult) 1000 else 0
                val cumulativePenalty = p.wrongCount * 50

                // SRS順（早いものほど少し優遇）
                val srsPriority = (dueIds.size - index)

                val total = lastResultPenalty + cumulativePenalty + srsPriority

                Log.d(
                    "WeakPriority",
                    "wordId=$id score=$total lastResult=${p.lastResult} wrongCount=${p.wrongCount}"
                )

                total
            }

            scoreMap[id] = score
        }

        // --- ソート ---
        val sorted = dueIds.sortedByDescending { scoreMap[it] ?: 0 }

        // 最終ログ
        Log.d("WeakPriority", "sorted=$sorted")

        return sorted
    }
}