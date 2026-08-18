package com.stulab.studylockapp.data

import android.content.Context
import com.stulab.studylockapp.R
import com.stulab.studylockapp.learning.SpellingEligibilityChecker
import com.stulab.studylockapp.ui.ProgressMetricUiModel
import com.stulab.studylockapp.ui.TopProgressUiModel

class ProgressRepository(private val db: AppDatabase) {
    private val summaryDao = db.progressSummaryDao()

    suspend fun getTopProgress(context: Context, grade: Int): TopProgressUiModel {
        val totalCount = summaryDao.getTotalWordCount(grade)
        val shortTermCount = summaryDao.getShortTermMasterCount(grade)
        val longTermCount = summaryDao.getLongTermMasterCount(grade)
        val spellingCleared = summaryDao.getSpellingClearedCount(grade)
        val wordPronCleared = summaryDao.getWordPronunciationClearedCount(grade)
        val sentencePronCleared = summaryDao.getSentencePronunciationClearedCount(grade)

        val allWords = summaryDao.getWordsByGrade(grade)
        
        val spellingEligibleCount = allWords.count { SpellingEligibilityChecker.isEligible(it) }
        val sentenceEligibleCount = allWords.count { hasValidSentence(it.sentence) }

        return TopProgressUiModel(
            shortTerm = ProgressMetricUiModel(
                label = "短期",
                iconResId = R.drawable.ic_history_24,
                completedCount = shortTermCount,
                eligibleCount = totalCount
            ),
            longTerm = ProgressMetricUiModel(
                label = "長期",
                iconResId = R.drawable.ic_emoji_events_24,
                completedCount = longTermCount,
                eligibleCount = totalCount
            ),
            spelling = ProgressMetricUiModel(
                label = "スペル",
                iconResId = R.drawable.ic_edit_24,
                completedCount = spellingCleared,
                eligibleCount = spellingEligibleCount
            ),
            wordPronunciation = ProgressMetricUiModel(
                label = "単語発音",
                iconResId = R.drawable.ic_mic_24,
                completedCount = wordPronCleared,
                eligibleCount = totalCount
            ),
            sentencePronunciation = ProgressMetricUiModel(
                label = "例文発音",
                iconResId = R.drawable.ic_hearing_24,
                completedCount = sentencePronCleared,
                eligibleCount = sentenceEligibleCount
            )
        )
    }

    private fun hasValidSentence(sentence: String?): Boolean {
        if (sentence == null) return false
        val words = sentence.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
        return words.size >= 3
    }
}
