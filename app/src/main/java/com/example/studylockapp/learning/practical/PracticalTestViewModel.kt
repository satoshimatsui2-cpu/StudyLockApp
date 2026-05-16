package com.example.studylockapp.learning.practical

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.studylockapp.data.AppSettings
import com.example.studylockapp.data.PointManager
import com.example.studylockapp.data.practical.PracticalQuestion
import com.example.studylockapp.data.practical.PracticalQuizMode
import com.example.studylockapp.data.practical.PracticalTestRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * 実践テスト画面用のUI状態を保持するデータクラス。
 */
data class PracticalUiState(
    val isLoading: Boolean = false,
    val question: PracticalQuestion? = null,
    val shuffledChoices: List<String> = emptyList(),
    val correctChoice: String = "",
    val isAnswered: Boolean = false,
    val isCorrect: Boolean = false,
    val selectedAnswer: String = "",
    val pointsGained: Int = 0,
    val error: Boolean = false
)

/**
 * 実践テストのロジックを管理するViewModel。
 */
class PracticalTestViewModel(
    private val repository: PracticalTestRepository,
    private val pointManager: PointManager,
    private val appSettings: AppSettings
) : ViewModel() {

    private val _uiState = MutableStateFlow(PracticalUiState())
    val uiState = _uiState.asStateFlow()

    // セッションを一意に識別するためのID
    private val sessionId = UUID.randomUUID().toString()

    /**
     * 指定されたグレードに合わせた問題をランダムに1問読み込みます。
     * 引数で渡された grade を優先し、fallback として設定値やデフォルト値を使用しません。
     */
    fun loadQuestion(grade: Int) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = false) }
            
            // 穴埋め問題を取得
            val questions = repository.getQuestions(PracticalQuizMode.FILL_BLANK, grade)
            
            if (questions.isEmpty()) {
                _uiState.update { it.copy(isLoading = false, error = true) }
                return@launch
            }

            val question = questions.random()
            val correctChoice = question.choices[question.correctOptionIndex - 1]
            val shuffled = question.choices.shuffled()

            _uiState.update { state ->
                state.copy(
                    isLoading = false,
                    question = question,
                    shuffledChoices = shuffled,
                    correctChoice = correctChoice,
                    isAnswered = false,
                    isCorrect = false,
                    selectedAnswer = "",
                    pointsGained = 0
                )
            }
        }
    }

    /**
     * ユーザーの回答を判定し、ポイント加算と履歴保存を行います。
     */
    fun submitAnswer(answer: String) {
        val currentState = _uiState.value
        if (currentState.isAnswered || currentState.question == null) return

        val isCorrect = answer == currentState.correctChoice
        val points = if (isCorrect) 10 else 0

        // 回答済み状態と結果を即座にUIに反映
        _uiState.update { it.copy(
            isAnswered = true,
            isCorrect = isCorrect,
            selectedAnswer = answer,
            pointsGained = points
        ) }

        viewModelScope.launch {
            try {
                // 履歴保存 (スナップショットを含む)
                repository.saveHistory(
                    question = currentState.question,
                    selectedAnswer = answer,
                    isCorrect = isCorrect,
                    points = points,
                    sessionId = sessionId
                )

                // 正解時のみポイントを加算
                if (isCorrect) {
                    pointManager.add(points)
                }
            } catch (e: Exception) {
                Log.e("PracticalTestViewModel", "Failed to save history or add points", e)
            }
        }
    }
}
