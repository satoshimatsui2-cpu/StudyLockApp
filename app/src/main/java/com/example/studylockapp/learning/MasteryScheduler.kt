package com.example.studylockapp.learning

import com.example.studylockapp.data.db.WordMasteryEntity
import java.util.concurrent.TimeUnit

/**
 * 習得レベルと称号の定義
 */
enum class MasteryTier(val label: String) {
    LEARNING("学習中"),
    BASIC_MASTER("基礎マスター"),
    LONG_TERM_MASTER("長期マスター")
}

/**
 * 単語の習得ロジックを管理するクラス
 */
object MasteryScheduler {

    private const val SKIP_LEVEL_INTERVAL_MS = 60 * 60 * 1000L // 飛び級を許可する最小間隔 (1時間)

    fun getTier(level: Int): MasteryTier {
        return when {
            level >= 10 -> MasteryTier.LONG_TERM_MASTER
            level >= 5 -> MasteryTier.BASIC_MASTER
            else -> MasteryTier.LEARNING
        }
    }

    /**
     * 正解時の状態更新
     */
    fun onCorrect(state: WordMasteryEntity, isAudioRestricted: Boolean) {
        val now = System.currentTimeMillis()
        val currentMode = QuizMode.valueOf(state.scheduledMode)
        val wasAudioRequired = currentMode == QuizMode.LISTEN_EN

        // 統計更新
        state.challengeCount++
        state.successCount++
        state.currentStreak++
        if (state.currentStreak > state.bestStreak) state.bestStreak = state.currentStreak

        // モード別統計
        when (currentMode) {
            QuizMode.EN_TO_JP -> state.enToJpCorrects++
            QuizMode.JP_TO_EN -> state.jpToEnCorrects++
            QuizMode.LISTEN_EN -> if (!isAudioRestricted) state.listenCorrects++
            else -> {}
        }

        // 飛び級判定 (抑制ロジック)
        val canSkip = state.currentStreak >= 3 && 
                      state.challengeCount >= 5 && 
                      (now - state.lastCorrectTime) >= SKIP_LEVEL_INTERVAL_MS
        
        val bonusLevel = if (canSkip) 1 else 0
        val nextLevel = (state.level + 1 + bonusLevel).coerceAtMost(10)
        
        state.lastCorrectTime = now

        // 音声制限時の特別扱い（借りを作る）
        if (wasAudioRequired && isAudioRestricted) {
            state.pendingListenReview = true
            state.deferredListenCount++
        } else if (wasAudioRequired) {
            state.pendingListenReview = false
        }

        applyTransition(state, nextLevel, isCorrect = true)
        updateMasteryStatus(state)
    }

    /**
     * 不正解時の状態更新
     */
    fun onWrong(state: WordMasteryEntity) {
        state.challengeCount++
        state.failureCount++
        state.currentStreak = 0

        // レベルダウン
        val nextLevel = when (state.level) {
            10 -> 7
            9 -> 7
            else -> (state.level - 1).coerceAtLeast(0)
        }

        applyTransition(state, nextLevel, isCorrect = false)
        updateMasteryStatus(state)
    }

    private fun applyTransition(state: WordMasteryEntity, nextLevel: Int, isCorrect: Boolean) {
        state.level = nextLevel
        val now = System.currentTimeMillis()

        val (intervalMillis, nextMode) = if (isCorrect) {
            getSuccessTransition(nextLevel)
        } else {
            getFailureTransition(nextLevel)
        }

        state.nextReviewTime = now + intervalMillis
        state.scheduledMode = nextMode.name
    }

    private fun getSuccessTransition(level: Int): Pair<Long, QuizMode> {
        return when (level) {
            1 -> TimeUnit.MINUTES.toMillis(10) to QuizMode.JP_TO_EN
            2 -> TimeUnit.MINUTES.toMillis(30) to QuizMode.LISTEN_EN
            3 -> TimeUnit.DAYS.toMillis(1) to QuizMode.JP_TO_EN
            4 -> TimeUnit.DAYS.toMillis(3) to QuizMode.LISTEN_EN
            5 -> TimeUnit.DAYS.toMillis(7) to QuizMode.JP_TO_EN
            6 -> TimeUnit.DAYS.toMillis(14) to QuizMode.LISTEN_EN
            7 -> TimeUnit.DAYS.toMillis(30) to QuizMode.JP_TO_EN
            8 -> TimeUnit.DAYS.toMillis(45) to QuizMode.LISTEN_EN
            9 -> TimeUnit.DAYS.toMillis(60) to QuizMode.JP_TO_EN
            10 -> TimeUnit.DAYS.toMillis(90) to QuizMode.LISTEN_EN
            else -> TimeUnit.DAYS.toMillis(120) to QuizMode.JP_TO_EN
        }
    }

    private fun getFailureTransition(level: Int): Pair<Long, QuizMode> {
        return TimeUnit.MINUTES.toMillis(10) to QuizMode.EN_TO_JP
    }

    fun updateMasteryStatus(state: WordMasteryEntity) {
        // 称号維持ルール
        if (!state.isBasicMastered) {
            state.isBasicMastered = state.level >= 5 &&
                    state.enToJpCorrects >= 1 &&
                    state.jpToEnCorrects >= 2 &&
                    state.listenCorrects >= 1
        }

        if (!state.isLongTermMastered) {
            state.isLongTermMastered = state.level >= 10 &&
                    state.enToJpCorrects >= 1 &&
                    state.jpToEnCorrects >= 3 &&
                    state.listenCorrects >= 3
        }
    }
}
