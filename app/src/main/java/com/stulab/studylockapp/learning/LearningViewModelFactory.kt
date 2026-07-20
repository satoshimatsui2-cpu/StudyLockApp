package com.stulab.studylockapp.learning

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.stulab.studylockapp.R
import com.stulab.studylockapp.data.AppDatabase
import com.stulab.studylockapp.data.PointManager
import com.stulab.studylockapp.data.AppSettings
import com.stulab.studylockapp.data.practical.PracticalTestRepository
import com.stulab.studylockapp.data.StudyHistoryRepository

class LearningViewModelFactory(private val context: Context) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(LearningViewModel::class.java)) {
            val appContext = context.applicationContext
            val db = AppDatabase.getInstance(appContext)
            
            val wordDao = db.wordDao()
            val masteryDao = db.wordMasteryDao()
            val studyLogDao = db.studyLogDao()
            val historyDao = db.practicalHistoryDao()
            
            val appSettings = AppSettings(appContext)
            val quizManager = QuizManager(wordDao, masteryDao, studyLogDao, appSettings)
            val pointManager = PointManager(appContext)
            val audioChecker = LearningAudioStateChecker(appContext)
            val practicalRepo = PracticalTestRepository(appContext, historyDao)
            
            val requiredWarningText = context.getString(R.string.warning_audio_required)
            val optionalWarningText = context.getString(R.string.warning_audio_optional)
            
            return LearningViewModel(
                appContext,
                wordDao,
                masteryDao,
                quizManager,
                pointManager,
                audioChecker,
                requiredWarningText,
                optionalWarningText,
                appSettings,
                practicalRepo
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
