package com.example.studylockapp.learning

import android.view.View
import androidx.core.content.ContextCompat
import com.example.studylockapp.R
import com.example.studylockapp.databinding.LayoutReviewCardBinding

/**
 * レビューカードの表示（バインド）を担当するクラス。
 * 新スキーマに合わせて発音記号(phonetic)の表示ロジックを完全に削除しました。
 */
object ReviewCardBinder {
    fun bind(binding: LayoutReviewCardBinding, model: ReviewCardUiModel) {
        val context = binding.root.context
        
        // 1. 基本情報
        binding.chipReviewMode.text = model.modeChipText
        binding.chipReviewMode.setChipIconResource(model.modeChipIconRes)
        binding.textReviewQuestionText.text = model.questionText

        // 2. 排他表示制御 (リスニング不正解 vs 通常)
        if (model.showListeningCompare) {
            binding.containerResults.visibility = View.GONE
            binding.layoutReviewPhoneticRow.visibility = View.GONE
            binding.layoutListeningCompare.visibility = View.VISIBLE
            
            // 聞き比べ表示（発音記号なし、単語のみ）
            binding.includeWrong.apply {
                labelCompare.text = context.getString(R.string.review_label_your_answer)
                labelCompare.setTextColor(ContextCompat.getColor(context, R.color.choice_wrong))
                textWord.text = model.wrongAnswerText
                textPhonetic.visibility = View.GONE
            }
            binding.includeCorrect.apply {
                labelCompare.text = context.getString(R.string.review_label_correct_answer)
                labelCompare.setTextColor(ContextCompat.getColor(context, R.color.choice_correct))
                textWord.text = model.correctAnswerText
                textPhonetic.visibility = View.GONE
            }
        } else {
            binding.containerResults.visibility = View.VISIBLE
            binding.layoutReviewPhoneticRow.visibility = View.GONE // 新スキーマに phonetic がないため常時非表示
            binding.layoutListeningCompare.visibility = View.GONE

            // 通常の結果表示
            binding.layoutResultWrong.visibility = if (model.showWrongResult) View.VISIBLE else View.GONE
            binding.textReviewAnswerWrong.text = model.wrongAnswerText
            binding.textReviewAnswerCorrect.text = model.correctAnswerText
        }

        // 3. 詳細 (例文のみ)
        binding.textReviewSentence.text = model.sentence
        binding.textReviewSentenceJp.text = model.sentenceJp

        // 4. 再生ボタン制御
        val alpha = if (model.playButtonsEnabled) 1.0f else 0.3f
        listOf(
            binding.buttonPlayReviewWord,
            binding.buttonPlayReviewSentence,
            binding.includeWrong.buttonPlay,
            binding.includeCorrect.buttonPlay
        ).forEach {
            it.isEnabled = model.playButtonsEnabled
            it.alpha = alpha
        }
    }
}
