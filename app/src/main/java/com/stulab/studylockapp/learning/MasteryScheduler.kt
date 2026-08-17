package com.stulab.studylockapp.learning

import android.util.Log
import com.stulab.studylockapp.data.db.WordMasteryEntity
import java.util.Calendar
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

    /**
     * 習得レベル更新の結果を保持するデータクラス
     */
    data class MasteryUpdateResult(
        val oldLevel: Int,
        val newLevel: Int,
        val bonusLevel: Int,
        val isFlyingLevelUp: Boolean,
        val skippedLevel: Int?
    )

    fun getTier(state: WordMasteryEntity): MasteryTier {
        return when {
            isLongTermMasteredNow(state) -> MasteryTier.LONG_TERM_MASTER
            isBasicMasteredNow(state) -> MasteryTier.BASIC_MASTER
            else -> MasteryTier.LEARNING
        }
    }

    /**
     * 基礎マスターの条件判定
     */
    fun isBasicMasteredNow(state: WordMasteryEntity): Boolean {
        return state.level >= 5 &&
                state.enToJpCorrects >= 1 &&
                state.jpToEnCorrects >= 2 &&
                state.listenCorrects >= 1
    }

    /**
     * 長期マスターの条件判定
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
    fun onCorrect(
        state: WordMasteryEntity, 
        actualMode: QuizMode, 
        isAudioRestricted: Boolean,
        timingSettings: ReviewTimingSettings
    ): MasteryUpdateResult {
        val oldLevel = state.level
        val now = System.currentTimeMillis()
        state.challengeCount++
        state.successCount++
        state.currentStreak++
        if (state.currentStreak > state.bestStreak) state.bestStreak = state.currentStreak

        // モード別統計 (並び替えや類義語などは JP_TO_EN 系統に集計)
        when (actualMode) {
            QuizMode.EN_TO_JP -> {
                state.enToJpAttempts++
                state.enToJpCorrects++
            }
            QuizMode.JP_TO_EN, QuizMode.FILL_BLANK, QuizMode.SENTENCE_SORT, QuizMode.SYNONYM_PICK, QuizMode.ANTONYM_PICK -> {
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
                (now - state.lastCorrectTime) >= SKIP_LEVEL_INTERVAL_MS &&
                (state.level == 6 || state.level == 8) // LV6またはLV8の時のみ飛び級を許可

        val bonusLevel = if (canSkip) 1 else 0
        val nextLevel = (state.level + 1 + bonusLevel).coerceAtMost(10)
        state.lastCorrectTime = now

        applyTransition(state, nextLevel, isCorrect = true, timingSettings = timingSettings)
        updateMasteryStatus(state)

        return MasteryUpdateResult(
            oldLevel = oldLevel,
            newLevel = nextLevel,
            bonusLevel = bonusLevel,
            isFlyingLevelUp = bonusLevel > 0,
            skippedLevel = if (bonusLevel > 0) oldLevel + 1 else null
        )
    }

    /**
     * 不正解時の状態更新
     */
    fun onWrong(
        state: WordMasteryEntity, 
        actualMode: QuizMode,
        timingSettings: ReviewTimingSettings,
        isUnknown: Boolean = false
    ) {
        state.challengeCount++
        state.failureCount++
        state.currentStreak = 0

        when (actualMode) {
            QuizMode.EN_TO_JP -> state.enToJpAttempts++
            QuizMode.JP_TO_EN, QuizMode.FILL_BLANK, QuizMode.SENTENCE_SORT, QuizMode.SYNONYM_PICK, QuizMode.ANTONYM_PICK -> state.jpToEnAttempts++
            QuizMode.LISTEN_EN, QuizMode.LISTEN_FILL_BLANK -> state.listenAttempts++
            else -> {}
        }

        val nextLevel = when (state.level) {
            10, 9 -> 7
            else -> (state.level - 1).coerceAtLeast(0)
        }

        applyTransition(state, nextLevel, isCorrect = false, timingSettings = timingSettings, isUnknown = isUnknown)
        updateMasteryStatus(state)
    }

    private fun applyTransition(
        state: WordMasteryEntity, 
        nextLevel: Int, 
        isCorrect: Boolean,
        timingSettings: ReviewTimingSettings,
        isUnknown: Boolean = false
    ) {
        state.level = nextLevel
        val now = System.currentTimeMillis()

        val (baseIntervalMillis, nextMode) = if (isCorrect) {
            getSuccessTransition(nextLevel, timingSettings)
        } else {
            getFailureTransition(nextLevel, timingSettings, isUnknown)
        }

        // インターバルが1日以上（86400000ms以上）の場合は、日付を跨ぐので0時基準に調整する
        val finalReviewTime = if (baseIntervalMillis >= TimeUnit.DAYS.toMillis(1)) {
            val days = (baseIntervalMillis / TimeUnit.DAYS.toMillis(1)).toInt()
            adjustToStartOfDay(now, days)
        } else {
            now + baseIntervalMillis
        }

        state.nextReviewTime = finalReviewTime
        state.scheduledMode = nextMode.name
    }

    /**
     * 指定した日数の後の 00:00:00.000 のミリ秒を取得します。
     */
    private fun adjustToStartOfDay(timeMillis: Long, daysOffset: Int): Long {
        val cal = Calendar.getInstance()
        cal.timeInMillis = timeMillis
        cal.add(Calendar.DAY_OF_YEAR, daysOffset)
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    /**
     * 指定された LV1〜LV10 到達時の次回予約モード
     */
    private fun getSuccessTransition(level: Int, settings: ReviewTimingSettings): Pair<Long, QuizMode> {
        return when (level) {
            1 -> settings.correctSameDayDelayMillis to QuizMode.JP_TO_EN // LV0正解時は設定値を反映
            2 -> TimeUnit.DAYS.toMillis(1) to QuizMode.LISTEN_EN
            3 -> TimeUnit.DAYS.toMillis(2) to QuizMode.FILL_BLANK
            4 -> TimeUnit.DAYS.toMillis(3) to QuizMode.SENTENCE_SORT
            5 -> TimeUnit.DAYS.toMillis(7) to QuizMode.SYNONYM_PICK
            6 -> TimeUnit.DAYS.toMillis(14) to QuizMode.LISTEN_FILL_BLANK
            7 -> TimeUnit.DAYS.toMillis(30) to QuizMode.LISTEN_EN
            8 -> TimeUnit.DAYS.toMillis(45) to QuizMode.ANTONYM_PICK
            9 -> TimeUnit.DAYS.toMillis(60) to QuizMode.LISTEN_FILL_BLANK
            10 -> TimeUnit.DAYS.toMillis(90) to QuizMode.SENTENCE_SORT
            else -> TimeUnit.DAYS.toMillis(120) to QuizMode.LISTEN_FILL_BLANK
        }
    }

    private fun getFailureTransition(
        level: Int, 
        settings: ReviewTimingSettings, 
        isUnknown: Boolean
    ): Pair<Long, QuizMode> {
        // 不正解時は常に当日再出題。タイミングのみ設定値に従う。
        val delay = if (isUnknown) settings.unknownSameDayDelayMillis else settings.wrongSameDayDelayMillis
        return delay to QuizMode.EN_TO_JP
    }

    fun updateMasteryStatus(state: WordMasteryEntity) {
        state.isBasicMastered = isBasicMasteredNow(state)
        state.isLongTermMastered = isLongTermMasteredNow(state)
    }
}
