package com.example.studylockapp.learning.practical

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.studylockapp.data.AppSettings
import com.example.studylockapp.data.PointManager
import com.example.studylockapp.data.SilentMode
import com.example.studylockapp.data.practical.PracticalQuestion
import com.example.studylockapp.data.practical.PracticalQuizMode
import com.example.studylockapp.data.practical.PracticalTestRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * リスニング問題の再生状態を表すEnum
 */
enum class ListeningPlaybackState {
    NONE,
    WAITING_TO_START,
    PLAYING_FIRST,
    ANSWERING,
    PLAYING_AGAIN,
    FINISHED
}

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
    val error: Boolean = false,

    // --- リスニング用追加 ---
    val isListeningQuestion: Boolean = false,
    val listeningPlaybackState: ListeningPlaybackState = ListeningPlaybackState.NONE,
    val hasStartedListening: Boolean = false,
    val hasUsedReplay: Boolean = false,
    val isScored: Boolean = true,
    val currentPlayingSegmentId: Int? = null,
    val listeningScript: String? = null,
    val listeningDisplaySegments: List<ListeningTtsSegment> = emptyList()
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
    private var sessionId = UUID.randomUUID().toString()

    /**
     * 指定されたグレードに合わせた問題をランダムに1問読み込みます。
     */
    fun loadQuestion(grade: Int) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = false) }
            
            // サイレントモードに基づき、出題可能なモードを決定
            val possibleModes = if (appSettings.silentMode == SilentMode.ON) {
                listOf(PracticalQuizMode.FILL_BLANK)
            } else {
                listOf(PracticalQuizMode.LISTENING, PracticalQuizMode.FILL_BLANK)
            }

            var selectedQuestion: PracticalQuestion? = null
            
            // 候補モードからランダムに選び、問題が存在するかチェック
            for (mode in possibleModes.shuffled()) {
                val questions = repository.getQuestions(mode, grade)
                if (questions.isNotEmpty()) {
                    selectedQuestion = questions.random()
                    break
                }
            }

            if (selectedQuestion == null) {
                _uiState.update { it.copy(isLoading = false, error = true) }
                return@launch
            }

            val question = selectedQuestion
            val correctChoice = question.choices[question.correctOptionIndex - 1]
            val shuffled = question.choices.shuffled()
            
            // 判定強化
            val isListening = question.type == PracticalQuizMode.LISTENING || !question.ttsScript.isNullOrBlank()

            // 調査用ログ
            Log.d(
                "PracticalTestViewModel",
                "selected type=${question.type}, no=${question.no}, hasTts=${!question.ttsScript.isNullOrBlank()}, isListening=$isListening, script=${question.ttsScript?.take(30)}"
            )

            // 新規問題読み込み時にセッションIDを更新
            sessionId = UUID.randomUUID().toString()

            _uiState.update { state ->
                state.copy(
                    isLoading = false,
                    question = question,
                    shuffledChoices = shuffled,
                    correctChoice = correctChoice,
                    isAnswered = false,
                    isCorrect = false,
                    selectedAnswer = "",
                    pointsGained = 0,
                    // リスニング状態の初期化
                    isListeningQuestion = isListening,
                    listeningPlaybackState = if (isListening) ListeningPlaybackState.WAITING_TO_START else ListeningPlaybackState.NONE,
                    hasStartedListening = false,
                    hasUsedReplay = false,
                    isScored = true,
                    currentPlayingSegmentId = null,
                    listeningScript = if (isListening) question.ttsScript else null,
                    listeningDisplaySegments = emptyList()
                )
            }
        }
    }

    /**
     * リスニングの再生開始時の状態更新。
     */
    fun onListeningPlayStarted(isReplay: Boolean) {
        _uiState.update { state ->
            if (isReplay) {
                state.copy(
                    listeningPlaybackState = ListeningPlaybackState.PLAYING_AGAIN,
                    hasUsedReplay = true,
                    isScored = false
                )
            } else {
                state.copy(
                    listeningPlaybackState = ListeningPlaybackState.PLAYING_FIRST,
                    hasStartedListening = true
                )
            }
        }
    }

    /**
     * リスニングの再生完了時の状態更新。
     */
    fun onListeningPlayCompleted() {
        _uiState.update { state ->
            state.copy(
                listeningPlaybackState = ListeningPlaybackState.ANSWERING,
                currentPlayingSegmentId = null
            )
        }
    }

    /**
     * 現在再生中のセグメントIDを更新します。
     */
    fun onListeningSegmentChanged(segmentId: Int?) {
        _uiState.update { it.copy(currentPlayingSegmentId = segmentId) }
    }
    
    /**
     * 表示用セグメント（スクリプト）をセットします。
     */
    fun setListeningDisplaySegments(segments: List<ListeningTtsSegment>) {
        _uiState.update { it.copy(listeningDisplaySegments = segments) }
    }

    /**
     * ユーザーの回答を判定し、ポイント加算と履歴保存を行います。
     */
    fun submitAnswer(answer: String) {
        val currentState = _uiState.value
        if (currentState.isAnswered || currentState.question == null) return

        // リスニング問題の場合、再生中は回答をブロックする
        if (currentState.isListeningQuestion) {
            if (currentState.listeningPlaybackState == ListeningPlaybackState.WAITING_TO_START ||
                currentState.listeningPlaybackState == ListeningPlaybackState.PLAYING_FIRST ||
                currentState.listeningPlaybackState == ListeningPlaybackState.PLAYING_AGAIN) {
                return
            }
        }

        val isCorrect = answer == currentState.correctChoice
        
        // ポイント計算 (リスニング正解は20pt、穴埋めは10pt)
        val points = if (isCorrect && currentState.isScored) {
            if (currentState.isListeningQuestion) 20 else 10
        } else {
            0
        }

        // 履歴保存用のステータス決定
        val resultStatus = when {
            !currentState.isScored -> "UNSCORED"
            isCorrect -> "CORRECT"
            else -> "WRONG"
        }

        // UI状態を回答済みに更新
        _uiState.update { it.copy(
            isAnswered = true,
            isCorrect = isCorrect,
            selectedAnswer = answer,
            pointsGained = points,
            listeningPlaybackState = if (it.isListeningQuestion) ListeningPlaybackState.FINISHED else ListeningPlaybackState.NONE
        ) }

        viewModelScope.launch {
            try {
                // 履歴保存
                repository.saveHistory(
                    question = currentState.question,
                    selectedAnswer = answer,
                    isCorrect = isCorrect,
                    points = points,
                    sessionId = sessionId,
                    isScored = currentState.isScored,
                    usedReplay = currentState.hasUsedReplay,
                    resultStatus = resultStatus
                )

                if (isCorrect && currentState.isScored) {
                    pointManager.add(points)
                }
            } catch (e: Exception) {
                Log.e("PracticalTestViewModel", "Failed to save history or add points", e)
            }
        }
    }
}
