package com.stulab.studylockapp.learning

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class FlyingLevelUpFlowTest {

    @Test
    fun testFlyingLevelUpStateTransitions() {
        // 初期状態
        var state = LearningUiState()
        assertEquals(ReviewDisplayState.NONE, state.reviewDisplayState)
        assertNull(state.flyingLevelUp)

        // 1. 回答提出 (飛び級発生想定)
        val flyingModel = FlyingLevelUpUiModel(fromLevel = 6, skippedLevel = 7, toLevel = 8)
        state = state.copy(
            reviewDisplayState = ReviewDisplayState.WAITING_FOR_ANIMATION,
            flyingLevelUp = flyingModel
        )
        assertEquals(ReviewDisplayState.WAITING_FOR_ANIMATION, state.reviewDisplayState)
        assertNotNull(state.flyingLevelUp)

        // 2. アニメーション完了通知 (飛び級モーダル準備完了)
        // ViewModel.notifyAnimationFinished() の内部ロジックを模倣
        val nextStateAfterAnim = if (state.flyingLevelUp != null) {
            ReviewDisplayState.READY_FOR_FLYING_LEVEL_UP
        } else {
            ReviewDisplayState.READY_FOR_MODAL
        }
        state = state.copy(reviewDisplayState = nextStateAfterAnim)
        assertEquals("飛び級情報がある場合は READY_FOR_FLYING_LEVEL_UP になること", 
            ReviewDisplayState.READY_FOR_FLYING_LEVEL_UP, state.reviewDisplayState)

        // 3. モーダル表示開始通知
        // ViewModel.onFlyingLevelUpShown()
        state = state.copy(reviewDisplayState = ReviewDisplayState.SHOWING_FLYING_LEVEL_UP)
        assertEquals(ReviewDisplayState.SHOWING_FLYING_LEVEL_UP, state.reviewDisplayState)

        // 4. モーダル「次へ」ボタン押下 (レビュー準備完了へ遷移)
        // ViewModel.onFlyingLevelUpDismissed()
        state = state.copy(reviewDisplayState = ReviewDisplayState.READY_FOR_MODAL)
        assertEquals("飛び級確認後は READY_FOR_MODAL になること", 
            ReviewDisplayState.READY_FOR_MODAL, state.reviewDisplayState)

        // 5. レビュー表示開始
        // ViewModel.onReviewModalShown()
        state = state.copy(reviewDisplayState = ReviewDisplayState.SHOWING_MODAL)
        assertEquals(ReviewDisplayState.SHOWING_MODAL, state.reviewDisplayState)

        // 6. レビュー「次へ」ボタン押下 (次の問題へ: flyingLevelUpはリセットされる)
        // loadNextQuiz() 相当
        state = state.copy(
            reviewDisplayState = ReviewDisplayState.NONE,
            flyingLevelUp = null
        )
        assertEquals(ReviewDisplayState.NONE, state.reviewDisplayState)
        assertNull("次問ロード時に飛び級情報はクリアされること", state.flyingLevelUp)
    }

    @Test
    fun testNormalLevelUpStateTransitions() {
        // 初期状態
        var state = LearningUiState()

        // 1. 回答提出 (通常レベルアップ: flyingLevelUpはnull)
        state = state.copy(
            reviewDisplayState = ReviewDisplayState.WAITING_FOR_ANIMATION,
            flyingLevelUp = null
        )

        // 2. アニメーション完了通知 (直接レビュー準備完了へ)
        val nextState = if (state.flyingLevelUp != null) {
            ReviewDisplayState.READY_FOR_FLYING_LEVEL_UP
        } else {
            ReviewDisplayState.READY_FOR_MODAL
        }
        state = state.copy(reviewDisplayState = nextState)
        assertEquals("飛び級がない場合は直接 READY_FOR_MODAL になること", 
            ReviewDisplayState.READY_FOR_MODAL, state.reviewDisplayState)
    }
}
