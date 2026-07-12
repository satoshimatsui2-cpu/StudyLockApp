package com.example.studylockapp.learning.practical

/**
 * 実践テスト画面でのUIイベント定義
 */
sealed class PracticalUiEvent {
    object ShowCorrect : PracticalUiEvent()
    object ShowWrong : PracticalUiEvent()
}
