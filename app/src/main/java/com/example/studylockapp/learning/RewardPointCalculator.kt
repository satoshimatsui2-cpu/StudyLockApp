package com.example.studylockapp.learning

/**
 * 英検級の差分に基づいた獲得ポイント計算機
 */
object RewardPointCalculator {

    /**
     * 獲得ポイントを算出する。
     * @param basePoint モード別の基本ポイント (4, 8, 12, 16 など)
     * @param wordGrade 単語の持つ級ランク (1-7)
     * @param targetGrade ユーザーが目標とする級ランク (1-7)
     * @return 算出されたポイント (Int)
     */
    fun calculate(
        basePoint: Int,
        wordGrade: Int,
        targetGrade: Int
    ): Int {
        return when {
            // 目標以上の級を学習している場合は満額
            wordGrade >= targetGrade -> basePoint
            
            // 目標より1つ下の級（復習）の場合は半分
            wordGrade == targetGrade - 1 -> basePoint / 2
            
            // それ以下（2つ下〜）の場合は四分の一
            else -> basePoint / 4
        }
    }
}
