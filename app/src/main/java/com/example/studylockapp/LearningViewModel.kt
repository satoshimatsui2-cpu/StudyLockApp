package com.example.studylockapp

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.example.studylockapp.data.WordEntity

class LearningViewModel(application: Application) : AndroidViewModel(application) {

    // UI に表示する級の名前
    private val _gradeName = MutableLiveData<String>()
    val gradeName: LiveData<String> = _gradeName

    // UI に表示する単語数
    private val _wordCount = MutableLiveData<Int>()
    val wordCount: LiveData<Int> = _wordCount

    fun setGradeInfo(gradeFilter: String, allWords: List<WordEntity>) {
        _gradeName.value = convertGradeFilterToName(gradeFilter)
        _wordCount.value = allWords.size
    }

    private fun convertGradeFilterToName(gradeFilter: String): String {
        return when (gradeFilter) {
            "All" -> getApplication<Application>().getString(R.string.label_grade_all)
            "5" -> getApplication<Application>().getString(R.string.grade_5)
            "4" -> getApplication<Application>().getString(R.string.grade_4)
            "3" -> getApplication<Application>().getString(R.string.grade_3)
            "2.5" -> getApplication<Application>().getString(R.string.grade_25)
            "2" -> getApplication<Application>().getString(R.string.grade_2)
            "1.5" -> getApplication<Application>().getString(R.string.grade_15)
            "1" -> getApplication<Application>().getString(R.string.grade_1)
            else -> gradeFilter
        }
    }
}