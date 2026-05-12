package com.example.studylockapp.data

import android.content.Context
import android.util.Log
import com.example.studylockapp.data.db.WordDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader

/**
 * TSVファイルから語彙データをインポートするクラス
 */
class TsvImporter(
    private val context: Context,
    private val wordDao: WordDao,
    private val appSettings: AppSettings
) {
    companion object {
        private const val TAG = "TsvImporter"
        private const val CURRENT_WORD_DATA_VERSION = 2
    }

    suspend fun seedIfNeeded() {
        val count = wordDao.countAllWords()
        val savedVersion = appSettings.wordDataVersion
        
        // 初回、またはデータバージョンが上がった場合にインポートを実行
        if (count == 0 || savedVersion < CURRENT_WORD_DATA_VERSION) {
            Log.d(TAG, "Starting word data import. Reason: count=$count, savedVersion=$savedVersion, targetVersion=$CURRENT_WORD_DATA_VERSION")
            val success = importAllGrades()
            if (success) {
                appSettings.wordDataVersion = CURRENT_WORD_DATA_VERSION
            }
        }
    }

    private suspend fun importAllGrades(): Boolean = withContext(Dispatchers.IO) {
        try {
            // リリース後を見据え、非破壊的インポート（Upsert）に変更
            // 既存の学習進捗（WordMasteryなど）を維持するため deleteAll は行わない
            
            for (gradeNum in 1..7) {
                val fileName = "words/grade$gradeNum.tsv"
                try {
                    context.assets.open(fileName).use { inputStream ->
                        importSingleFile(inputStream, gradeNum)
                    }
                } catch (e: java.io.FileNotFoundException) {
                    Log.w(TAG, "File not found: $fileName. Skipping grade $gradeNum.")
                }
            }
            Log.d(TAG, "All grade files processing completed.")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Critical error during TSV import", e)
            false
        }
    }

    private suspend fun importSingleFile(inputStream: InputStream, expectedGrade: Int) {
        BufferedReader(InputStreamReader(inputStream)).use { reader ->
            val entities = mutableListOf<WordEntity>()

            // ヘッダー行をスキップ
            val header = reader.readLine()
            if (header == null) {
                Log.w(TAG, "Empty TSV file for grade $expectedGrade")
                return
            }

            var lineNumber = 1
            while (true) {
                val line = reader.readLine() ?: break
                lineNumber++

                if (line.isBlank()) continue

                val columns = TsvParser.parseLine(line)
                if (columns.size >= 15) {
                    val fileGrade = columns[1].trim().toIntOrNull()
                    if (fileGrade != expectedGrade) {
                        Log.e(TAG, "Grade mismatch in grade$expectedGrade.tsv line=$lineNumber: found=$fileGrade")
                        continue
                    }

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
                    Log.w(TAG, "Invalid TSV line grade=$expectedGrade line=$lineNumber columns=${columns.size}")
                    continue
                }

                if (entities.size >= 500) {
                    // @Upsert により、既存レコードは UPDATE、新規は INSERT となり CASCADE 削除を回避する
                    wordDao.upsertAll(entities.toList())
                    entities.clear()
                }
            }

            if (entities.isNotEmpty()) {
                wordDao.upsertAll(entities.toList())
                entities.clear()
            }

            Log.d(TAG, "Imported/Updated grade $expectedGrade")
        }
    }
}
