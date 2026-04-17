package com.example.studylockapp.data

import android.content.Context
import android.util.Log
import com.example.studylockapp.R
import com.example.studylockapp.data.db.WordDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * TSVファイルから語彙データをインポートするクラス
 */
class TsvImporter(
    private val context: Context,
    private val wordDao: WordDao
) {
    private val TAG = "TsvImporter"

    suspend fun seedIfNeeded() {
        val count = wordDao.countAllWords()
        if (count == 0) {
            importFromTsv()
        }
    }

    suspend fun importFromTsv() = withContext(Dispatchers.IO) {
        try {
            val inputStream = context.resources.openRawResource(R.raw.words)
            val reader = BufferedReader(InputStreamReader(inputStream))
            val entities = mutableListOf<WordEntity>()

            val header = reader.readLine() // ヘッダー行をスキップ
            if (header == null) {
                Log.e(TAG, "TSV file is empty")
                return@withContext
            }

            var lineNumber = 2 // ヘッダーが1行目なので、データは2行目から
            reader.forEachLine { line ->
                if (line.isNotBlank()) {
                    val columns = TsvParser.parseLine(line)
                    if (columns.size >= 15) {
                        val row = TsvWordRow(
                            no = columns[0],
                            grade = columns[1],
                            word = columns[2],
                            japanese = columns[3],
                            description = columns[4],
                            sentence = columns[5],
                            japaneseSentence = columns[6],
                            pos = columns[7],
                            difficulty = columns[8],
                            frequency = columns[9],
                            choicesEnJa = columns[10],
                            choicesJaEn = columns[11],
                            choicesListening = columns[12],
                            synonyms = columns[13],
                            antonyms = columns[14]
                        )
                        TsvWordMapper.mapToEntity(row, lineNumber)?.let {
                            entities.add(it)
                        }
                    } else {
                        Log.e(TAG, "Error at line $lineNumber: Insufficient columns. found=${columns.size}, expected=15")
                    }
                }
                lineNumber++
            }
            
            if (entities.isNotEmpty()) {
                wordDao.insertAll(entities)
                Log.d(TAG, "Successfully imported ${entities.size} words from TSV")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Critical error during TSV import", e)
        }
    }
}
