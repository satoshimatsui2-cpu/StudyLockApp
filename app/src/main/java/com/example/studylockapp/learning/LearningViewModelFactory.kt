package com.example.studylockapp.learning

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.studylockapp.R
import com.example.studylockapp.data.AppDatabase
import com.example.studylockapp.data.PointManager
import com.example.studylockapp.data.AppSettings

class LearningViewModelFactory(private val context: Context) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(LearningViewModel::class.java)) {
            val db = AppDatabase.getInstance(context.applicationContext)
            val wordDao = db.wordDao()
            val masteryDao = db.wordMasteryDao()
            
            val appSettings = AppSettings(context.applicationContext)
            // AppSettings.currentLearningGrade は String 型なので、toIntOrNull() で Int に変換
            val userLevel = appSettings.currentLearningGrade.toIntOrNull() ?: 2
            
            val quizManager = QuizManager(wordDao, masteryDao, userLevel = userLevel)
            val pointManager = PointManager(context.applicationContext)
            val audioChecker = LearningAudioStateChecker(context.applicationContext)
            
            val requiredWarningText = context.getString(R.string.warning_audio_required)
            val optionalWarningText = context.getString(R.string.warning_audio_optional)
            
            return LearningViewModel(
                context.applicationContext,
                wordDao,
                masteryDao,
                quizManager,
                pointManager,
                audioChecker,
                requiredWarningText,
                optionalWarningText,
                appSettings
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
