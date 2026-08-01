package com.stulab.studylockapp.ui.wordbook

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.stulab.studylockapp.data.AppDatabase
import com.stulab.studylockapp.data.AppSettings
import com.stulab.studylockapp.data.MyWordBookImporter

class MyWordBookViewModelFactory(private val context: Context) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MyWordBookViewModel::class.java)) {
            val db = AppDatabase.getInstance(context.applicationContext)
            val importer = MyWordBookImporter(db)
            val settings = AppSettings(context.applicationContext)
            return MyWordBookViewModel(db, importer, settings) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
