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

        val targetIds = when (mode) {
            LearningModes.TEST_LISTEN_Q2 -> listeningQuestions.map { it.id }.toSet()
            LearningModes.TEST_FILL_BLANK -> wordIdSet
            else -> wordIdSet
        }

        if (targetIds.isEmpty()) {
            return ModeStats(0, 0, 0, 0, 0, 0, 0, 0)
        }

        // ▼▼▼ 修正 ▼▼▼
        // 1. getAllProgressForModeでモード全体の進捗を取得
        // 2. その中から targetIds に含まれるものだけをフィルタリング
        val allProgressForMode = progressDao.getAllProgressForMode(mode)
        val progress = allProgressForMode.filter { it.wordId in targetIds }

        val progressedIds = progress.map { it.wordId }.toSet()

        val reviewCount = progress.count { it.nextDueAtSec <= nowSec }
        val newCount = targetIds.size - progressedIds.size

        // トロフィーの計算はフィルタリング済みの `progress` を使う
        val bronze = progress.count { it.level >= 2 }
        val silver = progress.count { it.level >= 3 }
        val gold = progress.count { it.level >= 4 }
        val crystal = progress.count { it.level >= 5 }
        val purple = progress.count { it.level >= 6 }

        return ModeStats(
            review = reviewCount,
            newCount = newCount,
            total = targetIds.size,
            bronze = bronze,
            silver = silver,
            gold = gold,
            crystal = crystal,
            purple = purple
        )
    }
}