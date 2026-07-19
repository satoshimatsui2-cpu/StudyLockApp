package com.stulab.studylockapp.learning

import android.content.res.ColorStateList
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import androidx.core.content.ContextCompat
import com.stulab.studylockapp.R
import com.stulab.studylockapp.databinding.ActivityLearningBinding
import com.google.android.material.button.MaterialButton

/**
 * 学習画面のアニメーションを一括管理するクラス
 */
class AnimationManager(private val binding: ActivityLearningBinding) {

    companion object {
        const val DURATION_CORRECT = 1000L
        const val DURATION_WRONG = 1400L
    }

    fun playCorrectSequence(button: View?, point: Int, tierChanged: Boolean, tierLabel: String, onEnd: () -> Unit) {
        if (button != null) {
            showCorrect(button)
            button.postDelayed({
                showReward(point)
                if (tierChanged) playTierUpAnimation(tierLabel)
            }, 120)
        } else {
            showReward(point)
        }
        binding.rootLayout.postDelayed({ onEnd() }, DURATION_CORRECT)
    }

    fun playWrongSequence(selected: View?, correct: View?, onEnd: () -> Unit) {
        if (selected != null && correct != null) {
            showWrong(selected, correct)
        }
        binding.rootLayout.postDelayed({ onEnd() }, DURATION_WRONG)
    }

    /**
     * 習得度達成（基礎マスター / 長期マスター）の特別演出
     */
    fun playMasterCelebration(isLongTerm: Boolean) {
        val headerView = binding.layoutJourneyHeader.root
        headerView.animate()
            .scaleX(1.15f)
            .scaleY(1.15f)
            .setDuration(400)
            .setInterpolator(OvershootInterpolator())
            .withEndAction {
                headerView.animate()
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(300)
                    .start()
            }
            .start()
    }

    /**
     * 飛び級特別演出
     */
    fun playFlyingLevelUp(oldLevel: Int, newLevel: Int) {
        val badge = binding.layoutFlyingLevelup.cardFlyingLevelup
        val text = binding.layoutFlyingLevelup.textFlyingLevels
        
        text.text = "LV$oldLevel → LV$newLevel"
        
        badge.visibility = View.VISIBLE
        badge.alpha = 0f
        badge.scaleX = 0.8f
        badge.scaleY = 0.8f
        
        badge.animate()
            .alpha(1f)
            .scaleX(1.1f)
            .scaleY(1.1f)
            .setDuration(400)
            .setInterpolator(OvershootInterpolator())
            .withEndAction {
                badge.animate()
                    .alpha(0f)
                    .scaleX(1.3f)
                    .scaleY(1.3f)
                    .setStartDelay(800)
                    .setDuration(300)
                    .withEndAction { badge.visibility = View.GONE }
                    .start()
            }
            .start()
    }

    private fun showReward(point: Int) {
        binding.textRewardPopup.apply {
            // 表示文言を +10pt に統一
            text = "+${point}pt"
            alpha = 0f
            translationY = 0f
            visibility = View.VISIBLE
            animate()
                .alpha(1f)
                .translationY(-30f) // 移動量を -40f -> -30f に調整
                .setDuration(300)
                .setInterpolator(DecelerateInterpolator())
                .withEndAction {
                    animate()
                        .alpha(0f)
                        .translationY(-50f) // 最終到達を -60f -> -50f に調整
                        .setDuration(300)
                        .setStartDelay(400)
                        .withEndAction { visibility = View.GONE } // 終了後に隠す
                        .start()
                }
                .start()
        }
    }

    fun playTierUpAnimation(tierLabel: String) {
        val headerView = binding.layoutJourneyHeader.root
        headerView.animate()
            .scaleX(1.05f)
            .scaleY(1.05f)
            .setDuration(200)
            .withEndAction {
                headerView.animate().scaleX(1f).scaleY(1f).setDuration(200).start()
            }.start()
    }

    fun playModeToggleClick(view: View) {
        view.animate()
            .scaleX(0.96f)
            .scaleY(0.96f)
            .setDuration(100)
            .withEndAction {
                view.animate().scaleX(1f).scaleY(1f).setDuration(100).start()
            }.start()
    }

    private fun showCorrect(button: View) {
        if (button is MaterialButton) {
            button.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(button.context, R.color.choice_correct_bg))
            button.strokeColor = ColorStateList.valueOf(ContextCompat.getColor(button.context, R.color.choice_correct_stroke))
            button.setTextColor(ContextCompat.getColor(button.context, R.color.choice_correct_text))
        }
        button.animate().scaleX(1.05f).scaleY(1.05f).setDuration(150).withEndAction {
            button.animate().scaleX(1f).scaleY(1f).setDuration(150).start()
        }.start()
    }

    private fun showWrong(selected: View, correct: View) {
        if (selected is MaterialButton) {
            selected.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(selected.context, R.color.choice_wrong_bg))
            selected.strokeColor = ColorStateList.valueOf(ContextCompat.getColor(selected.context, R.color.choice_wrong_stroke))
            selected.setTextColor(ContextCompat.getColor(selected.context, R.color.choice_wrong_text))
        }
        if (correct is MaterialButton) {
            correct.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(correct.context, R.color.choice_correct_bg))
            correct.strokeColor = ColorStateList.valueOf(ContextCompat.getColor(correct.context, R.color.choice_correct_stroke))
            correct.setTextColor(ContextCompat.getColor(correct.context, R.color.choice_correct_text))
        }
        correct.animate().scaleX(1.08f).scaleY(1.08f).setDuration(200).withEndAction {
            correct.animate().scaleX(1f).scaleY(1f).setDuration(200).start()
        }.start()
        shake(selected)
    }

    private fun shake(view: View) {
        view.translationX = 0f
        view.animate().translationX(-12f).setDuration(50).withEndAction {
            view.animate().translationX(12f).setDuration(50).withEndAction {
                view.animate().translationX(0f).setDuration(50).start()
            }.start()
        }.start()
    }

    fun pressDown(view: View) { view.animate().scaleX(0.95f).scaleY(0.95f).setDuration(80).start() }
    fun release(view: View) { view.animate().scaleX(1f).scaleY(1f).setDuration(80).start() }

    /**
     * 目標達成時のド派手お祝い（簡易版：ヘッダーの連続拡大縮小）
     */
    fun playGoalGrandCelebration() {
        val root = binding.rootLayout
        // ここで本来は紙吹雪ビューなどを生成したい
        
        val headerView = binding.layoutJourneyHeader.root
        headerView.animate()
            .scaleX(1.2f)
            .scaleY(1.2f)
            .setDuration(500)
            .setInterpolator(OvershootInterpolator())
            .withEndAction {
                headerView.animate()
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(400)
                    .start()
            }
            .start()
    }
}
