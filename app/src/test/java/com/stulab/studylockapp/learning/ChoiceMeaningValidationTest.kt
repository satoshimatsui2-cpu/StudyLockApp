package com.stulab.studylockapp.learning

import com.stulab.studylockapp.data.TsvParser
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.*

class ChoiceMeaningValidationTest {

    private fun getAssetsFile(path: String): File {
        val root = File(path)
        if (root.exists()) return root
        return File("app", path)
    }

    @Test
    fun validateChoiceMeaningsTsv() {
        val file = getAssetsFile("src/main/assets/words/choice_meanings.tsv")
        assertTrue("choice_meanings.tsv exists at ${file.absolutePath}", file.exists())

        val lookupKeys = mutableSetOf<String>()
        file.bufferedReader().use { reader ->
            val header = reader.readLine()
            assertNotNull("TSV header exists", header)
            
            var line: String?
            var index = 0
            while (reader.readLine().also { line = it } != null) {
                val currentLine = line ?: break
                if (currentLine.isBlank()) continue
                val cols = TsvParser.parseLine(currentLine)
                assertTrue("Line ${index + 2} has enough columns", cols.size >= 4)
                
                val key = cols[0]
                val eng = cols[1]
                val norm = cols[2]
                val ja = cols[3]
                
                assertFalse("Duplicate lookupKey: $key", lookupKeys.contains(key))
                lookupKeys.add(key)
                
                assertEquals("Normalized text matches for: $eng", ReviewMeaningResolver.normalizeChoiceText(eng), norm)
                assertFalse("Japanese meaning is not empty for: $eng", ja.isBlank())
                index++
            }
        }
        println("Validated ${lookupKeys.size} entries in choice_meanings.tsv")
    }

    @Test
    fun verifyMeaningResolutionCoverage() {
        val wordsDir = getAssetsFile("src/main/assets/words")
        val allWords = mutableMapOf<String, String>() // lower_en -> ja

        // 1. Load vocabulary
        for (i in 1..7) {
            val file = File(wordsDir, "grade$i.tsv")
            if (!file.exists()) continue
            file.bufferedReader().useLines { lines ->
                lines.drop(1).forEach { line ->
                    val cols = TsvParser.parseLine(line)
                    if (cols.size > 3) {
                        allWords[cols[2].trim().lowercase()] = cols[3].trim()
                    }
                }
            }
        }

        // 2. Load supplemental meanings
        val supplementary = mutableMapOf<String, String>()
        val choiceMeaningsFile = File(wordsDir, "choice_meanings.tsv")
        if (choiceMeaningsFile.exists()) {
            choiceMeaningsFile.bufferedReader().useLines { lines ->
                lines.drop(1).forEach { line ->
                    val cols = TsvParser.parseLine(line)
                    if (cols.size > 3) {
                        // Simple logic: key is normalized_english
                        supplementary[cols[2]] = cols[3]
                    }
                }
            }
        }

        // 3. Extract all unique choices from all grades
        val uniqueChoices = mutableSetOf<String>()
        for (i in 1..7) {
            val file = File(wordsDir, "grade$i.tsv")
            if (!file.exists()) continue
            file.bufferedReader().useLines { lines ->
                lines.drop(1).forEach { line ->
                    val cols = TsvParser.parseLine(line)
                    if (cols.size < 15) return@forEach
                    
                    try {
                        JSONArray(cols[11]).let { jaEn -> 
                            for (j in 0 until jaEn.length()) uniqueChoices.add(jaEn.getString(j).trim())
                        }
                        JSONArray(cols[12]).let { list -> 
                            for (j in 0 until list.length()) uniqueChoices.add(list.getString(j).trim())
                        }
                        JSONArray(cols[13]).let { syns -> 
                            for (j in 0 until syns.length()) uniqueChoices.add(syns.getJSONObject(j).getString("word").trim())
                        }
                        JSONArray(cols[14]).let { ants -> 
                            for (j in 0 until ants.length()) uniqueChoices.add(ants.getJSONObject(j).getString("word").trim())
                        }
                    } catch (e: Exception) {}
                }
            }
        }

        // 4. Test resolution
        val unsolved = mutableListOf<String>()
        uniqueChoices.filter { it.isNotEmpty() }.forEach { choice ->
            val low = choice.lowercase()
            val norm = ReviewMeaningResolver.normalizeChoiceText(choice)
            
            val found = allWords.containsKey(low) || supplementary.containsKey(norm)
            if (!found) {
                unsolved.add(choice)
            }
        }

        println("Total unique choices tested: ${uniqueChoices.size}")
        println("Resolved: ${uniqueChoices.size - unsolved.size}")
        println("Unsolved: ${unsolved.size}")
        
        if (unsolved.isNotEmpty()) {
            println("Top 20 Unsolved:")
            unsolved.take(20).forEach { println(" - $it") }
        }
    }
}
