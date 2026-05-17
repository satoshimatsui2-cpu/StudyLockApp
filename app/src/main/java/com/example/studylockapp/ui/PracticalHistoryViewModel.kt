package com.example.studylockapp.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.studylockapp.data.AppDatabase
import com.example.studylockapp.data.db.PracticalHistoryEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 実践テスト履歴画面のデータを管理するViewModel
 */
class PracticalHistoryViewModel(application: Application) : AndroidViewModel(application) {

    private val dao = AppDatabase.getInstance(application).practicalHistoryDao()

    private val _historyItems = MutableStateFlow<List<PracticalHistoryEntity>>(emptyList())
    val historyItems = _historyItems.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()

    // 現在のフィルタ状態
    private var currentFilter = "ALL" // ALL, WRONG, UNSCORED

    /**
     * 履歴データを読み込みます。
     * 各問題の最新の解答結果のみを抽出して表示します。
     */
    fun loadHistory(filter: String = "ALL") {
        currentFilter = filter
        viewModelScope.launch {
            _isLoading.value = true
            val results = when (filter) {
                "WRONG" -> dao.getLatestWrongAnswersByQuestion()
                "UNSCORED" -> dao.getLatestUnscoredHistoryByQuestion()
                else -> dao.getLatestHistoryByQuestion()
            }
            _historyItems.value = results
            _isLoading.value = false
        }
    }

    /**
     * 現在のフィルタ設定で再読み込みします。
     */
    fun refresh() {
        loadHistory(currentFilter)
    }
}
