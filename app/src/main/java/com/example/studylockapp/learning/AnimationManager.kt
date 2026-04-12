package com.example.studylockapp.learning

import android.content.res.ColorStateList
import android.view.View
import android.view.animation.DecelerateInterpolator
import androidx.core.content.ContextCompat
import com.example.studylockapp.R
import com.example.studylockapp.databinding.ActivityLearningBinding

/**
 * 学習画面のアニメーションを一括管理するクラス (演出時間集約版)
 */
class AnimationManager(private val binding: ActivityLearningBinding) {

    companion object {
        const val DURATION_CORRECT = 1000L // 正解演出時間
        const val DURATION_WRONG = 1400L   // 不正解演出時間
    }

    /**
     * 正解シーケンスの実行
     * 演出終了後、onEnd (レビュー開始) を呼び出す
     */
    fun playCorrectSequence(button: View, point: Int, tierChanged: Boolean, tierLabel: String, onEnd: () -> Unit) {
        showCorrect(button)
        button.postDelayed({
            showReward(point)
            if (tierChanged) playTierUpAnimation(tierLabel)
        }, 120)

        // 指定時間後にレビュー開始へ (フォールバック)
        button.postDelayed({ onEnd() }, DURATION_CORRECT)
    }

    /**
     * 不正解シーケンスの実行
     */
    fun playWrongSequence(selected: View?, correct: View?, onEnd: () -> Unit) {
        if (selected != null && correct != null) {
            showWrong(selected, correct)
        }
        
        // ボタンが取れても取れなくても、指定時間後にレビュー開始へ
        binding.rootLayout.postDelayed({ onEnd() }, DURATION_WRONG)
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

    fun playTierUpAnimation(tierLabel: String) {
        binding.cardMasteryJourney.animate()
            .scaleX(1.05f)
            .scaleY(1.05f)
            .setDuration(200)
            .withEndAction {
                binding.cardMasteryJourney.animate().scaleX(1f).scaleY(1f).setDuration(200).start()
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
