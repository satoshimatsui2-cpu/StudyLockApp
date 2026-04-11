package com.example.studylockapp.learning

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.studylockapp.R
import com.example.studylockapp.data.AppDatabase
import com.example.studylockapp.data.PointManager

class LearningViewModelFactory(private val context: Context) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(LearningViewModel::class.java)) {
            val db = AppDatabase.getInstance(context.applicationContext)
            val quizManager = QuizManager(db.wordDao())
            val pointManager = PointManager(context.applicationContext)
            val audioChecker = LearningAudioStateChecker(context.applicationContext)
            
            val requiredWarningText = context.getString(R.string.warning_audio_required)
            val optionalWarningText = context.getString(R.string.warning_audio_optional)
            
            return LearningViewModel(
                quizManager,
                pointManager,
                audioChecker,
                requiredWarningText,
                optionalWarningText
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
