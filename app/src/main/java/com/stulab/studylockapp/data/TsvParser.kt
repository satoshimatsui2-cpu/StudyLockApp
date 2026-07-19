package com.stulab.studylockapp.data

/**
 * TSV行をパースするためのクラス。
 * 引用符で囲まれたフィールドや、内部の二重引用符エスケープ ("") を処理します。
 */
object TsvParser {
    fun parseLine(line: String): List<String> {
        val result = mutableListOf<String>()
        var currentField = StringBuilder()
        var inQuotes = false
        var i = 0

        // 行全体が引用符で囲まれている場合（エクスポート形式による）への対策
        val trimmedLine = if (line.startsWith("\"") && line.endsWith("\"")) {
            line.substring(1, line.length - 1)
        } else {
            line
        }

        while (i < trimmedLine.length) {
            val c = trimmedLine[i]
            when {
                c == '"' -> {
                    if (inQuotes && i + 1 < trimmedLine.length && trimmedLine[i + 1] == '"') {
                        // 二重引用符のエスケープ
                        currentField.append('"')
                        i++
                    } else {
                        inQuotes = !inQuotes
                    }
                }
                c == '\t' && !inQuotes -> {
                    result.add(currentField.toString().trim())
                    currentField = StringBuilder()
                }
                else -> {
                    currentField.append(c)
                }
            }
            i++
        }
        result.add(currentField.toString().trim())
        return result
    }
}
