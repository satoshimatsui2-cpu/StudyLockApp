package com.example.studylockapp.data

import android.content.Context
import android.util.Log
import com.example.studylockapp.ListeningQuestion
import com.example.studylockapp.R
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * CSVファイルからデータを読み込むためのヘルパークラス
 */
class CsvDataLoader(private val context: Context) {

    fun loadWords(): List<WordEntity> {
        val result = mutableListOf<WordEntity>()
        runCatching {
            context.resources.openRawResource(R.raw.words).use { input ->
                BufferedReader(InputStreamReader(input)).useLines { lines ->
                    lines.drop(1).forEach { line ->
                        val cols = parseCsvLine(line)
                        if (cols.size >= 7) {
                            result.add(WordEntity(
                                no = cols[0].trim().toIntOrNull() ?: 0,
                                grade = cols[1].trim(),
                                word = cols[2].trim(),
                                japanese = cols[3].trim(),
                                description = cols[4].trim(),
                                smallTopicId = cols[5].trim(),
                                mediumCategoryId = cols[6].trim()
                            ))
                        }
                    }
                }
            }
        }.onFailure { Log.e("CsvDataLoader", "Error reading words CSV", it) }
        return result
    }

    fun loadListeningQuestions(): List<ListeningQuestion> {
        val result = mutableListOf<ListeningQuestion>()
        runCatching {
            // rawリソースから "listening2" を探す
            val resId = context.resources.getIdentifier("listening2", "raw", context.packageName)
            if (resId != 0) {
                context.resources.openRawResource(resId).use { input ->
                    BufferedReader(InputStreamReader(input)).useLines { lines ->
                        lines.drop(1).forEach { line ->
                            val cols = parseCsvLine(line)
                            if (cols.size >= 11) {
                                val id = cols[0].trim().toIntOrNull() ?: 0
                                result.add(ListeningQuestion(
                                    id = id,
                                    grade = cols[1],
                                    script = cols[3].replace("\n", ""),
                                    question = cols[4],
                                    options = listOf(cols[5], cols[6], cols[7], cols[8]),
                                    correctIndex = (cols[9].toIntOrNull() ?: 1) - 1,
                                    explanation = cols[10]
                                ))
                            }
                        }
                    }
                }
            }
        }.onFailure { Log.e("CsvDataLoader", "Error reading listening CSV", it) }
        return result
    }

// CsvDataLoader.kt

    fun loadFillBlankQuestions(): List<FillBlankQuestion> {
        val questions = mutableListOf<FillBlankQuestion>()
        val TAG = "CsvDataLoader"
        Log.d(TAG, "loadFillBlankQuestions: Starting to load questions from res/raw.")

        try {
            // ▼▼▼ 修正: assets.open ではなく resources.openRawResource を使用 ▼▼▼
            // ファイル名: fill_in_the_blank_questions.csv -> R.raw.fill_in_the_blank_questions
            context.resources.openRawResource(R.raw.fill_in_the_blank_questions).bufferedReader().useLines { lines ->

                Log.d(TAG, "loadFillBlankQuestions: Raw resource opened successfully.")
                var parsedCount = 0
                val lineList = lines.toList()
                Log.d(TAG, "loadFillBlankQuestions: Total lines: ${lineList.size}")

                lineList.drop(1) // ヘッダーをスキップ
                    .forEach { line ->
                        val tokens = line.split(",")
                        if (tokens.size >= 10) {
                            try {
                                val id = tokens[0].toInt()
                                val grade = tokens[1]
                                val unit = tokens[2].toInt()
                                val questionText = tokens[3]
                                val choices = listOf(tokens[4], tokens[5], tokens[6], tokens[7])
                                val correctOption = tokens[8].toIntOrNull()
                                val explanation = tokens[9]

                                if (correctOption != null && correctOption in 1..4) {
                                    questions.add(
                                        FillBlankQuestion(
                                            id = id,
                                            grade = grade,
                                            unit = unit,
                                            question = questionText,
                                            choices = choices,
                                            correctIndex = correctOption - 1,
                                            explanation = explanation
                                        )
                                    )
                                    parsedCount++
                                }
                            } catch (e: NumberFormatException) {
                                Log.e(TAG, "loadFillBlankQuestions: Parse error on line: $line")
                            }
                        }
                    }
                Log.d(TAG, "loadFillBlankQuestions: Parsing complete. Count: $parsedCount")
            }
        } catch (e: Exception) {
            Log.e(TAG, "loadFillBlankQuestions: Failed to read raw resource.", e)
        }
        return questions
    }

    private fun parseCsvLine(line: String): List<String> {
        val result = mutableListOf<String>()
        var current = StringBuilder()
        var inQuotes = false
        for (char in line) {
            when {
                char == '"' -> inQuotes = !inQuotes
                char == ',' && !inQuotes -> { result.add(current.toString()); current = StringBuilder() }
                else -> current.append(char)
            }
        }
        result.add(current.toString())
        return result
    }
}
