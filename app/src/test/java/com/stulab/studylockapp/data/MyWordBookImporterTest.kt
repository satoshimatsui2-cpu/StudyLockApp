package com.stulab.studylockapp.data

import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream

class MyWordBookImporterTest {

    @Test
    fun testParseAndValidate_Full15Columns_WithSymbolsAndMultiWords() {
        val header = "no\tgrade\tword\tmeaning\tdefinition\texample\texampleMeaning\tpartOfSpeech\tdifficulty\tfrequency\tchoicesEnJa\tchoicesJaEn\tchoicesListening\tsynonyms\tantonyms"
        
        // 2nd row: word must match the correct choice in JSON exactly (including symbols like ~)
        val row2 = "1\t11\tdecide on ~\t〜に決める\tdef1\tex1\tm1\tphrase\t3\t3\t[\"〜に決める\",\"に反対する\",\"を無視する\",\"を疑う\",\"を批判する\"]\t[\"decide on ~\",\"put off ~\",\"rely on ~\",\"look for ~\",\"refuse ~\"]\t[\"decide on ~\",\"depend on ~\",\"decide against ~\",\"rely on ~\",\"agree on ~\"]\t[]\t[]"
        
        // 3rd row
        val row3 = "2\t11\tmake a decision\t決断する\tdef2\tex2\tm2\tphrase\t3\t3\t[\"決断する\",\"a\",\"b\",\"c\",\"d\"]\t[\"make a decision\",\"make a promise\",\"make a mistake\",\"make an effort\",\"take a break\"]\t[\"make a decision\",\"make a suggestion\",\"make a difference\",\"make a promise\",\"make an appointment\"]\t[]\t[]"
        
        val row4 = "3\t11\tw3\tm3\td3\te3\tm3\tn\t3\t3\t[\"m3\",\"a\",\"b\",\"c\",\"d\"]\t[\"w3\",\"a\",\"b\",\"c\",\"d\"]\t[\"w3\",\"a\",\"b\",\"c\",\"d\"]\t[]\t[]"
        val row5 = "4\t11\tw4\tm4\td4\te4\tm4\tn\t3\t3\t[\"m4\",\"a\",\"b\",\"c\",\"d\"]\t[\"w4\",\"a\",\"b\",\"c\",\"d\"]\t[\"w4\",\"a\",\"b\",\"c\",\"d\"]\t[]\t[]"
        
        val tsv = "$header\n$row2\n$row3\n$row4\n$row5"
        val inputStream = ByteArrayInputStream(tsv.toByteArray(Charsets.UTF_8))
        
        // Debug: Print raw fields for user report
        val lines = tsv.lines()
        if (lines.size > 1) {
            val fields = TsvParser.parseLine(lines[1])
            println("Row 2, Column 11 (choicesJaEn) raw: [${fields[11]}]")
            println("Row 2, Column 12 (choicesListening) raw: [${fields[12]}]")
        }

        val result = MyWordBookImporter.parseAndValidateTsv(inputStream)
        
        assertTrue(result.errors.toString(), result.errors.isEmpty())
        assertEquals(4, result.rows.size)
        
        // row2 check
        assertEquals("decide on ~", result.rows[0].word)
        assertTrue(result.rows[0].choicesJaEn.contains("decide on ~"))
        
        // row3 check
        assertEquals("make a decision", result.rows[1].word)
        assertTrue(result.rows[1].choicesJaEn.contains("make a decision"))
    }

    @Test
    fun testParseAndValidate_ExcelQuotedJson() {
        val header = "no\tgrade\tword\tmeaning\tdefinition\texample\texampleMeaning\tpartOfSpeech\tdifficulty\tfrequency\tchoicesEnJa\tchoicesJaEn\tchoicesListening\tsynonyms\tantonyms"
        // Excel style: Quoted field with double double-quotes
        val row = "1\t11\tw\tm\td\te\tm\tp\t3\t3\t\"[\"\"m\"\",\"\"a\"\",\"\"b\"\",\"\"c\"\",\"\"d\"\"]\"\t\"[\"\"w\"\",\"\"a\"\",\"\"b\"\",\"\"c\"\",\"\"d\"\"]\"\t\"[\"\"w\"\",\"\"a\"\",\"\"b\"\",\"\"c\"\",\"\"d\"\"]\"\t[]\t[]"
        val tsv = "$header\n$row\n$row\n$row\n$row"
        val inputStream = ByteArrayInputStream(tsv.toByteArray(Charsets.UTF_8))
        val result = MyWordBookImporter.parseAndValidateTsv(inputStream)
        
        assertTrue(result.errors.toString(), result.errors.isEmpty())
        assertEquals("m", result.rows[0].choicesEnJa[0])
    }

    @Test
    fun testParseAndValidate_Simple5Columns() {
        val header = "word\tmeaning\tpartOfSpeech\texample\texampleMeaning"
        val row1 = "apple\tりんご\tnoun\tI eat an apple.\t私はりんごを食べる。"
        val row2 = "banana\tバナナ\tnoun\tex\tm"
        val row3 = "cherry\tさくらんぼ\tnoun\tex\tm"
        val row4 = "date\tデーツ\tnoun\tex\tm"
        val tsv = "$header\n$row1\n$row2\n$row3\n$row4"
        val inputStream = ByteArrayInputStream(tsv.toByteArray(Charsets.UTF_8))
        val result = MyWordBookImporter.parseAndValidateTsv(inputStream)
        
        assertTrue(result.errors.isEmpty())
        assertEquals(4, result.rows.size)
    }

    @Test
    fun testParseAndValidate_InvalidColumnsCount() {
        val tsv = "word\tmeaning\tpartOfSpeech\na\tb\tc"
        val inputStream = ByteArrayInputStream(tsv.toByteArray(Charsets.UTF_8))
        val result = MyWordBookImporter.parseAndValidateTsv(inputStream)
        assertFalse(result.errors.isEmpty())
        assertTrue(result.errors[0].contains("ヘッダーの列数が不正です"))
    }

    @Test
    fun testParseAndValidate_InvalidChoicesCount() {
        val header = "no\tgrade\tword\tmeaning\tdefinition\texample\texampleMeaning\tpartOfSpeech\tdifficulty\tfrequency\tchoicesEnJa\tchoicesJaEn\tchoicesListening\tsynonyms\tantonyms"
        val row1 = "1\t90\tword1\t意味1\tdef\tex\tm\tpos\t3\t3\t\"[\"\"1\"\",\"\"2\"\"]\"\t\"[]\"\t\"[]\"\t\"[]\"\t\"[]\""
        val tsv = "$header\n$row1"
        val inputStream = ByteArrayInputStream(tsv.toByteArray(Charsets.UTF_8))
        val result = MyWordBookImporter.parseAndValidateTsv(inputStream)
        assertFalse(result.errors.isEmpty())
        assertTrue(result.errors.toString(), result.errors.any { it.contains("要素数が5件ではありません") })
    }

    @Test
    fun testConvertToEntities_AutoGeneration_Deterministic() {
        val rows = listOf(
            createParsedRow("apple", "りんご"),
            createParsedRow("banana", "バナナ"),
            createParsedRow("cherry", "さくらんぼ"),
            createParsedRow("date", "デーツ"),
            createParsedRow("elderberry", "エルダーベリー")
        )
        val entities1 = MyWordBookImporter.convertToEntities(rows, 90)
        val entities2 = MyWordBookImporter.convertToEntities(rows, 90)

        assertEquals(900001, entities1[0].no)
        assertEquals(5, entities1[0].choicesEnJa.size)
        assertEquals(entities1[0].choicesEnJa.sorted(), entities2[0].choicesEnJa.sorted())
    }

    private fun createParsedRow(word: String, meaning: String): MyWordBookImporter.ParsedRow {
        return MyWordBookImporter.ParsedRow(
            word = word,
            meaning = meaning,
            description = "",
            sentence = "",
            japaneseSentence = "",
            pos = "noun",
            difficulty = 3,
            frequency = 3,
            choicesEnJa = emptyList(),
            choicesJaEn = emptyList(),
            choicesListening = emptyList(),
            synonyms = emptyList(),
            antonyms = emptyList()
        )
    }

    @Test
    fun testParseAndValidate_BOMRemoval() {
        val bom = "\uFEFF"
        val header = "word\tmeaning\tpartOfSpeech\texample\texampleMeaning"
        val row1 = "apple\tりんご\tn\tex\tm"
        val row2 = "b\tb\tn\tex\tm"
        val row3 = "c\tc\tn\tex\tm"
        val row4 = "d\td\tn\tex\tm"
        val tsv = "${bom}$header\n$row1\n$row2\n$row3\n$row4"
        val inputStream = ByteArrayInputStream(tsv.toByteArray(Charsets.UTF_8))
        val result = MyWordBookImporter.parseAndValidateTsv(inputStream)
        assertTrue(result.errors.isEmpty())
        assertEquals(4, result.rows.size)
        assertEquals("apple", result.rows[0].word)
    }
}
