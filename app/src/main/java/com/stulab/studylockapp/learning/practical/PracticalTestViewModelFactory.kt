package com.stulab.studylockapp.learning.practical

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.stulab.studylockapp.data.AppDatabase
import com.stulab.studylockapp.data.AppSettings
import com.stulab.studylockapp.data.PointManager
import com.stulab.studylockapp.data.practical.PracticalTestRepository

/**
 * PracticalTestViewModelのためのFactory
 */
class PracticalTestViewModelFactory(private val context: Context) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        // modelClass.isAssignableFrom を使用した安全な型チェック
        if (modelClass.isAssignableFrom(PracticalTestViewModel::class.java)) {
            val appContext = context.applicationContext
            val db = AppDatabase.getInstance(appContext)
            val historyDao = db.practicalHistoryDao()
            val repository = PracticalTestRepository(appContext, historyDao)
            val pointManager = PointManager(appContext)
            val appSettings = AppSettings(appContext)
            
            return PracticalTestViewModel(
                repository,
                pointManager,
                appSettings
            ) as T
        }
        // 指定されたクラスが作成対象外の場合は例外を投げる
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
