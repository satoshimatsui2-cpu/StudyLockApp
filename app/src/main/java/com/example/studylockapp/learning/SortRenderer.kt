package com.example.studylockapp.learning

import android.view.LayoutInflater
import android.view.View
import android.widget.TextView
import com.example.studylockapp.R
import com.example.studylockapp.LearningActivity

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

        // UIの初期化 - showBasicQuizを呼び出すことで言語判定と文字サイズ設定を共通化する
        ui.showBasicQuiz("正しい英文の順番に並べてください。", quiz.question, emptyList())

        // 並び替え用エリアを表示し、4択エリアを隠す
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
