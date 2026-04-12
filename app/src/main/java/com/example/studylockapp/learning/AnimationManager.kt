package com.example.studylockapp.learning

import android.content.res.ColorStateList
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.DecelerateInterpolator
import androidx.core.content.ContextCompat
import com.example.studylockapp.R
import com.example.studylockapp.databinding.ActivityLearningBinding

/**
 * 学習画面のアニメーションを一括管理するクラス
 */
class AnimationManager(private val binding: ActivityLearningBinding) {

    /**
     * 正解時の報酬（ポイント）アニメーション
     */
    fun showReward(point: Int) {
        binding.textRewardPopup.apply {
            text = "+$point ⭐"
            alpha = 0f
            translationY = 0f
            scaleX = 0.8f
            scaleY = 0.8f
            visibility = View.VISIBLE
            
            animate()
                .alpha(1f)
                .translationY(-100f)
                .scaleX(1.4f)
                .scaleY(1.4f)
                .setDuration(400)
                .setInterpolator(DecelerateInterpolator())
                .withEndAction {
                    animate()
                        .alpha(0f)
                        .translationY(-140f)
                        .scaleX(1.0f)
                        .scaleY(1.0f)
                        .setDuration(300)
                        .setStartDelay(500)
                        .start()
                }
                .start()
        }
    }

    /**
     * ランクアップ（基礎マスター昇格など）時の特別演出
     */
    fun playTierUpAnimation(tierLabel: String) {
        binding.cardQuestion.animate()
            .scaleX(1.05f)
            .scaleY(1.05f)
            .setDuration(200)
            .withEndAction {
                binding.cardQuestion.animate()
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(300)
                    .start()
            }
            .start()

        binding.chipMastery.animate()
            .scaleX(1.5f)
            .scaleY(1.5f)
            .setDuration(300)
            .setInterpolator(AccelerateDecelerateInterpolator())
            .withEndAction {
                binding.chipMastery.animate()
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(200)
                    .start()
            }
            .start()
    }

    fun showCorrect(button: View) {
        button.backgroundTintList = ColorStateList.valueOf(
            ContextCompat.getColor(button.context, R.color.choice_correct)
        )
        button.animate().scaleX(1.05f).scaleY(1.05f).setDuration(150).withEndAction {
            button.animate().scaleX(1f).scaleY(1f).setDuration(150).start()
        }.start()
    }

    fun showWrong(selected: View, correct: View) {
        selected.backgroundTintList = ColorStateList.valueOf(
            ContextCompat.getColor(selected.context, R.color.choice_wrong)
        )
        correct.backgroundTintList = ColorStateList.valueOf(
            ContextCompat.getColor(correct.context, R.color.choice_correct)
        )
        correct.animate().scaleX(1.08f).scaleY(1.08f).setDuration(200).withEndAction {
            correct.animate().scaleX(1f).scaleY(1f).setDuration(200).start()
        }.start()
        shake(selected)
    }

    fun shake(view: View) {
        view.translationX = 0f
        view.animate().translationX(-12f).setDuration(50).withEndAction {
            view.animate().translationX(12f).setDuration(50).withEndAction {
                view.animate().translationX(-8f).setDuration(50).withEndAction {
                    view.animate().translationX(8f).setDuration(50).withEndAction {
                        view.animate().translationX(0f).setDuration(50).start()
                    }.start()
                }.start()
            }.start()
        }.start()
    }

    fun pressDown(view: View) {
        view.animate().scaleX(0.95f).scaleY(0.95f).setDuration(80).start()
    }

    fun release(view: View) {
        view.animate().scaleX(1f).scaleY(1f).setDuration(80).start()
    }

    fun playCorrectSequence(button: View, point: Int, tierChanged: Boolean, tierLabel: String, onEnd: () -> Unit) {
        showCorrect(button)
        
        button.postDelayed({
            showReward(point)
            if (tierChanged) {
                playTierUpAnimation(tierLabel)
            }
        }, 120)

        val delay = if (tierChanged) 1500L else 850L
        button.postDelayed({
            onEnd()
        }, delay)
    }
}
