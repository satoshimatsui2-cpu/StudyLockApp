package com.example.studylockapp.learning

import android.view.LayoutInflater
import android.view.View
import android.widget.TextView
import com.example.studylockapp.R
import com.example.studylockapp.LearningActivity
import com.example.studylockapp.GradeLabelFormatter

class SortRenderer : QuizRenderer {

    private var currentQuiz: QuizData? = null
    private val selectedTokens = mutableListOf<String>()
    private val availableTokens = mutableListOf<String>()

    override fun render(ui: QuizUiProvider, quiz: QuizData) {
        if (ui !is LearningActivity) return
        val binding = ui.exposeBinding()
        
        currentQuiz = quiz
        selectedTokens.clear()
        availableTokens.clear()
        availableTokens.addAll(quiz.sortTokens ?: emptyList())

        // UIの初期化
        binding.textQuestionTitle.text = "正しい英文の順番に並べてください。"
        binding.textQuestionBody.text = quiz.question // 日本語文
        
        // Gradeバッジの設定
        binding.textQuestionGradeBadge.text = GradeLabelFormatter.format(quiz.word.grade)
        binding.cardQuestionGradeBadge.visibility = View.VISIBLE

        // 既存の4択を隠し、並び替え用エリアを表示
        binding.choicesContainer.visibility = View.GONE
        binding.layoutSortContainer.visibility = View.VISIBLE
        
        updateTokenViews(ui)
    }

    private fun updateTokenViews(ui: LearningActivity) {
        val binding = ui.exposeBinding()
        val inflater = LayoutInflater.from(ui)
        
        // 解答エリアの更新
        binding.flexboxAnswer.removeAllViews()
        selectedTokens.forEachIndexed { index, token ->
            val itemView = inflater.inflate(R.layout.item_sort_token_selected, binding.flexboxAnswer, false)
            itemView.findViewById<TextView>(R.id.text_token).text = token
            itemView.setOnClickListener {
                selectedTokens.removeAt(index)
                availableTokens.add(token)
                updateTokenViews(ui)
            }
            binding.flexboxAnswer.addView(itemView)
        }

        // 候補エリアの更新
        binding.flexboxCandidates.removeAllViews()
        availableTokens.forEachIndexed { index, token ->
            val itemView = inflater.inflate(R.layout.item_sort_token_candidate, binding.flexboxCandidates, false)
            itemView.findViewById<TextView>(R.id.text_token).text = token
            itemView.setOnClickListener {
                availableTokens.removeAt(index)
                selectedTokens.add(token)
                updateTokenViews(ui)
            }
            binding.flexboxCandidates.addView(itemView)
        }
        
        binding.buttonSortCheck.isEnabled = selectedTokens.isNotEmpty()
        binding.buttonSortCheck.setOnClickListener {
            checkAnswer(ui)
        }
    }

    private fun checkAnswer(ui: LearningActivity) {
        val userAnswer = selectedTokens.joinToString(" ").replace(Regex("\\s+"), " ").trim()
        ui.exposeLearningViewModel().submitAnswer(userAnswer)
    }
}
