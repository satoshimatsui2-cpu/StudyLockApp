package com.example.studylockapp.data.practical

import android.content.Context
import android.util.Log
import com.example.studylockapp.data.db.PracticalHistoryDao
import com.example.studylockapp.data.db.PracticalHistoryEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.io.InputStreamReader

/**
 * 実践テストのデータを管理するリポジトリ。
 * TSVからの読み込みと、解答履歴の保存を担当します。
 */
class PracticalTestRepository(
    private val context: Context,
    private val historyDao: PracticalHistoryDao
) {
    companion object {
        private const val TAG = "PracticalTestRepo"
    }

    /**
     * 指定されたグレードとタイプに一致する問題があるか確認します。
     */
    suspend fun hasQuestions(type: PracticalQuizMode, grade: Int): Boolean {
        return getQuestions(type, grade).isNotEmpty()
    }

    /**
     * 除外すべき問題No（正解済み or 直近の不正解）を取得します。
     */
    suspend fun getExcludedQuestionNos(type: PracticalQuizMode, grade: Int, recentlySince: Long): List<String> {
        return historyDao.getExcludedQuestionNos(grade, type.name, recentlySince)
    }

    /**
     * 指定されたグレードとタイプに一致する問題をTSVから取得します。
     * ファイル全文を読み込み、状態遷移型パーサで処理することで、セル内の改行やクォートを安全に扱います。
     */
    suspend fun getQuestions(type: PracticalQuizMode, grade: Int): List<PracticalQuestion> = withContext(Dispatchers.IO) {
        val fileName = when (type) {
            PracticalQuizMode.FILL_BLANK -> "practical/practical_fill_blank_questions.tsv"
            PracticalQuizMode.REARRANGE -> "practical/practical_rearrange_questions.tsv"
            PracticalQuizMode.LISTENING -> "practical/practical_listening_questions.tsv"
        }

        val questions = mutableListOf<PracticalQuestion>()
        try {
            context.assets.open(fileName).use { inputStream ->
                val content = InputStreamReader(inputStream).readText()
                val rows = parseTsv(content)
                
                // ヘッダー(1行目)を除外
                if (rows.size <= 1) return@withContext emptyList()

                for (i in 1 until rows.size) {
                    val columns = rows[i]
                    
                    if (type == PracticalQuizMode.LISTENING) {
                        // リスニング形式: 11カラム（id, grade, part, tts_script, question_text, option_1..4, correct_option, explanation）
                        if (columns.size < 11) continue

                        val qGrade = columns[1].trim().toIntOrNull() ?: -1
                        val correctOption = columns[9].trim().toIntOrNull() ?: -1

                        if (qGrade == grade && correctOption in 1..4) {
                            questions.add(
                                PracticalQuestion(
                                    no = columns[0].trim(),
                                    grade = qGrade,
                                    unit = columns[2].trim(), // part
                                    question = formatText(columns[4]), // 互換性のために question_text を保持
                                    ttsScript = formatText(columns[3]),
                                    questionText = formatText(columns[4]),
                                    choices = listOf(columns[5], columns[6], columns[7], columns[8]).map { it.trim() },
                                    correctOptionIndex = correctOption,
                                    explanation = formatText(columns[10]),
                                    type = type
                                )
                            )
                        }
                    } else {
                        // 既存形式: 10カラム（no, grade, unit, question, choice1..4, correct_option, explanation）必須
                        if (columns.size < 10) continue

                        val qGrade = columns[1].trim().toIntOrNull() ?: -1
                        val correctOption = columns[8].trim().toIntOrNull() ?: -1

                        // グレード一致チェック ＆ 正解インデックス(1..4)の範囲チェック
                        if (qGrade == grade && correctOption in 1..4) {
                            questions.add(
                                PracticalQuestion(
                                    no = columns[0].trim(),
                                    grade = qGrade,
                                    unit = columns[2].trim(),
                                    question = formatText(columns[3]),
                                    choices = listOf(columns[4], columns[5], columns[6], columns[7]).map { it.trim() },
                                    correctOptionIndex = correctOption,
                                    explanation = formatText(columns[9]),
                                    type = type
                                )
                            )
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load practical questions from $fileName", e)
        }
        questions
    }

    /**
     * 状態遷移ベースのTSVパーサ。
     * ダブルクォート内のタブや改行をセルの内容として保持し、"" によるエスケープも処理します。
     */
    private fun parseTsv(content: String): List<List<String>> {
        val result = mutableListOf<List<String>>()
        var currentLine = mutableListOf<String>()
        var currentCell = StringBuilder()
        var inQuotes = false
        var i = 0
        
        while (i < content.length) {
            val c = content[i]
            val nextC = if (i + 1 < content.length) content[i + 1] else null

            when {
                // クォート内のエスケープ ("")
                c == '"' && inQuotes && nextC == '"' -> {
                    currentCell.append('"')
                    i++
                }
                // クォートの開始・終了
                c == '"' -> {
                    inQuotes = !inQuotes
                }
                // 区切り文字 (タブ)
                c == '\t' && !inQuotes -> {
                    currentLine.add(currentCell.toString())
                    currentCell = StringBuilder()
                }
                // 行末
                (c == '\n' || c == '\r') && !inQuotes -> {
                    if (c == '\r' && nextC == '\n') i++ // CRLF対応
                    currentLine.add(currentCell.toString())
                    result.add(currentLine.toList())
                    currentLine = mutableListOf()
                    currentCell = StringBuilder()
                }
                else -> {
                    currentCell.append(c)
                }
            }
            i++
        }
        
        // 最後の行の処理
        if (currentLine.isNotEmpty() || currentCell.isNotEmpty()) {
            currentLine.add(currentCell.toString())
            result.add(currentLine.toList())
        }
        
        return result
    }

    /**
     * セル内の "\n" 文字列を実改行に変換し、前後の不要なクォートを除去します。
     */
    private fun formatText(text: String): String {
        return text.replace("\\n", "\n").trim().removeSurrounding("\"")
    }

    /**
     * 解答履歴を保存します。
     */
    suspend fun saveHistory(
        question: PracticalQuestion,
        selectedAnswer: String,
        isCorrect: Boolean,
        points: Int,
        sessionId: String,
        isScored: Boolean = true,
        usedReplay: Boolean = false,
        resultStatus: String? = null
    ) = withContext(Dispatchers.IO) {
        try {
            val status = resultStatus ?: if (isCorrect) "CORRECT" else "WRONG"
            
            val history = PracticalHistoryEntity(
                questionNo = question.no,
                questionType = question.type.name,
                grade = question.grade,
                unit = question.unit,
                questionText = question.questionText ?: question.question,
                choicesJson = JSONArray(question.choices).toString(),
                correctAnswer = question.choices[question.correctOptionIndex - 1],
                selectedAnswer = selectedAnswer,
                isCorrect = isCorrect,
                points = points,
                explanation = question.explanation,
                answeredAt = System.currentTimeMillis(),
                sessionId = sessionId,
                isScored = isScored,
                usedReplay = usedReplay,
                resultStatus = status
            )
            historyDao.insert(history)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save practical history", e)
        }
    }
}
