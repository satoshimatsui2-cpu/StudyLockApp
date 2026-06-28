package com.example.studylockapp.learning

import android.view.View
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.example.studylockapp.R
import com.example.studylockapp.databinding.LayoutReviewCardBinding

/**
 * レビューカードの表示（バインド）を担当するクラス。
 */
object ReviewCardBinder {
    fun bind(
        binding: LayoutReviewCardBinding, 
        model: ReviewCardUiModel,
        onPlayUserAnswer: (String) -> Unit,
        onPlayCorrectAnswer: (String) -> Unit
    ) {
        val context = binding.root.context
        
        // 1. 基本情報
        binding.chipReviewMode.text = model.modeChipText
        binding.chipReviewMode.setChipIconResource(model.modeChipIconRes)
        binding.textReviewQuestionText.text = model.questionText

        // 2. 排他表示制御
        // 旧来の「聞き比べエリア」は結果エリアに統合されたため、常に非表示
        binding.layoutListeningCompare.visibility = View.GONE
        binding.containerResults.visibility = View.VISIBLE
        binding.layoutReviewPhoneticRow.visibility = View.GONE

        // 正誤ブロックの表示制御
        val showWrong = model.showWrongResult && !model.isUnknownAnswer
        binding.layoutResultWrong.visibility = if (showWrong) View.VISIBLE else View.GONE
        binding.textReviewAnswerWrong.text = model.wrongAnswerText
        binding.textReviewAnswerCorrect.text = model.correctAnswerText

        // --- 結果エリアの音声再生制御 (QuizModeに基づく判定) ---

        // 正解エリア
        if (model.canPlayCorrectAnswer) {
            binding.iconPlayCorrect.visibility = View.VISIBLE
            binding.layoutResultCorrect.isClickable = true
            binding.layoutResultCorrect.setOnClickListener {
                onPlayCorrectAnswer(model.correctAnswerText)
            }
        } else {
            binding.iconPlayCorrect.visibility = View.GONE
            binding.layoutResultCorrect.setOnClickListener(null)
            binding.layoutResultCorrect.isClickable = false
        }

        // 不正解エリア
        if (model.canPlayWrongAnswer && showWrong) {
            binding.iconPlayWrong.visibility = View.VISIBLE
            binding.layoutResultWrong.isClickable = true
            binding.layoutResultWrong.setOnClickListener {
                onPlayUserAnswer(model.wrongAnswerText)
            }
        } else {
            binding.iconPlayWrong.visibility = View.GONE
            binding.layoutResultWrong.setOnClickListener(null)
            binding.layoutResultWrong.isClickable = false
        }

        // 結果エリア外の単語音声コントロール (EN_TO_JP等、結果エリアで再生できない場合に表示)
        binding.buttonPlayQuestionInline.visibility = if (model.showAudioControls) View.VISIBLE else View.GONE
        if (model.showAudioControls) {
            binding.buttonPlayQuestionInline.setOnClickListener {
                onPlayCorrectAnswer(model.questionText) // 問題文(英語)を再生
            }
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

        // 6. 再生ボタン制御 (例文再生など)
        val alpha = if (model.playButtonsEnabled) 1.0f else 0.3f
        listOf(
            binding.buttonPlayQuestionInline,
            binding.buttonPlayReviewSentence
        ).forEach {
            it.isEnabled = model.playButtonsEnabled
            it.alpha = alpha
        }
    }

    private fun dpToPx(dp: Int, context: android.content.Context): Int {
        return (dp * context.resources.displayMetrics.density).toInt()
    }
}
