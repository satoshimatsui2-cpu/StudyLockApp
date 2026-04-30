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
            val studyLogDao = db.studyLogDao()
            
            val appSettings = AppSettings(context.applicationContext)
            // AppSettings.safeLearningGrade は "1"〜"7" を返すため、Intに変換して QuizManager に渡す
            val userLevel = appSettings.safeLearningGrade.toIntOrNull()?.takeIf { it in 1..7 } ?: 3
            
            val quizManager = QuizManager(wordDao, masteryDao, studyLogDao, userLevel = userLevel)
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
