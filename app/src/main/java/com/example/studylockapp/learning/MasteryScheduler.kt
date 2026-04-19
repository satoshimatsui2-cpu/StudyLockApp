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

    private const val SKIP_LEVEL_INTERVAL_MS = 60 * 60 * 1000L

    fun getTier(state: WordMasteryEntity): MasteryTier {
        return when {
            isLongTermMasteredNow(state) -> MasteryTier.LONG_TERM_MASTER
            isBasicMasteredNow(state) -> MasteryTier.BASIC_MASTER
            else -> MasteryTier.LEARNING
        }
    }

    /**
     * 基礎マスターの条件判定 (既存ロジック維持)
     * jpToEnCorrects は 2 回必要。
     */
    fun isBasicMasteredNow(state: WordMasteryEntity): Boolean {
        return state.level >= 5 &&
                state.enToJpCorrects >= 1 &&
                state.jpToEnCorrects >= 2 &&
                state.listenCorrects >= 1
    }

    /**
     * 長期マスターの条件判定 (既存ロジック維持)
     */
    fun isLongTermMasteredNow(state: WordMasteryEntity): Boolean {
        return state.level >= 10 &&
                state.enToJpCorrects >= 1 &&
                state.jpToEnCorrects >= 3 &&
                state.listenCorrects >= 3
    }

    /**
     * 正解時の状態更新
     */
    fun onCorrect(state: WordMasteryEntity, actualMode: QuizMode, isAudioRestricted: Boolean) {
        val now = System.currentTimeMillis()
        state.challengeCount++
        state.successCount++
        state.currentStreak++
        if (state.currentStreak > state.bestStreak) state.bestStreak = state.currentStreak

        // モード別統計 (FILL_BLANK は JP_TO_EN の統計に含める, LISTEN_FILL_BLANK は LISTEN_EN に含める)
        when (actualMode) {
            QuizMode.EN_TO_JP -> {
                state.enToJpAttempts++
                state.enToJpCorrects++
            }
            QuizMode.JP_TO_EN, QuizMode.FILL_BLANK -> {
                state.jpToEnAttempts++
                state.jpToEnCorrects++
            }
            QuizMode.LISTEN_EN, QuizMode.LISTEN_FILL_BLANK -> {
                state.listenAttempts++
                if (!isAudioRestricted) state.listenCorrects++
            }
            else -> {}
        }

        if (actualMode == QuizMode.LISTEN_EN && !isAudioRestricted) {
            state.pendingListenReview = false
        } else if (QuizMode.valueOf(state.scheduledMode) == QuizMode.LISTEN_EN && isAudioRestricted) {
            state.pendingListenReview = true
        }

        val canSkip = state.currentStreak >= 3 && 
                      state.challengeCount >= 5 && 
                      (now - state.lastCorrectTime) >= SKIP_LEVEL_INTERVAL_MS
        
        val bonusLevel = if (canSkip) 1 else 0
        val nextLevel = (state.level + 1 + bonusLevel).coerceAtMost(10)
        state.lastCorrectTime = now

        applyTransition(state, nextLevel, isCorrect = true)
        updateMasteryStatus(state)
    }

    /**
     * 不正解時の状態更新
     */
    fun onWrong(state: WordMasteryEntity, actualMode: QuizMode) {
        state.challengeCount++
        state.failureCount++
        state.currentStreak = 0

        when (actualMode) {
            QuizMode.EN_TO_JP -> state.enToJpAttempts++
            QuizMode.JP_TO_EN, QuizMode.FILL_BLANK -> state.jpToEnAttempts++
            QuizMode.LISTEN_EN, QuizMode.LISTEN_FILL_BLANK -> state.listenAttempts++
            else -> {}
        }

        val nextLevel = when (state.level) {
            10, 9 -> 7
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

    /**
     * 単語の習熟度(レベル)に合わせた学習フロー。
     * 段階的に負荷を上げ、想起と文脈理解を繰り返します。
     */
    private fun getSuccessTransition(level: Int): Pair<Long, QuizMode> {
        return when (level) {
            1 -> TimeUnit.MINUTES.toMillis(1) to QuizMode.LISTEN_FILL_BLANK
            2 -> TimeUnit.DAYS.toMillis(1) to QuizMode.LISTEN_FILL_BLANK
            3 -> TimeUnit.DAYS.toMillis(2) to QuizMode.FILL_BLANK 
            4 -> TimeUnit.DAYS.toMillis(3) to QuizMode.JP_TO_EN
            5 -> TimeUnit.DAYS.toMillis(7) to QuizMode.LISTEN_FILL_BLANK // L5で新モード
            6 -> TimeUnit.DAYS.toMillis(14) to QuizMode.LISTEN_EN
            7 -> TimeUnit.DAYS.toMillis(30) to QuizMode.FILL_BLANK 
            8 -> TimeUnit.DAYS.toMillis(45) to QuizMode.LISTEN_FILL_BLANK // L8で新モード
            9 -> TimeUnit.DAYS.toMillis(60) to QuizMode.JP_TO_EN
            10 -> TimeUnit.DAYS.toMillis(90) to QuizMode.LISTEN_EN
            else -> TimeUnit.DAYS.toMillis(120) to QuizMode.JP_TO_EN
        }
    }

    private fun getFailureTransition(level: Int): Pair<Long, QuizMode> {
        return TimeUnit.MINUTES.toMillis(10) to QuizMode.EN_TO_JP
    }

    fun updateMasteryStatus(state: WordMasteryEntity) {
        state.isBasicMastered = isBasicMasteredNow(state)
        state.isLongTermMastered = isLongTermMasteredNow(state)
    }
}
