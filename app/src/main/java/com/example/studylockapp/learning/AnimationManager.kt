package com.example.studylockapp.learning

import android.content.res.ColorStateList
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import androidx.core.content.ContextCompat
import com.example.studylockapp.R
import com.example.studylockapp.databinding.ActivityLearningBinding

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
            
        // 注意: ゲージのアニメーションは Compose 側で自動的に行われるため、
        // ここでの古いプロパティ (masteryProgressRail) への参照は削除しました。
    }

    private fun showReward(point: Int) {
        binding.textRewardPopup.apply {
            text = "+$point PT"
            alpha = 0f
            translationY = 0f
            visibility = View.VISIBLE
            animate()
                .alpha(1f)
                .translationY(-40f)
                .setDuration(300)
                .setInterpolator(DecelerateInterpolator())
                .withEndAction {
                    animate()
                        .alpha(0f)
                        .translationY(-60f)
                        .setDuration(300)
                        .setStartDelay(400)
                        .start()
                }
                .start()
        }
    }

    /**
     * ランクアップ演出
     * layout_journey_header のルート View をアニメーションさせます。
     */
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
        button.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(button.context, R.color.choice_correct))
        button.animate().scaleX(1.05f).scaleY(1.05f).setDuration(150).withEndAction {
            button.animate().scaleX(1f).scaleY(1f).setDuration(150).start()
        }.start()
    }

    private fun showWrong(selected: View, correct: View) {
        selected.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(selected.context, R.color.choice_wrong))
        correct.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(correct.context, R.color.choice_correct))
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
}
