package com.stulab.studylockapp.data

import android.util.Log
import org.json.JSONArray
import org.json.JSONObject

/**
 * TsvWordRow DTOからWordEntityへの変換を担うマッパー
 */
object TsvWordMapper {
    private const val TAG = "TsvWordMapper"

    fun mapToEntity(row: TsvWordRow, lineNumber: Int): WordEntity? {
        return try {
            WordEntity(
                no = row.no.toInt(),
                grade = row.grade.toInt(),
                word = row.word,
                japanese = row.japanese,
                description = row.description,
                sentence = row.sentence,
                japaneseSentence = row.japaneseSentence,
                pos = row.pos,
                difficulty = row.difficulty.toIntOrNull() ?: 1,
                frequency = row.frequency.toIntOrNull() ?: 1,
                choicesEnJa = parseJsonList(row.choicesEnJa, "choicesEnJa", lineNumber),
                choicesJaEn = parseJsonList(row.choicesJaEn, "choicesJaEn", lineNumber),
                choicesListening = parseJsonList(row.choicesListening, "choicesListening", lineNumber),
                synonyms = parseRelatedWords(row.synonyms, "synonyms", lineNumber),
                antonyms = parseRelatedWords(row.antonyms, "antonyms", lineNumber)
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error at line $lineNumber: Failed to map basic fields. ${e.message}")
            null
        }
    }

    private fun parseJsonList(json: String, fieldName: String, lineNumber: Int): List<String> {
        if (json.isBlank() || json == "[]") return emptyList()
        return try {
            val arr = JSONArray(json)
            List(arr.length()) { arr.getString(it) }
        } catch (e: Exception) {
            Log.e(TAG, "Error at line $lineNumber, field $fieldName: Invalid JSON array format. content=[$json]")
            emptyList()
        }
    }

    private fun parseRelatedWords(json: String, fieldName: String, lineNumber: Int): List<RelatedWord> {
        if (json.isBlank() || json == "[]") return emptyList()
        return try {
            val arr = JSONArray(json)
            List(arr.length()) {
                val obj = arr.getJSONObject(it)
                RelatedWord(
                    word = obj.optString("word", ""),
                    note = obj.optString("note", "")
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error at line $lineNumber, field $fieldName: Invalid JSON object array format. content=[$json]")
            emptyList()
        }
    }
}
