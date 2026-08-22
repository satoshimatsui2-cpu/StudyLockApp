package com.stulab.studylockapp.ui

import java.util.*

object PronunciationUtils {

    fun normalizeNumberAndTime(text: String): String {
        var s = text.lowercase()

        val numWords = mapOf(
            "0" to "zero", "1" to "one", "2" to "two", "3" to "three", "4" to "four",
            "5" to "five", "6" to "six", "7" to "seven", "8" to "eight", "9" to "nine",
            "10" to "ten", "11" to "eleven", "12" to "twelve", "13" to "thirteen",
            "14" to "fourteen", "15" to "fifteen", "16" to "sixteen", "17" to "seventeen",
            "18" to "eighteen", "19" to "nineteen", "20" to "twenty",
            "30" to "thirty", "40" to "forty", "50" to "fifty", "60" to "sixty",
            "70" to "seventy", "80" to "eighty", "90" to "ninety",
            "100" to "one hundred", "200" to "two hundred", "300" to "three hundred",
            "400" to "four hundred", "500" to "five hundred", "600" to "six hundred",
            "700" to "seven hundred", "800" to "eight hundred", "900" to "nine hundred",
            "1000" to "one thousand"
        )

        val ordinalWords = mapOf(
            "1st" to "first", "2nd" to "second", "3rd" to "third", "4th" to "fourth",
            "5th" to "fifth", "6th" to "sixth", "7th" to "seventh", "8th" to "eighth",
            "9th" to "ninth", "10th" to "tenth", "11th" to "eleven", "12th" to "twelfth",
            "13th" to "thirteenth", "14th" to "fourteenth", "15th" to "fifteenth",
            "16th" to "sixteenth", "17th" to "seventeenth", "18th" to "eighteenth",
            "19th" to "nineteenth", "20th" to "twentieth"
        )

        ordinalWords.forEach { (key, value) ->
            s = s.replace(Regex("\\b$key\\b"), value)
        }

        s = s.replace(Regex("\\b(2[0-3]|[01]?[0-9])[:.](00)\\b")) { match ->
            numWords[match.groupValues[1]] ?: match.value
        }

        s = s.replace(Regex("\\b(2[0-3]|[01]?[0-9])[:.](30)\\b")) { match ->
            val hour = numWords[match.groupValues[1]] ?: match.groupValues[1]
            "$hour thirty"
        }

        s = s.replace(Regex("\\b(2[0-3]|[01]?[0-9])\\s*o'?clock\\b")) { match ->
            numWords[match.groupValues[1]] ?: match.value
        }

        s = s.replace(Regex("\\b(zero|one|two|three|four|five|six|seven|eight|nine|ten|eleven|twelve|thirteen|fourteen|fifteen|sixteen|seventeen|eighteen|nineteen|twenty)\\s*o'?clock\\b"), "$1")

        val digitRegex = Regex("\\b(\\d+)\\b")
        s = s.replace(digitRegex) { match ->
            numWords[match.value] ?: match.value
        }

        return s
    }

    fun normalizeForComparison(s: String): String {
        var normalized = s.lowercase()
        normalized = normalizeNumberAndTime(normalized)
        normalized = normalized.replace(Regex("[^a-z\\s]"), " ")
        normalized = normalized.replace(Regex("\\s+"), " ").trim()
        return normalized
    }

    fun normalizeMinimal(s: String): String {
        val withNumbers = normalizeNumberAndTime(s.lowercase())
        return withNumbers.replace(Regex("[^a-z]"), "").trim()
    }

    fun checkWordPronunciation(input: String, target: String): Boolean {
        val normInput = normalizeMinimal(input)
        val normTarget = normalizeMinimal(target)
        return normInput == normTarget || normInput == "${normTarget}s" || normInput == "${normTarget}es"
    }

    fun checkSentencePronunciation(recognized: String, targetSentence: String, targetWord: String): Boolean {
        val normRecognized = normalizeForComparison(recognized)
        val normTarget = normalizeForComparison(targetSentence)

        if (normRecognized == normTarget) return true

        val recognizedWords = normRecognized.split(" ").filter { it.isNotBlank() }.toSet()
        val allTargetWords = normTarget.split(" ").filter { it.isNotBlank() }
        if (allTargetWords.isEmpty()) return false

        val functionalWords = setOf("a", "an", "the", "is", "am", "are", "to", "of", "in", "on", "at")
        val filteredTargetWords = allTargetWords.filter { it !in functionalWords }
        val wordsToMatch = if (filteredTargetWords.size < 2) allTargetWords else filteredTargetWords
        
        val allowedMissing = when {
            wordsToMatch.size <= 5 -> 0
            wordsToMatch.size <= 8 -> 1
            else -> 2
        }

        val missingWords = wordsToMatch.filter { it !in recognizedWords }
        
        val normTargetWord = normalizeForComparison(targetWord)
        val normTargetWordList = normTargetWord.split(" ").filter { it.isNotBlank() }
        val containsTargetWord = normTargetWordList.all { tw ->
            recognizedWords.any { rw ->
                rw == tw || rw == "${tw}s" || rw == "${tw}es"
            }
        }

        val lastImportantWord = wordsToMatch.lastOrNull()
        val containsLastImportantWord = lastImportantWord == null || lastImportantWord in recognizedWords

        return missingWords.size <= allowedMissing && containsTargetWord && containsLastImportantWord
    }
}
