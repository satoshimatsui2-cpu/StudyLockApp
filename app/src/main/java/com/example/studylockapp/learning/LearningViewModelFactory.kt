package com.example.studylockapp.learning

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.studylockapp.data.AppDatabase
import com.example.studylockapp.data.PointManager

/**
 * LearningViewModelの依存関係を注入するためのFactory
 */
class LearningViewModelFactory(private val context: Context) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(LearningViewModel::class.java)) {
            val db = AppDatabase.getInstance(context.applicationContext)
            val quizManager = QuizManager(db.wordDao())
            val pointManager = PointManager(context.applicationContext)
            
            return LearningViewModel(quizManager, pointManager) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
