package com.example.studylockapp.learning

import android.view.View
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.example.studylockapp.R
import com.example.studylockapp.databinding.LayoutReviewCardBinding

/**
 * レビューカードの表示（バインド）を担当するクラス。
 * synonyms / antonyms の表示ロジックを追加しました。
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
            
            // 「わからない」の場合は、ユーザー回答(左側)を非表示にする
            binding.includeWrong.root.visibility = if (model.isUnknownAnswer) View.GONE else View.VISIBLE
            
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
            binding.layoutReviewPhoneticRow.visibility = View.GONE
            binding.layoutListeningCompare.visibility = View.GONE

            // 通常の4択などで「わからない」の場合は、誤答表示ブロック全体を非表示にする
            val showWrong = model.showWrongResult && !model.isUnknownAnswer
            binding.layoutResultWrong.visibility = if (showWrong) View.VISIBLE else View.GONE

            binding.textReviewAnswerWrong.text = model.wrongAnswerText
            binding.textReviewAnswerCorrect.text = model.correctAnswerText
        }

        // 3. 惜しい不正解 (Synonym Hint)
        if (!model.synonymHintTitle.isNullOrEmpty()) {
            binding.layoutSynonymHint.visibility = View.VISIBLE
            binding.textSynonymHintTitle.text = model.synonymHintTitle
            binding.textSynonymHintBody.text = model.synonymHintBody
        } else {
            binding.layoutSynonymHint.visibility = View.GONE
        }

        // 4. 反対の意味 (Antonyms)
        if (model.antonyms.isNotEmpty()) {
            binding.layoutAntonymsSection.visibility = View.VISIBLE
            binding.containerAntonyms.removeAllViews()
            model.antonyms.forEach { antonym ->
                val tv = TextView(context).apply {
                    text = if (antonym.note.isNotEmpty()) "・${antonym.word} (${antonym.note})" else "・${antonym.word}"
                    textSize = 14f
                    setTextColor(ContextCompat.getColor(context, R.color.text_main))
                    setPadding(0, dpToPx(4, context), 0, dpToPx(4, context))
                }
                binding.containerAntonyms.addView(tv)
            }
        } else {
            binding.layoutAntonymsSection.visibility = View.GONE
        }

        // 5. 詳細 (例文)
        binding.textReviewSentence.text = model.sentence
        binding.textReviewSentenceJp.text = model.sentenceJp

        // 6. 再生ボタン制御
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

    private fun dpToPx(dp: Int, context: android.content.Context): Int {
        return (dp * context.resources.displayMetrics.density).toInt()
    }
}
