package com.example.studylockapp.data

import android.content.Context
import android.util.Log
import com.example.studylockapp.R
import com.example.studylockapp.data.db.WordDao
import java.io.BufferedReader
import java.io.InputStreamReader

object CsvImporter {

    /**
     * res/raw/words.tsv からデータをインポートする
     */
    suspend fun import(context: Context, dao: WordDao) {
        try {
            val input = context.resources.openRawResource(R.raw.words)
            val reader = BufferedReader(InputStreamReader(input))

            Log.d("DEBUG", "IMPORT START")
            val header = reader.readLine() // ヘッダー飛ばす
            Log.d("DEBUG", "header = $header")

            val list = mutableListOf<WordEntity>()

            reader.forEachLine { line ->
                Log.d("DEBUG", "line = $line")
                val parts = line.split("\t").map { it.trim() }
                Log.d("DEBUG", "parts size = ${parts.size}")

                // 原因特定のため一時的にチェックを外す、またはログを残す
                if (parts.size < 10) {
                    Log.d("DEBUG", "skip row (size < 10): $parts")
                    return@forEachLine
                }

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
                        related = emptyList(),   // とりあえずOK
                        confusion = emptyList() // とりあえずOK
                    )
                    list.add(entity)
                } catch (e: Exception) {
                    Log.e("DEBUG", "Line parse error: $line", e)
                }
            }

            if (list.isNotEmpty()) {
                dao.insertAll(list)
                Log.d("DEBUG", "Imported ${list.size} words successfully")
            } else {
                Log.w("DEBUG", "No words were parsed from TSV")
            }

        } catch (e: Exception) {
            Log.e("DEBUG", "TSV Import failed", e)
        }
    }
}
