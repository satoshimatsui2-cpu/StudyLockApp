package com.stulab.studylockapp.data

import android.content.Context
import android.util.Log
import com.stulab.studylockapp.data.db.ChoiceMeaningDao
import com.stulab.studylockapp.data.db.ChoiceMeaningEntity
import com.stulab.studylockapp.data.db.WordDao
import com.stulab.studylockapp.learning.ReviewMeaningResolver
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
    private val choiceMeaningDao: ChoiceMeaningDao,
    private val appSettings: AppSettings
) {
    companion object {
        private const val TAG = "TsvImporter"
        private const val CURRENT_WORD_DATA_VERSION = 3
        private const val CURRENT_CHOICE_MEANING_VERSION = 2
    }

    suspend fun seedIfNeeded() {
        val wordCount = wordDao.countAllWords()
        val savedWordVersion = appSettings.wordDataVersion
        
        // 単語データのインポート
        if (wordCount == 0 || savedWordVersion < CURRENT_WORD_DATA_VERSION) {
            Log.d(TAG, "Starting word data import. Reason: count=$wordCount, savedVersion=$savedWordVersion, targetVersion=$CURRENT_WORD_DATA_VERSION")
            
            if (savedWordVersion > 0 && savedWordVersion < CURRENT_WORD_DATA_VERSION) {
                wordDao.deleteBuiltInWords()
            }

            if (importAllGrades()) {
                appSettings.wordDataVersion = CURRENT_WORD_DATA_VERSION
            }
        }

        // 選択肢意味補完データのインポート
        val choiceMeaningCount = choiceMeaningDao.countAll()
        val savedChoiceVersion = appSettings.choiceMeaningDataVersion

        if (choiceMeaningCount == 0 || savedChoiceVersion < CURRENT_CHOICE_MEANING_VERSION) {
            Log.d(TAG, "Starting choice meaning data import. Reason: count=$choiceMeaningCount, savedVersion=$savedChoiceVersion, targetVersion=$CURRENT_CHOICE_MEANING_VERSION")
            
            if (importChoiceMeanings()) {
                appSettings.choiceMeaningDataVersion = CURRENT_CHOICE_MEANING_VERSION
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

    private suspend fun importChoiceMeanings(): Boolean = withContext(Dispatchers.IO) {
        val fileName = "words/choice_meanings.tsv"
        try {
            context.assets.open(fileName).use { inputStream ->
                BufferedReader(InputStreamReader(inputStream)).use { reader ->
                    val entities = mutableListOf<ChoiceMeaningEntity>()
                    val header = reader.readLine()
                    if (header == null) return@withContext false

                    var lineNumber = 1
                    while (true) {
                        val line = reader.readLine() ?: break
                        lineNumber++
                        if (line.isBlank()) continue

                        val columns = TsvParser.parseLine(line)
                        if (columns.size >= 4) {
                            val english = columns[1]
                            val normalized = ReviewMeaningResolver.normalizeChoiceText(english)
                            val quizMode = if (columns.size > 4) columns[4].trim().takeIf { it.isNotBlank() } else null
                            val sourceWordId = if (columns.size > 5) columns[5].trim().toLongOrNull() else null
                            
                            val lookupKey = if (columns[0].isNotBlank()) columns[0] else ReviewMeaningResolver.generateLookupKey(normalized, quizMode, sourceWordId)

                            entities.add(ChoiceMeaningEntity(
                                lookupKey = lookupKey,
                                normalizedText = normalized,
                                displayText = english,
                                japanese = columns[3],
                                quizMode = quizMode,
                                sourceWordId = sourceWordId,
                                note = if (columns.size >= 7) columns[6] else null
                            ))
                        }

                        if (entities.size >= 500) {
                            choiceMeaningDao.upsertAll(entities.toList())
                            entities.clear()
                        }
                    }
                    if (entities.isNotEmpty()) {
                        choiceMeaningDao.upsertAll(entities.toList())
                    }
                }
            }
            Log.d(TAG, "Choice meaning data import completed.")
            true
        } catch (e: java.io.FileNotFoundException) {
            Log.w(TAG, "File not found: $fileName. Skipping choice meanings.")
            true // 任意ファイルなので成功扱い
        } catch (e: Exception) {
            Log.e(TAG, "Error importing choice meanings", e)
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
