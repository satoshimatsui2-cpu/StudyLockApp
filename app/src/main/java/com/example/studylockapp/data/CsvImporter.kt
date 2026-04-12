package com.example.studylockapp.data

import android.content.Context
import android.util.Log
import com.example.studylockapp.R
import com.example.studylockapp.data.db.WordDao
import java.io.BufferedReader
import java.io.InputStreamReader

object CsvImporter {

    private const val TAG = "QuizFlow"

    /**
     * 単語テーブルが空の場合に初期データを投入する
     */
    suspend fun seedIfNeeded(context: Context, dao: WordDao) {
        val countBefore = dao.countAllWords()
        if (countBefore > 0) {
            Log.e(TAG, "[Seed] Already has $countBefore words. Skipping.")
            return
        }

        Log.e(TAG, "[Seed] seed started. countBefore=$countBefore")
        
        try {
            val input = context.resources.openRawResource(R.raw.words)
            val reader = BufferedReader(InputStreamReader(input))

            reader.readLine() // skip header

            val list = mutableListOf<WordEntity>()
            reader.forEachLine { line ->
                val parts = line.split("\t").map { it.trim() }
                if (parts.size >= 10) {
                    try {
                        val entity = WordEntity(
                            no = parts[0].toInt(),
                            grade = parts[1].toInt(),
                            word = parts[2],
                            japanese = parts[3],
                            description = parts[4],
                            sentence = parts[5],
                            japaneseSentence = parts[6],
                            phonetic = parts[7],
                            type = parts[8],
                            pos = parts[9],
                            difficulty = parts.getOrNull(10)?.toIntOrNull() ?: 1,
                            frequency = parts.getOrNull(11)?.toIntOrNull() ?: 1,
                            related = emptyList(),
                            confusion = emptyList()
                        )
                        list.add(entity)
                    } catch (e: Exception) {
                        // ignore malformed lines
                    }
                }
            }

            if (list.isNotEmpty()) {
                dao.insertAll(list)
                val countAfter = dao.countAllWords()
                Log.e(TAG, "[Seed] seed finished. insertedCount=${list.size}, countAfter=$countAfter")
            } else {
                Log.e(TAG, "[Seed] FAILED: No words were parsed from raw resource.")
            }

        } catch (e: Exception) {
            Log.e(TAG, "[Seed] ERROR: Seed failed", e)
        }
    }
}
