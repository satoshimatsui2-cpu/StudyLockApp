package com.stulab.studylockapp.learning

import com.stulab.studylockapp.data.TsvParser
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Test
import java.io.File

class ChoiceMeaningExtractionTest {
    private fun getAssetsFile(path: String): File {
        val root = File(path)
        if (root.exists()) return root
        return File("app", path)
    }

    @Test
    fun extractMissingMeanings() {
        // 現在の作業ディレクトリはプロジェクトルートと想定
        val wordsDir = getAssetsFile("src/main/assets/words")
        val allWords = mutableSetOf<String>()
        val allChoices = mutableListOf<ChoiceOccurrence>()

        // 1. 全グレードの単語を収集（綴り検索用）
        for (i in 1..7) {
            val file = File(wordsDir, "grade$i.tsv")
            if (!file.exists()) continue
            file.bufferedReader().useLines { lines ->
                lines.drop(1).forEach { line ->
                    val cols = TsvParser.parseLine(line)
                    if (cols.size > 2) {
                        allWords.add(cols[2].trim().lowercase())
                    }
                }
            }
        }

        // 2. 既存の補完DBにあるものも収集
        val choiceMeaningsFile = File(wordsDir, "choice_meanings.tsv")
        val existingMeanings = mutableSetOf<String>()
        if (choiceMeaningsFile.exists()) {
            choiceMeaningsFile.bufferedReader().useLines { lines ->
                lines.drop(1).forEach { line ->
                    val cols = TsvParser.parseLine(line)
                    if (cols.size > 1) {
                        existingMeanings.add(cols[1].trim().lowercase())
                    }
                }
            }
        }

        // 3. 全選択肢を収集
        for (i in 1..7) {
            val file = File(wordsDir, "grade$i.tsv")
            if (!file.exists()) continue
            file.bufferedReader().useLines { lines ->
                lines.drop(1).forEach { line ->
                    val cols = TsvParser.parseLine(line)
                    if (cols.size < 15) return@forEach
                    
                    val no = cols[0]
                    val wordStr = cols[2].trim()
                    
                    // choicesJaEn (index 11)
                    try {
                        val jaEn = JSONArray(cols[11])
                        for (j in 0 until jaEn.length()) {
                            val c = jaEn.getString(j).trim()
                            if (c.isNotEmpty()) allChoices.add(ChoiceOccurrence(c, no, wordStr, "ChoicesJaEn"))
                        }
                    } catch (e: Exception) {}

                    // choicesListening (index 12)
                    try {
                        val listening = JSONArray(cols[12])
                        for (j in 0 until listening.length()) {
                            val c = listening.getString(j).trim()
                            if (c.isNotEmpty()) allChoices.add(ChoiceOccurrence(c, no, wordStr, "Listening"))
                        }
                    } catch (e: Exception) {}

                    // synonyms (index 13)
                    try {
                        val syns = JSONArray(cols[13])
                        for (j in 0 until syns.length()) {
                            val s = syns.getJSONObject(j).getString("word").trim()
                            if (s.isNotEmpty()) allChoices.add(ChoiceOccurrence(s, no, wordStr, "Synonym"))
                        }
                    } catch (e: Exception) {}

                    // antonyms (index 14)
                    try {
                        val ants = JSONArray(cols[14])
                        for (j in 0 until ants.length()) {
                            val a = ants.getJSONObject(j).getString("word").trim()
                            if (a.isNotEmpty()) allChoices.add(ChoiceOccurrence(a, no, wordStr, "Antonym"))
                        }
                    } catch (e: Exception) {}
                }
            }
        }

        // 4. 未解決項目の抽出
        val missing = allChoices.filter { 
            val eng = it.english.lowercase()
            eng !in allWords && eng !in existingMeanings 
        }.groupBy { it.english }

        println("--- STATISTICS ---")
        println("Total Vocabulary Words: ${allWords.size}")
        println("Total Choice Occurrences: ${allChoices.size}")
        println("Unique English Choices: ${allChoices.map { it.english.lowercase() }.distinct().size}")
        println("Solved by Vocabulary: ${allChoices.count { it.english.lowercase() in allWords }}")
        println("Solved by ChoiceMeanings: ${allChoices.count { it.english.lowercase() in existingMeanings && it.english.lowercase() !in allWords }}")
        println("Unsolved Unique English: ${missing.size}")
        
        // モード別未解決件数
        val modeMissing = allChoices.filter { 
            val eng = it.english.lowercase()
            eng !in allWords && eng !in existingMeanings 
        }.groupBy { it.mode }.mapValues { it.value.size }
        println("Unsolved by Mode: $modeMissing")

        // 5. 結果を出力 (TSV形式で読み取れるように)
        val resultFile = File("reports/unsolved_choices_extracted.tsv")
        resultFile.parentFile.mkdirs()
        resultFile.printWriter().use { out ->
            out.println("english\tsource_no\tsource_word\tmode")
            missing.forEach { (english, occurrences) ->
                occurrences.forEach { occ ->
                    out.println("${occ.english}\t${occ.sourceNo}\t${occ.sourceWord}\t${occ.mode}")
                }
            }
        }
        println("Report written to: ${resultFile.absolutePath}")
    }

    data class ChoiceOccurrence(
        val english: String,
        val sourceNo: String,
        val sourceWord: String,
        val mode: String
    )
}
