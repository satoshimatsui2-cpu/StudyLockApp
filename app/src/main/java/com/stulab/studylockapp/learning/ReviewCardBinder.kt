package com.stulab.studylockapp.learning

import android.view.View
import android.widget.TextView
import android.content.res.ColorStateList
import androidx.core.content.ContextCompat
import com.stulab.studylockapp.R
import com.stulab.studylockapp.databinding.LayoutReviewCardBinding
import com.stulab.studylockapp.databinding.ItemHearingReviewChoiceBinding
import android.view.LayoutInflater

/**
 * レビューカードの表示（バインド）を担当するクラス。
 */
object ReviewCardBinder {
    fun bind(
        binding: LayoutReviewCardBinding, 
        model: ReviewCardUiModel,
        onPlayUserAnswer: (String) -> Unit,
        onPlayCorrectAnswer: (String) -> Unit,
        onPlaySentence: (String) -> Unit,
        onFavoriteClick: () -> Unit
    ) {
        val context = binding.root.context
        
        // 1. 基本情報
        binding.chipReviewMode.text = model.modeChipText
        binding.chipReviewMode.setChipIconResource(model.modeChipIconRes)
        binding.textReviewQuestionText.text = model.questionText
        bindNote(binding.textReviewQuestionNote, model.questionNote)

        // 2. 排他表示制御
        // 旧来の「聞き比べエリア」は結果エリアに統合されたため、常に非表示
        binding.layoutListeningCompare.visibility = View.GONE
        binding.containerResults.visibility = View.VISIBLE
        binding.layoutReviewPhoneticRow.visibility = View.GONE

        val showWrong = model.showWrongResult && !model.isUnknownAnswer

        // 7. リスト形式のレビュー表示 (再生可能リスト または 英語→日本語)
        if (model.playableReviewChoices.isNotEmpty() || model.enToJpReviewChoices.isNotEmpty()) {
            binding.layoutResultWrong.visibility = View.GONE
            binding.layoutResultCorrect.visibility = View.GONE
            binding.layoutHearingChoicesContainer.visibility = View.VISIBLE
            binding.layoutHearingChoicesContainer.removeAllViews()
            
            // 再生可能リスト (LISTEN_EN, LISTEN_FILL_BLANK, FILL_BLANK 等)
            model.playableReviewChoices.forEach { choice ->
                renderListItem(binding, context, choice.englishText, choice.japaneseMeaning, choice.ttsText, choice.isCorrect, choice.isSelected, true, onPlayCorrectAnswer)
            }
            // 英語→日本語問題
            model.enToJpReviewChoices.forEach { choice ->
                renderListItem(binding, context, choice.japaneseText, choice.englishText, choice.ttsText, choice.isCorrect, choice.isSelected, choice.isPlayable, onPlayCorrectAnswer)
            }
        } else {
            binding.layoutHearingChoicesContainer.removeAllViews()
            binding.layoutHearingChoicesContainer.visibility = View.GONE
            
            // 正誤ブロックの表示制御を通常に戻す
            binding.layoutResultWrong.visibility = if (showWrong) View.VISIBLE else View.GONE
            binding.textReviewAnswerWrong.text = model.wrongAnswerText
            bindNote(binding.textReviewAnswerWrongNote, model.wrongAnswerNote)

            binding.layoutResultCorrect.visibility = View.VISIBLE
            binding.textReviewAnswerCorrect.text = model.correctAnswerText
            bindNote(binding.textReviewAnswerCorrectNote, model.correctAnswerNote)
        }

        // --- 結果エリアの音声再生制御 (QuizModeに基づく判定) ---

        // 正解エリア
        if (model.canPlayCorrectAnswer) {
            binding.iconPlayCorrect.visibility = View.VISIBLE
            binding.layoutResultCorrect.isClickable = true
            binding.layoutResultCorrect.setOnClickListener {
                onPlayCorrectAnswer(model.correctAnswerTtsText)
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
                onPlayUserAnswer(model.wrongAnswerTtsText)
            }
        } else {
            binding.iconPlayWrong.visibility = View.GONE
            binding.layoutResultWrong.setOnClickListener(null)
            binding.layoutResultWrong.isClickable = false
        }

        // 2.5 お気に入りボタン
        binding.buttonFavorite.apply {
            val (iconRes, tintColor, contentDesc) = if (model.isFavorite) {
                Triple(R.drawable.ic_round_stars_24, ContextCompat.getColor(context, R.color.mustard_accent), context.getString(R.string.cd_favorite_remove))
            } else {
                Triple(R.drawable.ic_round_star_border_24, ContextCompat.getColor(context, R.color.text_sub), context.getString(R.string.cd_favorite_add))
            }
            setImageResource(iconRes)
            imageTintList = ColorStateList.valueOf(tintColor)
            contentDescription = contentDesc
            isEnabled = !model.isFavoriteUpdating
            alpha = if (model.isFavoriteUpdating) 0.5f else 1.0f
            setOnClickListener { onFavoriteClick() }
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
        val canPlaySentence = model.playButtonsEnabled && model.sentence.isNotBlank()
        binding.buttonPlayReviewSentence.apply {
            visibility = if (model.sentence.isNotBlank()) View.VISIBLE else View.GONE
            isEnabled = canPlaySentence
            isClickable = canPlaySentence
            isFocusable = canPlaySentence
            alpha = if (canPlaySentence) 1.0f else 0.3f
            if (canPlaySentence) {
                setOnClickListener { onPlaySentence(model.sentence) }
                contentDescription = context.getString(R.string.action_play_example)
            } else {
                setOnClickListener(null)
                contentDescription = null
            }
        }

        // インライン再生ボタン (EN_TO_JP等)
        val canPlayInline = model.showAudioControls && model.playButtonsEnabled && model.questionTtsText.isNotBlank()
        binding.buttonPlayQuestionInline.apply {
            visibility = if (model.showAudioControls) View.VISIBLE else View.GONE
            isEnabled = canPlayInline
            isClickable = canPlayInline
            isFocusable = canPlayInline
            alpha = if (canPlayInline) 1.0f else 0.3f
            if (canPlayInline) {
                setOnClickListener { onPlayCorrectAnswer(model.questionTtsText) }
            } else {
                setOnClickListener(null)
            }
        }
    }

    private fun renderListItem(
        binding: LayoutReviewCardBinding,
        context: android.content.Context,
        primaryText: String,
        secondaryText: String?,
        ttsText: String?,
        isCorrect: Boolean,
        isSelected: Boolean,
        isPlayable: Boolean,
        onPlayAudio: (String) -> Unit
    ) {
        val itemView = LayoutInflater.from(context).inflate(R.layout.item_hearing_review_choice, binding.layoutHearingChoicesContainer, false)
        val itemBinding = ItemHearingReviewChoiceBinding.bind(itemView)
        
        itemBinding.textChoiceEnglish.text = primaryText
        
        val sec = secondaryText?.trim()
        if (sec.isNullOrEmpty()) {
            itemBinding.textChoiceJapanese.visibility = View.GONE
        } else {
            itemBinding.textChoiceJapanese.visibility = View.VISIBLE
            itemBinding.textChoiceJapanese.text = context.getString(R.string.label_meaning_bracket, sec)
        }
        
        // デザイン調整
        when {
            isCorrect -> {
                itemBinding.rootChoiceItem.setBackgroundResource(R.drawable.shape_result_card_correct)
                itemBinding.imageChoiceStatus.visibility = View.VISIBLE
                itemBinding.imageChoiceStatus.setImageResource(R.drawable.ic_check_circle_24)
                itemBinding.imageChoiceStatus.imageTintList = ColorStateList.valueOf(ContextCompat.getColor(context, R.color.choice_correct))
            }
            isSelected -> {
                itemBinding.rootChoiceItem.setBackgroundResource(R.drawable.shape_result_card_wrong)
                itemBinding.imageChoiceStatus.visibility = View.VISIBLE
                itemBinding.imageChoiceStatus.setImageResource(R.drawable.ic_close_24)
                itemBinding.imageChoiceStatus.imageTintList = ColorStateList.valueOf(ContextCompat.getColor(context, R.color.choice_wrong))
            }
            else -> {
                itemBinding.rootChoiceItem.setBackgroundResource(R.drawable.shape_hearing_choice_neutral)
                itemBinding.imageChoiceStatus.visibility = View.GONE
            }
        }
        
        // 再生ボタン制御
        val sanitizedTts = ttsText?.let { com.stulab.studylockapp.sanitizeForTts(it) }.orEmpty()
        val canPlay = isPlayable && sanitizedTts.isNotEmpty()

        itemBinding.buttonPlayChoice.visibility = if (canPlay) View.VISIBLE else View.GONE
        itemBinding.root.isClickable = canPlay
        itemBinding.root.isFocusable = canPlay

        if (canPlay) {
            val playAction = View.OnClickListener {
                onPlayAudio(sanitizedTts)
            }
            itemBinding.root.setOnClickListener(playAction)
            itemBinding.buttonPlayChoice.setOnClickListener(playAction)
            itemBinding.root.contentDescription = context.getString(R.string.action_play_word, sanitizedTts)
        } else {
            itemBinding.root.setOnClickListener(null)
            itemBinding.buttonPlayChoice.setOnClickListener(null)
            itemBinding.root.contentDescription = null
        }
        
        binding.layoutHearingChoicesContainer.addView(itemView)
    }

    private fun bindNote(textView: TextView, note: String?) {
        val trimmed = note?.trim()
        if (trimmed.isNullOrEmpty()) {
            textView.visibility = View.GONE
        } else {
            textView.visibility = View.VISIBLE
            textView.text = textView.context.getString(R.string.review_label_note_prefix, trimmed)
        }
    }

    private fun dpToPx(dp: Int, context: android.content.Context): Int {
        return (dp * context.resources.displayMetrics.density).toInt()
    }
}
