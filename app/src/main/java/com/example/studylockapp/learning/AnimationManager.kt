package com.example.studylockapp.learning

import android.content.res.ColorStateList
import android.view.View
import androidx.core.content.ContextCompat
import com.example.studylockapp.R
import com.example.studylockapp.databinding.ActivityLearningBinding

/**
 * 学習画面のアニメーションを一括管理するクラス
 */
class AnimationManager(private val binding: ActivityLearningBinding) {

    /**
     * 正解時の報酬（ポイント）アニメーションを表示
     */
    fun showReward(point: Int) {
        binding.textRewardPopup.apply {
            text = "+$point ⭐"
            
            alpha = 0f
            translationY = 0f
            scaleX = 0.8f // 少し小さめから
            scaleY = 0.8f
            
            animate()
                .alpha(1f)
                .translationY(-60f) // 少し高めに飛ばす
                .scaleX(1.2f) // 少し大きくして強調
                .scaleY(1.2f)
                .setDuration(300)
                .withEndAction {
                    animate()
                        .alpha(0f)
                        .translationY(-20f)
                        .scaleX(1.0f)
                        .scaleY(1.0f)
                        .setDuration(300)
                        .setStartDelay(600) // 余韻を長めに
                        .start()
                }
                .start()
        }
    }

    /**
     * 正解時のボタン演出
     */
    fun showCorrect(button: View) {
        button.backgroundTintList = ColorStateList.valueOf(
            ContextCompat.getColor(button.context, R.color.choice_correct)
        )

        button.animate()
            .scaleX(1.05f)
            .scaleY(1.05f)
            .setDuration(150)
            .withEndAction {
                button.animate()
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(150)
                    .start()
            }
            .start()
    }

    /**
     * 不正解時のボタン演出
     */
    fun showWrong(selected: View, correct: View) {
        selected.backgroundTintList = ColorStateList.valueOf(
            ContextCompat.getColor(selected.context, R.color.choice_wrong)
        )

        // 正解ボタンを強調（理解を助ける）
        correct.backgroundTintList = ColorStateList.valueOf(
            ContextCompat.getColor(correct.context, R.color.choice_correct)
        )
        correct.animate()
            .scaleX(1.08f)
            .scaleY(1.08f)
            .setDuration(200)
            .withEndAction {
                correct.animate()
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(200)
                    .start()
            }
            .start()

        shake(selected)
    }

    /**
     * 左右に小さく振るシェイクアニメーション
     */
    fun shake(view: View) {
        view.translationX = 0f
        view.animate()
            .translationX(-12f)
            .setDuration(50)
            .withEndAction {
                view.animate()
                    .translationX(12f)
                    .setDuration(50)
                    .withEndAction {
                        view.animate()
                            .translationX(-8f)
                            .setDuration(50)
                            .withEndAction {
                                view.animate()
                                    .translationX(8f)
                                    .setDuration(50)
                                    .withEndAction {
                                        view.animate()
                                            .translationX(0f)
                                            .setDuration(50)
                                            .start()
                                    }
                                    .start()
                            }
                            .start()
                    }
                    .start()
            }
            .start()
    }

    fun pressDown(view: View) {
        view.animate()
            .scaleX(0.95f)
            .scaleY(0.95f)
            .setDuration(80)
            .start()
    }

    fun release(view: View) {
        view.animate()
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(80)
            .start()
    }

    /**
     * 正解時の満足感を最大化するシーケンス
     */
    fun playCorrectSequence(button: View, point: Int, onEnd: () -> Unit) {
        showCorrect(button)

        button.postDelayed({
            showReward(point)
        }, 120) // 少しずらして開始

        button.postDelayed({
            onEnd()
        }, 850) // 余韻をしっかり持たせてから次へ
    }
}
