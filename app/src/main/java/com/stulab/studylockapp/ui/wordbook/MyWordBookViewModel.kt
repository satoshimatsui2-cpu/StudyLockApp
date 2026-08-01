package com.stulab.studylockapp.ui.wordbook

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stulab.studylockapp.GradeUtils
import com.stulab.studylockapp.data.AppDatabase
import com.stulab.studylockapp.data.AppSettings
import com.stulab.studylockapp.data.MyWordBookImporter
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class MyWordBookViewModel(
    private val db: AppDatabase,
    private val importer: MyWordBookImporter,
    private val appSettings: AppSettings
) : ViewModel() {

    private val _uiState = MutableStateFlow(MyWordBookUiState())
    val uiState: StateFlow<MyWordBookUiState> = _uiState.asStateFlow()

    init {
        observeWordCounts()
    }

    private fun observeWordCounts() {
        db.wordDao().getMyWordBookCountsFlow()
            .onEach { counts ->
                val slots = (90..99).map { grade ->
                    val total = counts.find { it.grade == grade }?.total ?: 0
                    WordBookSlot(
                        grade = grade,
                        displayName = GradeUtils.toDisplay(grade.toString(), appSettings),
                        customName = appSettings.getMyWordBookName(grade),
                        wordCount = total,
                        isRegistered = total > 0
                    )
                }
                _uiState.update { it.copy(slots = slots, isLoading = false) }
            }
            .launchIn(viewModelScope)
    }

    fun importTsv(context: Context, uri: Uri, grade: Int, initialName: String? = null) {
        if (_uiState.value.processingGrade != null) return

        viewModelScope.launch {
            _uiState.update { it.copy(
                processingGrade = grade, 
                isLoading = true, 
                errorMessage = null, 
                successMessage = null, 
                validationErrors = emptyList()
            ) }

            // 初回インポート（未設定）の場合のみ名前を設定
            if (appSettings.getMyWordBookName(grade) == null && initialName != null) {
                appSettings.setMyWordBookName(grade, initialName)
            }
            
            val result = try {
                context.contentResolver.openInputStream(uri)?.use { inputStream ->
                    importer.importTsv(inputStream, grade)
                } ?: MyWordBookImporter.ImportResult.Error("ファイルを開けませんでした")
            } catch (e: Exception) {
                MyWordBookImporter.ImportResult.Error("読み込みエラー: ${e.message}")
            }

            when (result) {
                is MyWordBookImporter.ImportResult.Success -> {
                    val name = appSettings.getMyWordBookDisplayName(grade)
                    _uiState.update { it.copy(
                        processingGrade = null, 
                        isLoading = false,
                        successMessage = "「$name」に${result.count}件の単語をインポートしました"
                    ) }
                }
                is MyWordBookImporter.ImportResult.Error -> {
                    _uiState.update { it.copy(
                        processingGrade = null, 
                        isLoading = false,
                        errorMessage = result.message,
                        validationErrors = result.validationErrors
                    ) }
                }
            }
        }
    }

    fun deleteWordBook(grade: Int) {
        if (_uiState.value.processingGrade != null) return

        viewModelScope.launch {
            _uiState.update { it.copy(processingGrade = grade, isLoading = true, errorMessage = null, successMessage = null) }
            try {
                importer.deleteMyWordBook(grade)
                
                // カスタム名も削除
                appSettings.clearMyWordBookName(grade)

                // もし削除したGradeが現在選択中ならデフォルト(3級)に戻す
                if (appSettings.currentLearningGrade == grade.toString()) {
                    appSettings.currentLearningGrade = "3"
                }

                _uiState.update { it.copy(
                    processingGrade = null, 
                    isLoading = false,
                    successMessage = "My単語帳を削除しました"
                ) }
            } catch (e: Exception) {
                _uiState.update { it.copy(
                    processingGrade = null, 
                    isLoading = false,
                    errorMessage = "削除に失敗しました"
                ) }
            }
        }
    }

    fun renameWordBook(grade: Int, newName: String) {
        val cleaned = newName.trim().replace("\n", "").replace("\t", "")
        if (cleaned.isEmpty()) {
            _uiState.update { it.copy(errorMessage = "単語帳名を入力してください") }
            return
        }
        if (cleaned.length > 30) {
            _uiState.update { it.copy(errorMessage = "単語帳名は30文字以内で入力してください") }
            return
        }

        appSettings.setMyWordBookName(grade, cleaned)
        // Flowを再トリガーするためにObserveを呼び出すか、Stateを直接更新する
        // ここでは SharedPreferences の変更を監視していないので、手動でスロットリストを更新する
        _uiState.update { state ->
            val updatedSlots = state.slots.map { slot ->
                if (slot.grade == grade) {
                    slot.copy(
                        displayName = appSettings.getMyWordBookDisplayName(grade),
                        customName = cleaned
                    )
                } else slot
            }
            state.copy(slots = updatedSlots)
        }
    }

    fun clearMessages() {
        _uiState.update { it.copy(errorMessage = null, successMessage = null, validationErrors = emptyList()) }
    }
}
