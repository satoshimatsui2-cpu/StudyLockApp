package com.example.studylockapp
import com.example.studylockapp.data.AppDatabase

/**
 * 学習状況（復習件数やトロフィー率など）を計算するロジック
 */
object LearningStatsLogic {

    suspend fun computeModeStats(
        db: AppDatabase,
        wordIdSet: Set<Int>,
        mode: String,
        nowSec: Long,
        listeningQuestions: List<ListeningQuestion>
    ): ModeStats {
        val progressDao = db.wordProgressDao()

        // --- 会話モード（リスニングQ2）の特別処理 ---
        if (mode == LearningModes.TEST_LISTEN_Q2) {
            val allQIds = listeningQuestions.map { it.id }.toSet()
            if (allQIds.isEmpty()) {
                return ModeStats(0, 0, 0, 0, 0, 0, 0, 0)
            }
            
            val progresses = progressDao.getAllProgressForMode(mode)
            val dueCount = progressDao.getDueWordIdsOrdered(mode, nowSec).count { it in allQIds }

            val masteredCount = progresses.count { it.wordId in allQIds && it.level >= 6 }
            val startedCount = progresses.filter { it.wordId in allQIds }.size

            return ModeStats(
                review = dueCount,
                newCount = allQIds.size - startedCount,
                total = allQIds.size,
                bronze = masteredCount,
                silver = masteredCount,
                gold = masteredCount,
                crystal = masteredCount,
                purple = masteredCount
            )
        }

        // --- 通常モードの処理 ---
        val progresses = progressDao.getAllProgressForMode(mode)
        // 今回の対象単語（級フィルタ済み）に含まれる進捗だけを抽出
        val targetProgresses = progresses.filter { it.wordId in wordIdSet }
        
        // 復習対象のカウント
        val dueCount = progressDao.getDueWordIdsOrdered(mode, nowSec).count { it in wordIdSet }

        val bronze = targetProgresses.count { it.level >= 2 }
        val silver = targetProgresses.count { it.level >= 3 }
        val gold = targetProgresses.count { it.level >= 4 }
        val crystal = targetProgresses.count { it.level >= 5 }
        val purple = targetProgresses.count { it.level >= 6 }

        return ModeStats(
            review = dueCount,
            newCount = wordIdSet.size - targetProgresses.size,
            total = wordIdSet.size,
            bronze = bronze,
            silver = silver,
            gold = gold,
            crystal = crystal,
            purple = purple
        )
    }
}