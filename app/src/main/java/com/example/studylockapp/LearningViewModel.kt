package com.example.studylockapp

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.example.studylockapp.data.AppDatabase
import com.example.studylockapp.data.WordEntity
import com.example.studylockapp.data.db.WordProgressDao
import com.example.studylockapp.learning.AnswerResult
import com.example.studylockapp.learning.ConversationStrategy
import com.example.studylockapp.learning.LearningModeStrategy
import com.example.studylockapp.learning.QuestionUiState
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class LearningViewModel(application: Application) : AndroidViewModel(application) {

    // Daoの取得
    private val dao: WordProgressDao = AppDatabase.getInstance(application).wordProgressDao()

    private var currentStrategy: LearningModeStrategy? = null

    // Activityに公開するUI状態
    private val _questionUiState = MutableStateFlow<QuestionUiState>(QuestionUiState.Loading)
    val questionUiState: StateFlow<QuestionUiState> = _questionUiState.asStateFlow()

    // Activityに公開する判定結果
    private val _answerResult = MutableSharedFlow<AnswerResult>()
    val answerResult: SharedFlow<AnswerResult> = _answerResult.asSharedFlow()

    // 外部からセットされるデータ
    var listeningQuestions: List<ListeningQuestion> = emptyList()
    
    // ヘッダー表示用
    private val _gradeName = MutableLiveData<String>()
    val gradeName: LiveData<String> = _gradeName

    private val _wordCount = MutableLiveData<Int>()
    val wordCount: LiveData<Int> = _wordCount

    private var allWords: List<WordEntity> = emptyList()

    fun setGradeInfo(grade: String, words: List<WordEntity>) {
        _gradeName.value = grade
        _wordCount.value = words.size
        this.allWords = words
    }

    fun setMode(modeKey: String) {
        currentStrategy = createStrategy(modeKey)
        loadNextQuestion()
    }

    fun loadNextQuestion() {
        viewModelScope.launch {
            _questionUiState.value = QuestionUiState.Loading
            val strategy = currentStrategy ?: return@launch
            _questionUiState.value = strategy.createQuestion()
        }
    }

    fun submitAnswer(answer: Any) {
        viewModelScope.launch {
            val strategy = currentStrategy ?: return@launch
            val result = strategy.judgeAnswer(answer)
            _answerResult.emit(result)
        }
    }

    private fun createStrategy(modeKey: String): LearningModeStrategy? {
        return when (modeKey) {
            // 修正点: LearningActivity ではなく LearningModes を使い、定数名も合わせる
            LearningModes.TEST_LISTEN_Q2 -> ConversationStrategy(getApplication(), listeningQuestions, dao, modeKey)
            else -> null
        }
    }
}