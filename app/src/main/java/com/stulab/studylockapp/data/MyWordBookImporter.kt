package com.stulab.studylockapp.data

import androidx.room.withTransaction
import com.stulab.studylockapp.data.db.WordDao
import com.stulab.studylockapp.learning.QuizMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader

/**
 * ユーザーが選択したTSVファイルからマイ単語帳をインポートするクラス。
 * フル15列形式（推奨）と簡易5列形式（後方互換）の両方をサポート。
 */
class MyWordBookImporter(
    private val db: AppDatabase
) {
    private val wordDao = db.wordDao()
    private val favoriteDao = db.favoriteWordDao()
    private val studyLogDao = db.studyLogDao()
    private val voiceCheckDao = db.voiceCheckDao()

    sealed class ImportResult {
        data class Success(val count: Int) : ImportResult()
        data class Error(val message: String, val validationErrors: List<String> = emptyList()) : ImportResult()
    }

    /**
     * TSVファイルをインポートする。
     * @param inputStream TSVの入力ストリーム
     * @param grade インポート先のGrade (90..99)
     */
    suspend fun importTsv(inputStream: InputStream, grade: Int): ImportResult = withContext(Dispatchers.IO) {
        if (grade !in 90..99) {
            return@withContext ImportResult.Error("不正なGradeです (90-99のみ許可)")
        }

        try {
            val parseResult = parseAndValidateTsv(inputStream)
            if (parseResult.errors.isNotEmpty()) {
                return@withContext ImportResult.Error("バリデーションエラーが発生しました", parseResult.errors)
            }

            val rows = parseResult.rows
            
            // 全体バリデーション
            if (rows.size < 4) {
                return@withContext ImportResult.Error("最低4件の単語が必要です")
            }
            if (rows.size > 9999) {
                return@withContext ImportResult.Error("最大9,999件まで登録可能です")
            }

            // 単語の重複チェック
            val normalizedWords = rows.map { it.word.trim().lowercase() }
            if (normalizedWords.distinct().size < rows.size) {
                return@withContext ImportResult.Error("英単語に重複があります")
            }

            // 選択肢の自動生成が必要な場合、最低5種類のユニークな英単語/意味が必要
            val needsAutoChoices = rows.any { it.choicesEnJa.isEmpty() || it.choicesJaEn.isEmpty() || it.choicesListening.isEmpty() }
            if (needsAutoChoices) {
                if (normalizedWords.distinct().size < 5) {
                    return@withContext ImportResult.Error("選択肢を自動生成するには、異なる英単語が最低5件必要です")
                }
                val uniqueMeanings = rows.map { it.meaning.trim().lowercase() }.distinct()
                if (uniqueMeanings.size < 5) {
                    return@withContext ImportResult.Error("選択肢を自動生成するには、異なる意味が最低5件必要です")
                }
            }

            val entities = convertToEntities(rows, grade)

            // トランザクションで入れ替え実行
            db.withTransaction {
                performDelete(grade)
                // 2. 新規データの挿入
                wordDao.upsertAll(entities)
            }

            ImportResult.Success(entities.size)
        } catch (e: Exception) {
            ImportResult.Error("インポートに失敗しました: ${e.message}")
        }
    }

    /**
     * 指定されたGradeのマイ単語帳を削除する。
     */
    suspend fun deleteMyWordBook(grade: Int) = withContext(Dispatchers.IO) {
        if (grade !in 90..99) return@withContext
        db.withTransaction {
            performDelete(grade)
        }
    }

    private suspend fun performDelete(grade: Int) {
        val startId = grade * 10000 + 1
        val endId = grade * 10000 + 9999

        // 削除順序: 依存データを先に削除
        favoriteDao.deleteByWordIdRange(startId, endId)
        studyLogDao.deleteByWordIdRange(startId, endId)
        voiceCheckDao.deleteByWordIdRange(startId.toLong(), endId.toLong())
        
        // word_mastery は CASCADE により削除される
        wordDao.deleteWordsByGrade(grade)
    }

    internal data class ParseResult(val rows: List<ParsedRow>, val errors: List<String>)

    companion object {
        private const val FULL_COLUMNS_COUNT = 15
        private const val SIMPLE_COLUMNS_COUNT = 5

        internal fun parseAndValidateTsv(inputStream: InputStream): ParseResult {
            val rows = mutableListOf<ParsedRow>()
            val errors = mutableListOf<String>()

            BufferedReader(InputStreamReader(inputStream, "UTF-8")).use { reader ->
                // BOM除去とヘッダー取得
                val firstLine = reader.readLine()?.trim('\uFEFF') ?: return ParseResult(emptyList(), listOf("ファイルが空です"))
                val headerCols = TsvParser.parseLine(firstLine)
                
                val isFullFormat = when (headerCols.size) {
                    FULL_COLUMNS_COUNT -> true
                    SIMPLE_COLUMNS_COUNT -> false
                    else -> return ParseResult(emptyList(), listOf("ヘッダーの列数が不正です (5列または15列必要ですが${headerCols.size}列あります)"))
                }
                
                if (isFullFormat) {
                    val expected = listOf("no", "grade", "word", "meaning", "definition", "example", "exampleMeaning", "partOfSpeech", "difficulty", "frequency", "choicesEnJa", "choicesJaEn", "choicesListening", "synonyms", "antonyms")
                    headerCols.forEachIndexed { index, s ->
                        if (s.lowercase() != expected[index].lowercase()) {
                            errors.add("不正なヘッダー名です: '${s}' (期待値: '${expected[index]}')")
                        }
                    }
                } else {
                    val expected = listOf("word", "meaning", "partOfSpeech", "example", "exampleMeaning")
                    headerCols.forEachIndexed { index, s ->
                        if (s.lowercase() != expected[index].lowercase()) {
                            errors.add("不正なヘッダー名です: '${s}' (期待値: '${expected[index]}')")
                        }
                    }
                }
                if (errors.isNotEmpty()) return ParseResult(emptyList(), errors)

                var line: String?
                var lineNum = 1
                while (reader.readLine().also { line = it } != null) {
                    lineNum++
                    if (line.isNullOrBlank()) continue
                    
                    val cols = TsvParser.parseLine(line!!)
                    if (cols.size != headerCols.size) {
                        errors.add("${lineNum}行目: 列数が不正です (${headerCols.size}列必要ですが${cols.size}列あります)")
                        continue
                    }

                    val row = if (isFullFormat) {
                        mapFullRow(cols, lineNum, errors)
                    } else {
                        mapSimpleRow(cols, lineNum, errors)
                    }

                    if (row != null && errors.size < 100) {
                        rows.add(row)
                    }
                }
            }
            return ParseResult(rows, errors)
        }

        private fun mapFullRow(cols: List<String>, lineNum: Int, errors: MutableList<String>): ParsedRow? {
            val word = cols[2].trim()
            val meaning = cols[3].trim()
            if (word.isEmpty()) errors.add("${lineNum}行目: 英単語が空です")
            if (meaning.isEmpty()) errors.add("${lineNum}行目: 意味が空です")
            if (word.isEmpty() || meaning.isEmpty()) return null

            val choicesEnJa = parseJsonList(cols[10], "choicesEnJa", lineNum, errors)
            val choicesJaEn = parseJsonList(cols[11], "choicesJaEn", lineNum, errors)
            val choicesListening = parseJsonList(cols[12], "choicesListening", lineNum, errors)

            // バリデーション: 入力されている場合は正確に5件、かつ正解を含むこと
            validateChoices(choicesEnJa, meaning, "choicesEnJa", lineNum, errors)
            validateChoices(choicesJaEn, word, "choicesJaEn", lineNum, errors)
            validateChoices(choicesListening, word, "choicesListening", lineNum, errors)

            return ParsedRow(
                word = word,
                meaning = meaning,
                description = cols[4].trim(),
                sentence = cols[5].trim(),
                japaneseSentence = cols[6].trim(),
                pos = cols[7].trim(),
                difficulty = cols[8].toIntOrNull() ?: 3,
                frequency = cols[9].toIntOrNull() ?: 3,
                choicesEnJa = choicesEnJa,
                choicesJaEn = choicesJaEn,
                choicesListening = choicesListening,
                synonyms = parseRelatedWords(cols[13], "synonyms", lineNum, errors),
                antonyms = parseRelatedWords(cols[14], "antonyms", lineNum, errors)
            )
        }

        private fun mapSimpleRow(cols: List<String>, lineNum: Int, errors: MutableList<String>): ParsedRow? {
            val word = cols[0].trim()
            val meaning = cols[1].trim()
            if (word.isEmpty()) errors.add("${lineNum}行目: 英単語が空です")
            if (meaning.isEmpty()) errors.add("${lineNum}行目: 意味が空です")
            if (word.isEmpty() || meaning.isEmpty()) return null

            return ParsedRow(
                word = word,
                meaning = meaning,
                description = "",
                sentence = cols[3].trim(),
                japaneseSentence = cols[4].trim(),
                pos = cols[2].trim(),
                difficulty = 3,
                frequency = 3,
                choicesEnJa = emptyList(),
                choicesJaEn = emptyList(),
                choicesListening = emptyList(),
                synonyms = emptyList(),
                antonyms = emptyList()
            )
        }

        private fun validateChoices(list: List<String>, correct: String, fieldName: String, lineNum: Int, errors: MutableList<String>) {
            if (list.isEmpty()) return
            
            // 1. 件数
            if (list.size != 5) {
                errors.add("${lineNum}行目: ${fieldName}の要素数が5件ではありません (現在${list.size}件)")
            }
            
            // 2. 空白
            if (list.any { it.trim().isEmpty() }) {
                errors.add("${lineNum}行目: ${fieldName}に空の選択肢があります")
            }
            
            // 3. 重複
            if (list.distinct().size != list.size) {
                errors.add("${lineNum}行目: ${fieldName}が重複しています")
            }
            
            // 4. 正解の有無
            if (!list.contains(correct)) {
                errors.add("${lineNum}行目: ${fieldName}に正解が含まれていません (期待値: $correct)")
            }
            
            // 5. タブや改行
            if (list.any { it.contains("\t") || it.contains("\n") }) {
                errors.add("${lineNum}行目: ${fieldName}にタブや改行を含めることはできません")
            }
        }

        private fun parseJsonList(json: String, fieldName: String, lineNum: Int, errors: MutableList<String>): List<String> {
            if (json.isBlank() || json == "[]") return emptyList()
            
            val arr = try {
                JSONArray(json)
            } catch (e: Exception) {
                // 実際のパースエラーを詳細に特定
                errors.add("${lineNum}行目: ${fieldName}をJSONとして解析できません (値: $json)")
                return emptyList()
            }
            
            val list = mutableListOf<String>()
            for (i in 0 until arr.length()) {
                val item = arr.opt(i)
                if (item == null) {
                    errors.add("${lineNum}行目: ${fieldName}に空の要素があります")
                    return emptyList()
                }
                if (item !is String) {
                    errors.add("${lineNum}行目: ${fieldName}に文字列以外の要素があります (型: ${item.javaClass.simpleName})")
                    return emptyList()
                }
                list.add(item)
            }
            return list
        }

        private fun parseRelatedWords(json: String, fieldName: String, lineNum: Int, errors: MutableList<String>): List<RelatedWord> {
            if (json.isBlank() || json == "[]") return emptyList()
            
            val arr = try {
                JSONArray(json)
            } catch (e: Exception) {
                errors.add("${lineNum}行目: ${fieldName}をJSONとして解析できません (値: $json)")
                return emptyList()
            }
            
            val list = mutableListOf<RelatedWord>()
            for (i in 0 until arr.length()) {
                val obj = try {
                    arr.getJSONObject(i)
                } catch (e: Exception) {
                    errors.add("${lineNum}行目: ${fieldName}に有効なオブジェクトではない要素があります")
                    continue
                }
                list.add(
                    RelatedWord(
                        word = obj.optString("word", "").trim(),
                        note = obj.optString("note", "").trim()
                    )
                )
            }
            return list
        }

        internal fun convertToEntities(rows: List<ParsedRow>, grade: Int): List<WordEntity> {
            val startIdBase = grade * 10000
            
            // 決定的な誤答生成のため、全単語と意味をソート済みのリストとして保持
            val allSortedRows = rows.sortedBy { it.word }

            return rows.mapIndexed { index, row ->
                val wordId = startIdBase + (index + 1)
                
                // 選択肢が空の場合のみ自動生成 (正解1件 + 誤答4件 = 合計5件)
                val choicesJaEn = if (row.choicesJaEn.isEmpty()) {
                    val decoys = selectDeterministicDecoys(row.word, row.pos, allSortedRows.map { it.word to it.pos })
                    (decoys + row.word)
                } else row.choicesJaEn

                val choicesEnJa = if (row.choicesEnJa.isEmpty()) {
                    val decoys = selectDeterministicDecoys(row.meaning, row.pos, allSortedRows.map { it.meaning to it.pos })
                    (decoys + row.meaning)
                } else row.choicesEnJa

                val choicesListening = if (row.choicesListening.isEmpty()) {
                    val decoys = selectDeterministicDecoys(row.word, row.pos, allSortedRows.map { it.word to it.pos })
                    (decoys + row.word)
                } else row.choicesListening

                WordEntity(
                    no = wordId,
                    grade = grade,
                    word = row.word,
                    japanese = row.meaning,
                    description = row.description,
                    sentence = row.sentence,
                    japaneseSentence = row.japaneseSentence,
                    pos = row.pos,
                    difficulty = row.difficulty,
                    frequency = row.frequency,
                    // 保存時は5件（正解＋誤答4）。画面表示時に ChoiceGenerator が 1+3 抽出。
                    choicesEnJa = choicesEnJa,
                    choicesJaEn = choicesJaEn,
                    choicesListening = choicesListening,
                    synonyms = row.synonyms,
                    antonyms = row.antonyms
                )
            }
        }

        /**
         * ソート済みの候補リストから、正解を除いた決定的な4件の誤答を選択する。
         * 同じ品詞を優先する。
         */
        private fun selectDeterministicDecoys(correctValue: String, pos: String, sortedCandidates: List<Pair<String, String>>): List<String> {
            val others = sortedCandidates.filter { it.first != correctValue }
            
            // 1. 同じ品詞の候補を抽出
            val samePosOthers = others.filter { it.second.isNotEmpty() && it.second == pos }.map { it.first }
            // 2. それ以外の候補を抽出
            val differentPosOthers = others.filter { it.second != pos }.map { it.first }
            
            val combined = (samePosOthers + differentPosOthers).distinct()
            if (combined.size < 4) return combined
            
            val seedIndex = Math.floorMod(correctValue.hashCode().toLong(), combined.size.toLong()).toInt()
            
            return listOf(
                combined[seedIndex % combined.size],
                combined[(seedIndex + 1) % combined.size],
                combined[(seedIndex + 2) % combined.size],
                combined[(seedIndex + 3) % combined.size]
            )
        }
    }

    internal data class ParsedRow(
        val word: String,
        val meaning: String,
        val description: String,
        val sentence: String,
        val japaneseSentence: String,
        val pos: String,
        val difficulty: Int,
        val frequency: Int,
        val choicesEnJa: List<String>,
        val choicesJaEn: List<String>,
        val choicesListening: List<String>,
        val synonyms: List<RelatedWord>,
        val antonyms: List<RelatedWord>
    )
}
